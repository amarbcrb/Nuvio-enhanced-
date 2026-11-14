package com.nuvio.app.features.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A way of watching: its own favorite sources, link languages and subtitle language.
 * French dubbed is the default; Arabic subtitled is picked per title.
 */
enum class StreamProfile(val id: String) {
    FRENCH_DUB("fr"),
    ARABIC_SUB("ar"),
    ;

    val other: StreamProfile
        get() = if (this == FRENCH_DUB) ARABIC_SUB else FRENCH_DUB

    /** Subtitle language the player picks for this profile, null to keep the player setting. */
    val subtitleLanguage: String?
        get() = if (this == ARABIC_SUB) "ar" else null

    companion object {
        val DEFAULT = FRENCH_DUB
        fun fromId(id: String?): StreamProfile? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Which profile each title uses (remembered once changed), and which profile the favorites and link
 * languages currently follow. The French profile keeps the storage used before profiles existed,
 * so nothing set earlier is lost; the Arabic profile has its own favorites and languages.
 */
object StreamProfileRepository {
    private const val TITLES_KEY = "stream_profile_titles_v1"
    private const val SEPARATOR = "\t"

    private val _titleProfiles = MutableStateFlow<Map<String, StreamProfile>>(emptyMap())
    val titleProfiles: StateFlow<Map<String, StreamProfile>> = _titleProfiles.asStateFlow()

    private val _active = MutableStateFlow(StreamProfile.DEFAULT)

    /** The profile the favorites and link languages follow right now. */
    val active: StateFlow<StreamProfile> = _active.asStateFlow()

    /** The title whose links are being searched, used by the profile chip and manual choices. */
    var currentTitleKey: String? = null
        private set

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _titleProfiles.value = BingeGroupCacheStorage.load(TITLES_KEY)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val key = line.substringBefore(SEPARATOR, "").takeIf { it.isNotBlank() }
                val profile = StreamProfile.fromId(line.substringAfter(SEPARATOR, ""))
                if (key != null && profile != null) key to profile else null
            }
            ?.toMap()
            .orEmpty()
    }

    /** Called when the app profile changes: reload the remembered titles and go back to the default. */
    fun onProfileChanged() {
        loaded = false
        ensureLoaded()
        switchActive(StreamProfile.DEFAULT)
    }

    fun profileFor(titleKey: String?): StreamProfile {
        ensureLoaded()
        return titleKey?.let { _titleProfiles.value[it] } ?: StreamProfile.DEFAULT
    }

    /** Makes the favorites and link languages follow the profile of [titleKey]. */
    fun activateForTitle(titleKey: String?) {
        currentTitleKey = titleKey
        switchActive(profileFor(titleKey))
    }

    /** Makes the favorites and link languages follow [profile] (fallback search with the other profile). */
    fun activateTemporarily(profile: StreamProfile) {
        switchActive(profile)
    }

    /** Remembers [profile] for [titleKey]; the default profile is not stored. */
    fun setForTitle(titleKey: String, profile: StreamProfile) {
        ensureLoaded()
        val updated = if (profile == StreamProfile.DEFAULT) {
            _titleProfiles.value - titleKey
        } else {
            _titleProfiles.value + (titleKey to profile)
        }
        if (updated != _titleProfiles.value) {
            _titleProfiles.value = updated
            BingeGroupCacheStorage.save(
                TITLES_KEY,
                updated.entries.joinToString("\n") { "${it.key}$SEPARATOR${it.value.id}" },
            )
        }
        if (currentTitleKey == titleKey) switchActive(profile)
    }

    /**
     * A link picked by hand tells which profile the title is watched with: Arabic audio or subtitles
     * selects the Arabic profile, French audio the French one. Other links change nothing.
     */
    fun learnFromManualChoice(stream: StreamItem) {
        val titleKey = currentTitleKey ?: return
        val info = runCatching {
            StreamLanguageDetector.detect(stream, cloudStreamProviderLanguages()[stream.addonId])
        }.getOrNull() ?: return
        val profile = when {
            "ar" in info.audio || ("ar" in info.subtitles && "fr" !in info.audio) -> StreamProfile.ARABIC_SUB
            "fr" in info.audio -> StreamProfile.FRENCH_DUB
            else -> return
        }
        setForTitle(titleKey, profile)
    }

    private fun switchActive(profile: StreamProfile) {
        if (_active.value == profile) return
        _active.value = profile
        PinnedStreamSourcesRepository.onProfileChanged()
        StreamLanguagePreferencesRepository.onProfileChanged()
    }

    /** Storage key of a per-profile setting; null for the French profile, which keeps the old storage. */
    internal fun storageKey(base: String): String? =
        if (_active.value == StreamProfile.DEFAULT) null else "${base}_${_active.value.id}"
}

/** Link languages of the Arabic profile before the user changes them: Arabic, subtitled first. */
internal val ArabicProfileDefaultLanguages = StreamLanguagePreferences(
    linkLanguages = setOf("ar"),
    includeSubtitled = true,
    dubMode = StreamDubMode.SubtitledFirst,
)
