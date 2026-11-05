package com.nuvio.app.core.licence

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.nuvio.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/**
 * Licences de Nuvio Pro. Les reglages viennent des ressources (res/values/licence.xml) :
 * la version gratuite garde licence_required = false, Nuvio Pro le met a true
 * (androidApp/src/pro/res/values/licence.xml).
 */
internal class LicenceConfig(
    val required: Boolean,
    val publicKeyBase64: String,
    val contact: String,
    val blocklistUrl: String,
) {
    companion object {
        const val TIME_URL = "https://www.google.com/generate_204"

        fun from(context: Context): LicenceConfig {
            val res = context.resources
            return LicenceConfig(
                required = res.getBoolean(R.bool.licence_required),
                publicKeyBase64 = res.getString(R.string.licence_public_key).trim(),
                contact = res.getString(R.string.licence_contact).trim(),
                blocklistUrl = res.getString(R.string.licence_blocklist_url).trim(),
            )
        }
    }
}

internal sealed interface LicenceStatus {
    data object Missing : LicenceStatus
    data class Valid(val expiryDay: Int, val daysLeft: Int) : LicenceStatus
    data class Expired(val expiryDay: Int) : LicenceStatus
    data object Invalid : LicenceStatus
    data object WrongDevice : LicenceStatus
    data object ClockChanged : LicenceStatus
    data object Blocked : LicenceStatus
    data object NotConfigured : LicenceStatus
}

/** Meme format que licence/generateur.html (bloc "licence-core"). */
internal object LicenceFormat {
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val VERSION = 1
    const val PAYLOAD_LEN = 8
    const val SIG_LEN = 64
    const val KEY_CHARS = 116
    const val DAY_MS = 86_400_000L

    fun normalize(text: String): String = buildString {
        for (c in text.uppercase()) {
            val mapped = when (c) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> c
            }
            if (ALPHABET.indexOf(mapped) >= 0) append(mapped)
        }
    }

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var bits = 0
        var value = 0
        for (b in bytes) {
            value = (value shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(value ushr (bits - 5)) and 31])
                bits -= 5
            }
            value = value and ((1 shl bits) - 1)
        }
        if (bits > 0) out.append(ALPHABET[(value shl (5 - bits)) and 31])
        return out.toString()
    }

    fun decode(normalized: String): ByteArray {
        val out = ByteArrayOutputStream()
        var bits = 0
        var value = 0
        for (c in normalized) {
            value = (value shl 5) or ALPHABET.indexOf(c)
            bits += 5
            if (bits >= 8) {
                out.write((value ushr (bits - 8)) and 0xFF)
                bits -= 8
                value = value and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }

    fun deviceCodeFor(stableId: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest("nuvio-licence-v1:$stableId".toByteArray(Charsets.UTF_8))
        val code = encode(hash.copyOfRange(0, 5))
        return code.substring(0, 4) + "-" + code.substring(4)
    }

    /** Le navigateur signe en r||s (64 octets), Java attend du DER. */
    fun rawSignatureToDer(raw: ByteArray): ByteArray {
        fun derInteger(bytes: ByteArray): ByteArray {
            var start = 0
            while (start < bytes.size - 1 && bytes[start] == 0.toByte()) start++
            var value = bytes.copyOfRange(start, bytes.size)
            if ((value[0].toInt() and 0x80) != 0) value = byteArrayOf(0) + value
            return byteArrayOf(0x02, value.size.toByte()) + value
        }
        val r = derInteger(raw.copyOfRange(0, 32))
        val s = derInteger(raw.copyOfRange(32, 64))
        return byteArrayOf(0x30, (r.size + s.size).toByte()) + r + s
    }

    fun parsePublicKey(base64: String): PublicKey? = runCatching {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(base64, Base64.DEFAULT)))
    }.getOrNull()

    fun verifySignature(publicKey: PublicKey, payload: ByteArray, signature: ByteArray): Boolean = runCatching {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(payload)
        verifier.verify(rawSignatureToDer(signature))
    }.getOrDefault(false)
}

internal class LicenceManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("nuvio_licence", Context.MODE_PRIVATE)
    val config = LicenceConfig.from(appContext)
    private val publicKey: PublicKey? = LicenceFormat.parsePublicKey(config.publicKeyBase64)

    /** Code a 8 caracteres affiche au client, par exemple "K7F3-92QX". */
    val deviceCode: String by lazy { LicenceFormat.deviceCodeFor(stableId()) }

    fun status(nowMillis: Long = System.currentTimeMillis()): LicenceStatus {
        if (!config.required) return LicenceStatus.Valid(expiryDay = Int.MAX_VALUE, daysLeft = Int.MAX_VALUE)
        if (publicKey == null) return LicenceStatus.NotConfigured
        if (prefs.getBoolean(KEY_BLOCKED, false)) return LicenceStatus.Blocked
        val stored = prefs.getString(KEY_LICENCE, null) ?: return LicenceStatus.Missing
        return check(publicKey, stored, nowMillis).also { recordSeen(it, nowMillis) }
    }

    /** Enregistre la cle si elle est valable pour ce telephone. */
    fun activate(input: String, nowMillis: Long = System.currentTimeMillis()): LicenceStatus {
        if (publicKey == null) return LicenceStatus.NotConfigured
        if (prefs.getBoolean(KEY_BLOCKED, false)) return LicenceStatus.Blocked
        val result = check(publicKey, input, nowMillis)
        if (result is LicenceStatus.Valid) {
            prefs.edit().putString(KEY_LICENCE, LicenceFormat.normalize(input)).apply()
        }
        recordSeen(result, nowMillis)
        return result
    }

    /** Lit l'heure reelle et la liste de blocage. Sans internet, ne fait rien. */
    suspend fun refreshOnline() {
        if (!config.required) return
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(LicenceConfig.TIME_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                try {
                    conn.responseCode
                    val serverTime = conn.date
                    if (serverTime > prefs.getLong(KEY_NETWORK_TIME, 0L)) {
                        prefs.edit().putLong(KEY_NETWORK_TIME, serverTime).apply()
                    }
                } finally {
                    conn.disconnect()
                }
            }
            if (config.blocklistUrl.isNotBlank()) {
                runCatching {
                    val conn = URL(config.blocklistUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 8_000
                    try {
                        if (conn.responseCode == 200) {
                            val mine = LicenceFormat.normalize(deviceCode)
                            val blocked = conn.inputStream.bufferedReader().useLines { lines ->
                                lines.any { LicenceFormat.normalize(it) == mine }
                            }
                            prefs.edit().putBoolean(KEY_BLOCKED, blocked).apply()
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        }
    }

    private fun check(key: PublicKey, text: String, nowMillis: Long): LicenceStatus {
        val normalized = LicenceFormat.normalize(text)
        if (normalized.length != LicenceFormat.KEY_CHARS) return LicenceStatus.Invalid
        val bytes = LicenceFormat.decode(normalized)
        val payload = bytes.copyOfRange(0, LicenceFormat.PAYLOAD_LEN)
        val signature = bytes.copyOfRange(LicenceFormat.PAYLOAD_LEN, LicenceFormat.PAYLOAD_LEN + LicenceFormat.SIG_LEN)
        if (payload[0].toInt() != LicenceFormat.VERSION) return LicenceStatus.Invalid
        if (!LicenceFormat.verifySignature(key, payload, signature)) return LicenceStatus.Invalid

        val device = LicenceFormat.decode(LicenceFormat.normalize(deviceCode))
        for (i in device.indices) {
            if (payload[1 + i] != device[i]) return LicenceStatus.WrongDevice
        }

        // Heure du telephone remise en arriere par rapport a ce que l'appli a deja vu.
        if (nowMillis + CLOCK_TOLERANCE_MS < prefs.getLong(KEY_LAST_SEEN, 0L)) return LicenceStatus.ClockChanged

        val expiryDay = ((payload[6].toInt() and 0xFF) shl 8) or (payload[7].toInt() and 0xFF)
        val effectiveNow = maxOf(nowMillis, prefs.getLong(KEY_NETWORK_TIME, 0L))
        val today = (effectiveNow / LicenceFormat.DAY_MS).toInt()
        if (today > expiryDay) return LicenceStatus.Expired(expiryDay)
        return LicenceStatus.Valid(expiryDay, expiryDay - today)
    }

    /** Retient la date la plus recente vue, sans depasser la fin de la licence. */
    private fun recordSeen(status: LicenceStatus, nowMillis: Long) {
        val expiryDay = when (status) {
            is LicenceStatus.Valid -> status.expiryDay
            is LicenceStatus.Expired -> status.expiryDay
            else -> return
        }
        val cap = (expiryDay + 1L) * LicenceFormat.DAY_MS
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)
        val next = maxOf(lastSeen, minOf(nowMillis, cap))
        if (next != lastSeen) prefs.edit().putLong(KEY_LAST_SEEN, next).apply()
    }

    @SuppressLint("HardwareIds")
    private fun stableId(): String {
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrBlank()) return androidId
        return prefs.getString(KEY_FALLBACK_ID, null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_FALLBACK_ID, it).apply() }
    }

    private companion object {
        const val KEY_LICENCE = "licence"
        const val KEY_LAST_SEEN = "last_seen"
        const val KEY_NETWORK_TIME = "network_time"
        const val KEY_BLOCKED = "blocked"
        const val KEY_FALLBACK_ID = "fallback_id"
        const val CLOCK_TOLERANCE_MS = 36L * 60 * 60 * 1000
    }
}
