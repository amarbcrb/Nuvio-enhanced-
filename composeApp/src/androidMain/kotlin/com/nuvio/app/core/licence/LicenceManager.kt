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
    val blocklistUrl: String,
) {
    companion object {
        const val TIME_URL = "https://www.google.com/generate_204"

        fun from(context: Context): LicenceConfig {
            val res = context.resources
            return LicenceConfig(
                required = res.getBoolean(R.bool.licence_required),
                publicKeyBase64 = res.getString(R.string.licence_public_key).replace(Regex("\\s+"), ""),
                blocklistUrl = res.getString(R.string.licence_blocklist_url).trim(),
            )
        }
    }
}

internal sealed interface LicenceStatus {
    data object Missing : LicenceStatus
    data class Valid(val expiryDay: Int, val daysLeft: Int, val clientName: String? = null) : LicenceStatus
    data class NotYetValid(val startDay: Int) : LicenceStatus
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
    const val VERSION = 2 // sans nom
    const val VERSION_NAMED = 3 // avec le nom du client : + longueur(1) + nom en UTF-8
    const val PAYLOAD_LEN = 10 // version(1) + appareil(5) + jour de debut(2) + jour de fin(2)
    const val SIG_LEN = 64
    const val KEY_CHARS = 119
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
        // Cle de renouvellement en attente : elle remplace la cle actuelle des que sa periode commence.
        val next = prefs.getString(KEY_NEXT_LICENCE, null)
        if (next != null) {
            val nextStatus = check(publicKey, next, nowMillis)
            when (nextStatus) {
                is LicenceStatus.Valid -> {
                    prefs.edit().putString(KEY_LICENCE, next).remove(KEY_NEXT_LICENCE).apply()
                    recordSeen(nextStatus, nowMillis)
                    return nextStatus
                }
                is LicenceStatus.NotYetValid -> Unit
                else -> prefs.edit().remove(KEY_NEXT_LICENCE).apply()
            }
        }
        val stored = prefs.getString(KEY_LICENCE, null)
            ?: return nextStart(nowMillis)?.let { LicenceStatus.NotYetValid(it) } ?: LicenceStatus.Missing
        return check(publicKey, stored, nowMillis).also { recordSeen(it, nowMillis) }
    }

    /**
     * Enregistre la cle si elle est faite pour ce telephone. Une cle qui commence plus tard
     * (renouvellement en avance) est gardee de cote et prend le relais le jour de son debut.
     */
    fun activate(input: String, nowMillis: Long = System.currentTimeMillis()): LicenceStatus {
        if (publicKey == null) return LicenceStatus.NotConfigured
        if (prefs.getBoolean(KEY_BLOCKED, false)) return LicenceStatus.Blocked
        val result = check(publicKey, input, nowMillis)
        when (result) {
            is LicenceStatus.Valid -> prefs.edit().putString(KEY_LICENCE, LicenceFormat.normalize(input)).apply()
            is LicenceStatus.NotYetValid -> prefs.edit().putString(KEY_NEXT_LICENCE, LicenceFormat.normalize(input)).apply()
            else -> Unit
        }
        recordSeen(result, nowMillis)
        return result
    }

    /** Premier jour de la cle de renouvellement en attente, ou null. */
    fun nextStart(nowMillis: Long = System.currentTimeMillis()): Int? {
        val key = publicKey ?: return null
        val next = prefs.getString(KEY_NEXT_LICENCE, null) ?: return null
        return (check(key, next, nowMillis) as? LicenceStatus.NotYetValid)?.startDay
    }

    /** Lit l'heure reelle et la liste de blocage. Sans internet, ne fait rien. */
    suspend fun refreshOnline() {
        refreshTime()
        refreshBlocklist()
    }

    /** Heure reelle (en-tete HTTP Date), pour ne pas dependre de l'horloge du telephone. */
    suspend fun refreshTime() {
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
        }
    }

    /**
     * Relit la liste de blocage. Pour un fichier sur GitHub, passe par l'API GitHub, qui donne la
     * version a jour tout de suite (le lien raw peut garder une ancienne copie quelques minutes).
     * Avec l'ETag, une liste inchangee repond 304 : rien a telecharger, et GitHub ne le compte pas
     * dans sa limite de requetes. Si l'API refuse (limite atteinte, panne), on lit le lien raw.
     */
    suspend fun refreshBlocklist() {
        if (!config.required || config.blocklistUrl.isBlank()) return
        withContext(Dispatchers.IO) {
            val api = githubApiUrl(config.blocklistUrl)
            val viaApi = api != null && runCatching { readBlocklistFromApi(api) }.getOrDefault(false)
            if (!viaApi) runCatching { readBlocklistRaw() }
        }
    }

    /** true si la reponse a ete utilisee (200 lu, ou 304 inchange). */
    private fun readBlocklistFromApi(api: String): Boolean {
        val conn = URL(api).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.useCaches = false
        conn.setRequestProperty("Accept", "application/vnd.github.raw")
        conn.setRequestProperty("User-Agent", "NuvioPro")
        prefs.getString(KEY_BLOCK_ETAG, null)?.let { conn.setRequestProperty("If-None-Match", it) }
        try {
            return when (conn.responseCode) {
                304 -> true
                200 -> {
                    val blocked = conn.inputStream.bufferedReader().useLines { lines -> lines.any(::isMyCode) }
                    prefs.edit()
                        .putBoolean(KEY_BLOCKED, blocked)
                        .putString(KEY_BLOCK_ETAG, conn.getHeaderField("ETag"))
                        .apply()
                    true
                }
                else -> false
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun readBlocklistRaw() {
        val separator = if (config.blocklistUrl.contains('?')) '&' else '?'
        val conn = URL(config.blocklistUrl + separator + "t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.useCaches = false
        try {
            if (conn.responseCode == 200) {
                val blocked = conn.inputStream.bufferedReader().useLines { lines -> lines.any(::isMyCode) }
                prefs.edit().putBoolean(KEY_BLOCKED, blocked).apply()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun isMyCode(line: String): Boolean = LicenceFormat.normalize(line) == LicenceFormat.normalize(deviceCode)

    /** https://raw.githubusercontent.com/<proprio>/<depot>/<branche>/<chemin> -> URL de l'API GitHub, sinon null. */
    private fun githubApiUrl(raw: String): String? {
        val match = Regex("""^https://raw\.githubusercontent\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$""").find(raw.trim()) ?: return null
        val (owner, repo, branch, path) = match.destructured
        return "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch"
    }

    private fun check(key: PublicKey, text: String, nowMillis: Long): LicenceStatus {
        val normalized = LicenceFormat.normalize(text)
        val bytes = LicenceFormat.decode(normalized)
        if (bytes.size < LicenceFormat.PAYLOAD_LEN + LicenceFormat.SIG_LEN) return LicenceStatus.Invalid
        if (normalized.length != (bytes.size * 8 + 4) / 5) return LicenceStatus.Invalid
        val version = bytes[0].toInt() and 0xFF
        val payloadLen = when (version) {
            LicenceFormat.VERSION -> LicenceFormat.PAYLOAD_LEN
            LicenceFormat.VERSION_NAMED -> LicenceFormat.PAYLOAD_LEN + 1 + (bytes[LicenceFormat.PAYLOAD_LEN].toInt() and 0xFF)
            else -> return LicenceStatus.Invalid
        }
        if (bytes.size != payloadLen + LicenceFormat.SIG_LEN) return LicenceStatus.Invalid
        val payload = bytes.copyOfRange(0, payloadLen)
        val signature = bytes.copyOfRange(payloadLen, payloadLen + LicenceFormat.SIG_LEN)
        if (!LicenceFormat.verifySignature(key, payload, signature)) return LicenceStatus.Invalid
        val clientName = if (version == LicenceFormat.VERSION_NAMED) {
            String(payload, LicenceFormat.PAYLOAD_LEN + 1, payloadLen - LicenceFormat.PAYLOAD_LEN - 1, Charsets.UTF_8).trim().ifEmpty { null }
        } else {
            null
        }

        val device = LicenceFormat.decode(LicenceFormat.normalize(deviceCode))
        for (i in device.indices) {
            if (payload[1 + i] != device[i]) return LicenceStatus.WrongDevice
        }

        // Heure du telephone remise en arriere par rapport a ce que l'appli a deja vu.
        if (nowMillis + CLOCK_TOLERANCE_MS < prefs.getLong(KEY_LAST_SEEN, 0L)) return LicenceStatus.ClockChanged

        val startDay = ((payload[6].toInt() and 0xFF) shl 8) or (payload[7].toInt() and 0xFF)
        val expiryDay = ((payload[8].toInt() and 0xFF) shl 8) or (payload[9].toInt() and 0xFF)
        val effectiveNow = maxOf(nowMillis, prefs.getLong(KEY_NETWORK_TIME, 0L))
        val today = (effectiveNow / LicenceFormat.DAY_MS).toInt()
        if (today < startDay) return LicenceStatus.NotYetValid(startDay)
        if (today > expiryDay) return LicenceStatus.Expired(expiryDay)
        return LicenceStatus.Valid(expiryDay, expiryDay - today, clientName)
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
        const val KEY_NEXT_LICENCE = "next_licence"
        const val KEY_LAST_SEEN = "last_seen"
        const val KEY_NETWORK_TIME = "network_time"
        const val KEY_BLOCKED = "blocked"
        const val KEY_BLOCK_ETAG = "blocklist_etag"
        const val KEY_FALLBACK_ID = "fallback_id"
        const val CLOCK_TOLERANCE_MS = 36L * 60 * 60 * 1000
    }
}
