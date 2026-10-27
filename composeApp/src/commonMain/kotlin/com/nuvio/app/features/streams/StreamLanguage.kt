package com.nuvio.app.features.streams

import com.nuvio.app.features.cloudstream.CloudStreamPluginItem
import com.nuvio.app.features.cloudstream.CloudStreamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Audio and subtitle languages of a stream, as far as they can be read from its name, description and
 * filename. Language codes are ISO 639-1 (`fr`, `en`, `ar`, ...).
 */
data class StreamLanguageInfo(
    val audio: Set<String> = emptySet(),
    val subtitles: Set<String> = emptySet(),
    val multi: Boolean = false,
    val dubbed: Boolean = false,
    val subbed: Boolean = false,
    /** Language of the CloudStream provider the stream comes from, used when the stream says nothing. */
    val providerLanguage: String? = null,
) {
    private val hasStreamInfo: Boolean
        get() = audio.isNotEmpty() || subtitles.isNotEmpty() || multi || dubbed || subbed

    /** Short badge text such as "VF", "VOSTFR", "MULTI", "EN · SUB", or null when nothing is known. */
    val label: String?
        get() {
            val parts = buildList {
                if (multi) add("MULTI")
                audio.sorted().forEach { code -> add(if (code == "fr") "VF" else code.uppercase()) }
                subtitles.sorted().forEach { code -> add(if (code == "fr") "VOSTFR" else "SUB ${code.uppercase()}") }
                if (dubbed && audio.isEmpty() && !multi) add("DUB")
                if (subbed && subtitles.isEmpty()) add("SUB")
            }.distinct().take(3)
            if (parts.isNotEmpty()) return parts.joinToString(" · ")
            return providerLanguage?.takeIf { !hasStreamInfo }?.uppercase()
        }
}

object StreamLanguageDetector {
    private val tokenSeparator = Regex("[^\\p{L}\\p{N}]+")

    private val audioWords: Map<String, String> = buildMap {
        listOf("VF", "VFF", "VFQ", "VFI", "VFB", "VF2", "TRUEFRENCH", "FRENCH", "FRANCAIS", "FRANÇAIS", "FRA", "FRE")
            .forEach { put(it, "fr") }
        listOf("ENGLISH", "ENG", "ANGLAIS").forEach { put(it, "en") }
        listOf("ARABIC", "ARA", "ARABE", "عربي", "العربية").forEach { put(it, "ar") }
        listOf("SPANISH", "ESPANOL", "ESPAÑOL", "CASTELLANO", "LATINO", "ESP", "SPA").forEach { put(it, "es") }
        listOf("ITALIAN", "ITALIANO", "ITA").forEach { put(it, "it") }
        listOf("GERMAN", "DEUTSCH", "GER", "DEU").forEach { put(it, "de") }
        listOf("PORTUGUESE", "PORTUGUES", "PORTUGUÊS", "PTBR").forEach { put(it, "pt") }
        listOf("JAPANESE", "JAPONAIS", "JAP", "JPN").forEach { put(it, "ja") }
        listOf("KOREAN", "COREEN", "CORÉEN", "KOR").forEach { put(it, "ko") }
        listOf("HINDI", "HIN").forEach { put(it, "hi") }
        listOf("TURKISH", "TURKCE", "TÜRKÇE").forEach { put(it, "tr") }
        listOf("RUSSIAN", "RUS").forEach { put(it, "ru") }
    }

    /** Two-letter codes only count when written in capitals, so words like "en" or "de" in titles do not match. */
    private val upperCaseCodes = mapOf("FR" to "fr", "EN" to "en", "AR" to "ar")

    private val subtitleWords: Map<String, String?> = mapOf(
        "VOSTFR" to "fr", "SUBFRENCH" to "fr", "STFR" to "fr", "VOSTF" to "fr",
        "VOSTEN" to "en", "VOSTENG" to "en", "VOSTA" to "en", "ENGSUB" to "en",
        "VOSTAR" to "ar", "مترجم" to "ar",
        "LEGENDADO" to "pt",
        "VOST" to null, "VOSTO" to null, "SUB" to null, "SUBS" to null, "SUBBED" to null,
        "SUBTITLED" to null, "SOFTSUB" to null, "HARDSUB" to null,
    )

    private val dubWords = mapOf(
        "DUB" to null, "DUBBED" to null, "DOBLADO" to "es", "DUBLADO" to "pt", "مدبلج" to "ar",
    )

    private val flags = mapOf(
        "🇫🇷" to "fr", "🇬🇧" to "en", "🇺🇸" to "en", "🇸🇦" to "ar", "🇪🇸" to "es", "🇲🇽" to "es",
        "🇮🇹" to "it", "🇩🇪" to "de", "🇧🇷" to "pt", "🇵🇹" to "pt", "🇯🇵" to "ja", "🇰🇷" to "ko",
        "🇮🇳" to "hi", "🇹🇷" to "tr", "🇷🇺" to "ru",
    )

    fun detect(stream: StreamItem, providerLanguage: String? = null): StreamLanguageInfo =
        detect(
            texts = listOfNotNull(
                stream.name,
                stream.title,
                stream.description,
                stream.sourceName,
                stream.behaviorHints.filename,
            ),
            providerLanguage = providerLanguage,
        )

    fun detect(texts: List<String>, providerLanguage: String? = null): StreamLanguageInfo {
        val text = texts.joinToString(" ")
        val audio = mutableSetOf<String>()
        val subtitles = mutableSetOf<String>()
        var multi = false
        var dubbed = false
        var subbed = false

        val rawTokens = text.split(tokenSeparator).filter { it.isNotEmpty() }
        var previousSubtitleMarker = false
        for (raw in rawTokens) {
            val token = raw.uppercase()
            val tokenLanguage = audioWords[token] ?: upperCaseCodes[raw]
            when {
                previousSubtitleMarker && tokenLanguage != null -> {
                    subtitles += tokenLanguage
                    previousSubtitleMarker = false
                    continue
                }
                token in subtitleWords -> {
                    subbed = true
                    subtitleWords[token]?.let { subtitles += it }
                    previousSubtitleMarker = subtitleWords[token] == null
                    continue
                }
                token in dubWords -> {
                    dubbed = true
                    dubWords[token]?.let { audio += it }
                }
                token == "MULTI" || token == "MULTILANG" || token == "DUAL" -> multi = true
                tokenLanguage != null -> audio += tokenLanguage
            }
            previousSubtitleMarker = false
        }
        flags.forEach { (flag, code) -> if (flag in text) audio += code }
        // "VF + VOSTFR" style labels list the French subtitles too; keep both.
        return StreamLanguageInfo(
            audio = audio,
            subtitles = subtitles,
            multi = multi,
            dubbed = dubbed,
            subbed = subbed,
            providerLanguage = providerLanguage?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
        )
    }
}

/** Languages offered in the link and extension language settings, as (code, native name). */
val StreamLanguageChoices: List<Pair<String, String>> = listOf(
    "fr" to "Français",
    "en" to "English",
    "ar" to "العربية",
    "es" to "Español",
    "it" to "Italiano",
    "de" to "Deutsch",
    "pt" to "Português",
    "tr" to "Türkçe",
    "ru" to "Русский",
    "hi" to "हिन्दी",
    "ja" to "日本語",
    "ko" to "한국어",
)

fun streamLanguageDisplayName(code: String): String =
    StreamLanguageChoices.firstOrNull { it.first == code.lowercase() }?.second ?: code.uppercase()

data class StreamLanguagePreferences(
    /** Link languages to keep; empty keeps every link. */
    val linkLanguages: Set<String> = emptySet(),
    /** Also keep links that are only subtitled in one of [linkLanguages] (VOSTFR for French). */
    val includeSubtitled: Boolean = true,
    /** Hide links whose language cannot be detected. */
    val hideUnknown: Boolean = false,
    /** CloudStream extension languages to use; empty uses every extension. */
    val extensionLanguages: Set<String> = emptySet(),
) {
    fun encode(): String = listOf(
        "links=${linkLanguages.sorted().joinToString(",")}",
        "sub=${if (includeSubtitled) 1 else 0}",
        "unknown=${if (hideUnknown) 1 else 0}",
        "ext=${extensionLanguages.sorted().joinToString(",")}",
    ).joinToString(";")

    companion object {
        fun decode(raw: String?): StreamLanguagePreferences {
            if (raw.isNullOrBlank()) return StreamLanguagePreferences()
            val values = raw.split(';').mapNotNull { part ->
                part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
            }.toMap()
            fun codes(key: String) = values[key].orEmpty().split(',').map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }.toSet()
            return StreamLanguagePreferences(
                linkLanguages = codes("links"),
                includeSubtitled = values["sub"] != "0",
                hideUnknown = values["unknown"] == "1",
                extensionLanguages = codes("ext"),
            )
        }
    }
}

object StreamLanguagePreferencesRepository {
    private val _preferences = MutableStateFlow(StreamLanguagePreferences())
    val preferences: StateFlow<StreamLanguagePreferences> = _preferences.asStateFlow()

    /** Shows every link for the rest of the session without changing the saved languages. */
    private val _linkFilterPaused = MutableStateFlow(false)
    val linkFilterPaused: StateFlow<Boolean> = _linkFilterPaused.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun snapshot(): StreamLanguagePreferences {
        ensureLoaded()
        return _preferences.value
    }

    fun update(transform: (StreamLanguagePreferences) -> StreamLanguagePreferences) {
        ensureLoaded()
        val updated = transform(_preferences.value)
        if (updated == _preferences.value) return
        _preferences.value = updated
        StreamBadgeSettingsStorage.saveStreamLanguagePreferences(updated.encode())
    }

    fun toggleLinkLanguage(code: String) = update { current ->
        current.copy(linkLanguages = current.linkLanguages.toggled(code))
    }

    fun toggleExtensionLanguage(code: String) = update { current ->
        current.copy(extensionLanguages = current.extensionLanguages.toggled(code))
    }

    fun setLinkFilterPaused(paused: Boolean) {
        _linkFilterPaused.value = paused
    }

    /** Changes whenever the links a stream search returns could change. */
    fun requestSignature(): String {
        val preferences = snapshot()
        return "${preferences.encode()}:paused=${_linkFilterPaused.value}"
    }

    private fun loadFromDisk() {
        hasLoaded = true
        _preferences.value = StreamLanguagePreferences.decode(StreamBadgeSettingsStorage.loadStreamLanguagePreferences())
    }

    private fun Set<String>.toggled(code: String): Set<String> {
        val normalized = code.lowercase()
        return if (normalized in this) this - normalized else this + normalized
    }
}

/** Whether this CloudStream extension may be used under the extension language preference. */
fun CloudStreamPluginItem.isAllowedByLanguagePreferences(): Boolean {
    val allowed = StreamLanguagePreferencesRepository.snapshot().extensionLanguages
    if (allowed.isEmpty()) return true
    val language = metadata.language?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return true
    return language in allowed || language == "multi" || language == "mul"
}

/** CloudStream provider language by stream group id, for streams that do not name their language. */
internal fun cloudStreamProviderLanguages(): Map<String, String> =
    CloudStreamRepository.uiState.value.plugins
        .mapNotNull { plugin ->
            plugin.metadata.language?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                ?.let { cloudStreamAddonId(plugin.metadata.id.value) to it }
        }
        .toMap()

internal fun StreamLanguageInfo.matches(preferences: StreamLanguagePreferences): Boolean {
    val languages = preferences.linkLanguages
    if (languages.isEmpty() || multi) return true
    if (audio.any { it in languages }) return true
    if (preferences.includeSubtitled && subtitles.any { it in languages }) return true
    if (audio.isEmpty() && subtitles.isEmpty()) {
        providerLanguage?.let { return it in languages }
        return !preferences.hideUnknown
    }
    return false
}

/** Removes links outside the preferred languages, unless the filter is paused. */
internal fun AddonStreamGroup.filteredByLinkLanguage(
    providerLanguages: Map<String, String>,
): AddonStreamGroup {
    val preferences = StreamLanguagePreferencesRepository.snapshot()
    if (preferences.linkLanguages.isEmpty() || StreamLanguagePreferencesRepository.linkFilterPaused.value) return this
    if (streams.isEmpty()) return this
    val providerLanguage = providerLanguages[addonId]
    val kept = streams.filter { stream ->
        StreamLanguageDetector.detect(stream, providerLanguage).matches(preferences)
    }
    return if (kept.size == streams.size) this else copy(streams = kept)
}
