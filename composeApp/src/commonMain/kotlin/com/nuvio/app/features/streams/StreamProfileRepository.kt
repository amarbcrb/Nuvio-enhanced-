package com.nuvio.app.features.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A way of watching. The three language profiles have their own favorite sources and fixed link
 * languages; "All" searches every source in every language, without favorites or filters.
 */
enum class StreamProfile(val id: String) {
    FRENCH_DUB("fr"),
    ARABIC_SUB("ar"),
    ENGLISH("en"),
    ALL("all"),
    ;

    /** Subtitle language the player picks for this profile, null to keep the player setting. */
    val subtitleLanguage: String?
        get() = when (this) {
            ARABIC_SUB -> "ar"
            ENGLISH -> "fr"
            else -> null
        }

    /** Whether the profile has favorites, a link language and a search scope. */
    val hasPreferences: Boolean
        get() = this != ALL

    companion object {
        /** The French profile keeps the settings made before profiles existed. */
        val FRENCH = FRENCH_DUB
        fun fromId(id: String?): StreamProfile? = entries.firstOrNull { it.id == id }
    }
}

/** Link languages and dub order fixed by each profile (the French one keeps the user's settings). */
internal fun StreamProfile.fixedLanguages(): StreamLanguagePreferences? = when (this) {
    StreamProfile.FRENCH_DUB -> null
    StreamProfile.ARABIC_SUB -> StreamLanguagePreferences(
        linkLanguages = setOf("ar"),
        includeSubtitled = true,
        dubMode = StreamDubMode.SubtitledFirst,
    )
    StreamProfile.ENGLISH -> StreamLanguagePreferences(
        linkLanguages = setOf("en"),
        includeSubtitled = true,
        dubMode = StreamDubMode.Any,
    )
    StreamProfile.ALL -> StreamLanguagePreferences(dubMode = StreamDubMode.Any)
}

/**
 * Which profile each title uses (remembered once changed), the profile of new titles, and which
 * profile the favorites and link languages currently follow.
 */
object StreamProfileRepository {
    private const val TITLES_KEY = "stream_profile_titles_v1"
    private const val DEFAULT_KEY = "stream_profile_default_v1"
    private const val SEPARATOR = "\t"

    private val _titleProfiles = MutableStateFlow<Map<String, StreamProfile>>(emptyMap())
    val titleProfiles: StateFlow<Map<String, StreamProfile>> = _titleProfiles.asStateFlow()

    private val _defaultProfile = MutableStateFlow(StreamProfile.FRENCH)

    /** Profile of the titles that have none of their own. */
    val defaultProfile: StateFlow<StreamProfile> = _defaultProfile.asStateFlow()

    private val _active = MutableStateFlow(StreamProfile.FRENCH)

    /** The profile the favorites and link languages follow right now. */
    val active: StateFlow<StreamProfile> = _active.asStateFlow()

    /** The title whose links are being searched, used by the search sheet and manual choices. */
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
        _defaultProfile.value = StreamProfile.fromId(BingeGroupCacheStorage.load(DEFAULT_KEY)) ?: StreamProfile.FRENCH
    }

    /** Called when the app profile changes: reload the remembered titles and go back to French. */
    fun onProfileChanged() {
        loaded = false
        ensureLoaded()
        switchActive(StreamProfile.FRENCH)
    }

    fun profileFor(titleKey: String?): StreamProfile {
        ensureLoaded()
        return titleKey?.let { _titleProfiles.value[it] } ?: _defaultProfile.value
    }

    /** Makes the favorites and link languages follow the profile of [titleKey]. */
    fun activateForTitle(titleKey: String?) {
        currentTitleKey = titleKey
        switchActive(profileFor(titleKey))
    }

    /** Makes the favorites and link languages follow [profile] (fallback search, settings page). */
    fun activateTemporarily(profile: StreamProfile) {
        switchActive(profile)
    }

    /** Remembers [profile] for [titleKey]. */
    fun setForTitle(titleKey: String, profile: StreamProfile) {
        ensureLoaded()
        val updated = _titleProfiles.value + (titleKey to profile)
        if (updated != _titleProfiles.value) {
            _titleProfiles.value = updated
            BingeGroupCacheStorage.save(
                TITLES_KEY,
                updated.entries.joinToString("\n") { "${it.key}$SEPARATOR${it.value.id}" },
            )
        }
        if (currentTitleKey == titleKey) switchActive(profile)
    }

    /** Profile used by the titles that have none of their own. */
    fun setDefault(profile: StreamProfile) {
        ensureLoaded()
        if (_defaultProfile.value == profile) return
        _defaultProfile.value = profile
        BingeGroupCacheStorage.save(DEFAULT_KEY, profile.id)
    }

    /**
     * A link picked by hand tells which profile the title is watched with: Arabic audio or subtitles
     * select Arabic, French audio French, English audio English. Other links change nothing.
     */
    fun learnFromManualChoice(stream: StreamItem) {
        val titleKey = currentTitleKey ?: return
        if (profileFor(titleKey) == StreamProfile.ALL) return
        val info = runCatching {
            StreamLanguageDetector.detect(stream, cloudStreamProviderLanguages()[stream.addonId])
        }.getOrNull() ?: return
        val profile = when {
            "ar" in info.audio || ("ar" in info.subtitles && "fr" !in info.audio) -> StreamProfile.ARABIC_SUB
            "fr" in info.audio -> StreamProfile.FRENCH_DUB
            "en" in info.audio && info.audio.size == 1 -> StreamProfile.ENGLISH
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
        if (_active.value == StreamProfile.FRENCH) null else "${base}_${_active.value.id}"
}
