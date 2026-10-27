package com.nuvio.app.features.livetv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_theme_documentary
import nuvio.composeapp.generated.resources.live_tv_theme_general
import nuvio.composeapp.generated.resources.live_tv_theme_kids
import nuvio.composeapp.generated.resources.live_tv_theme_movies
import nuvio.composeapp.generated.resources.live_tv_theme_music
import nuvio.composeapp.generated.resources.live_tv_theme_news
import nuvio.composeapp.generated.resources.live_tv_theme_other
import nuvio.composeapp.generated.resources.live_tv_theme_religious
import nuvio.composeapp.generated.resources.live_tv_theme_sports
import org.jetbrains.compose.resources.StringResource

/** Themes the TV Hub sorts channels into, read from their group and name. */
enum class LiveTvTheme(
    val key: String,
    val label: StringResource,
    private val keywords: List<String>,
) {
    News(
        "news",
        Res.string.live_tv_theme_news,
        listOf("news", "info", "actualit", "journal", "bfm", "cnews", "lci", "euronews", "jazeera", "أخبار", "نيوز", "اخبار"),
    ),
    Sports(
        "sports",
        Res.string.live_tv_theme_sports,
        listOf("sport", "foot", "bein", "eurosport", "dazn", "ligue", "league", "racing", "golf", "tennis", "رياض"),
    ),
    Kids(
        "kids",
        Res.string.live_tv_theme_kids,
        listOf("kids", "kid ", "enfant", "jeunesse", "cartoon", "animation", "junior", "gulli", "nick", "baby", "أطفال", "اطفال"),
    ),
    Movies(
        "movies",
        Res.string.live_tv_theme_movies,
        listOf("movie", "film", "cinema", "ciné", "cine", "series", "série", "drama", "action", "مسلسل", "أفلام", "افلام"),
    ),
    Music(
        "music",
        Res.string.live_tv_theme_music,
        listOf("music", "musique", "mtv", "hits", "clip", "موسيق", "أغاني"),
    ),
    Documentary(
        "documentary",
        Res.string.live_tv_theme_documentary,
        listOf("documentar", "docu", "discovery", "nature", "science", "history", "histoire", "travel", "voyage", "geographic", "وثائق"),
    ),
    Religious(
        "religious",
        Res.string.live_tv_theme_religious,
        listOf("relig", "coran", "quran", "islam", "church", "catholi", "gospel", "قرآن", "القران"),
    ),
    General(
        "general",
        Res.string.live_tv_theme_general,
        listOf("general", "généraliste", "generaliste", "public", "entertainment", "divertissement", "family", "lifestyle"),
    ),
    Other("other", Res.string.live_tv_theme_other, emptyList()),
    ;

    internal fun matches(text: String): Boolean = keywords.any { it in text }

    companion object {
        /** The theme of [channel]: its group decides first, then its name. */
        fun of(channel: LiveTvChannel): LiveTvTheme {
            val group = channel.group.orEmpty().lowercase()
            val name = channel.name.lowercase()
            return entries.firstOrNull { theme -> theme != Other && theme.matches(group) }
                ?: entries.firstOrNull { theme -> theme != Other && theme != General && theme.matches(name) }
                ?: if (group.isBlank() || "undefined" in group) General else Other
        }

        fun fromKey(key: String): LiveTvTheme? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A ready-made playlist of channels their owners broadcast for free, published by the iptv-org
 * project. [theme] names theme playlists; the others use [name].
 */
data class LiveTvFreeSource(
    val name: String,
    val url: String,
    val theme: LiveTvTheme? = null,
)

private const val IPTV_ORG = "https://iptv-org.github.io/iptv"

val LiveTvFreeSources: List<LiveTvFreeSource> = listOf(
    LiveTvFreeSource("France", "$IPTV_ORG/countries/fr.m3u"),
    LiveTvFreeSource("Belgique", "$IPTV_ORG/countries/be.m3u"),
    LiveTvFreeSource("Suisse", "$IPTV_ORG/countries/ch.m3u"),
    LiveTvFreeSource("Canada", "$IPTV_ORG/countries/ca.m3u"),
    LiveTvFreeSource("Algérie · الجزائر", "$IPTV_ORG/countries/dz.m3u"),
    LiveTvFreeSource("Maroc · المغرب", "$IPTV_ORG/countries/ma.m3u"),
    LiveTvFreeSource("Tunisie · تونس", "$IPTV_ORG/countries/tn.m3u"),
    LiveTvFreeSource("Français", "$IPTV_ORG/languages/fra.m3u"),
    LiveTvFreeSource("العربية", "$IPTV_ORG/languages/ara.m3u"),
    LiveTvFreeSource("Taqbaylit", "$IPTV_ORG/languages/kab.m3u"),
    LiveTvFreeSource("News", "$IPTV_ORG/categories/news.m3u", LiveTvTheme.News),
    LiveTvFreeSource("Sports", "$IPTV_ORG/categories/sports.m3u", LiveTvTheme.Sports),
    LiveTvFreeSource("Kids", "$IPTV_ORG/categories/kids.m3u", LiveTvTheme.Kids),
    LiveTvFreeSource("Movies", "$IPTV_ORG/categories/movies.m3u", LiveTvTheme.Movies),
    LiveTvFreeSource("Music", "$IPTV_ORG/categories/music.m3u", LiveTvTheme.Music),
    LiveTvFreeSource("Documentary", "$IPTV_ORG/categories/documentary.m3u", LiveTvTheme.Documentary),
)

/** A TV Hub folder: a theme, or the channels of one playlist. */
data class LiveTvFolder(
    val key: String,
    val theme: LiveTvTheme? = null,
    val sourceName: String? = null,
    val channelCount: Int,
) {
    fun contains(channel: LiveTvChannel): Boolean = when {
        theme != null -> LiveTvTheme.of(channel) == theme
        sourceName != null -> channel.playlistName == sourceName
        else -> true
    }

    companion object {
        fun themeKey(theme: LiveTvTheme) = "theme:${theme.key}"
        fun sourceKey(name: String) = "source:$name"
    }
}

/** Theme folders that have channels, then one folder per playlist when there are several. */
fun buildLiveTvFolders(channels: List<LiveTvChannel>): List<LiveTvFolder> {
    val byTheme = channels.groupingBy { LiveTvTheme.of(it) }.eachCount()
    val themes = LiveTvTheme.entries.mapNotNull { theme ->
        byTheme[theme]?.let { count -> LiveTvFolder(LiveTvFolder.themeKey(theme), theme = theme, channelCount = count) }
    }
    val bySource = channels.mapNotNull { it.playlistName?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount()
    val sources = if (bySource.size > 1) {
        bySource.map { (name, count) -> LiveTvFolder(LiveTvFolder.sourceKey(name), sourceName = name, channelCount = count) }
    } else {
        emptyList()
    }
    return themes + sources
}

/** The same channel from several playlists, shown once; [alternates] are its other streams. */
data class LiveTvChannelEntry(
    val channel: LiveTvChannel,
    val alternates: List<LiveTvChannel> = emptyList(),
)

private val channelNoise = Regex("""\((?:[^)]*)\)|\[(?:[^]]*)]""")
private val qualityWords = setOf("hd", "fhd", "uhd", "sd", "4k", "720p", "1080p", "1080i", "2160p", "hevc", "h265")

/** Name used to recognise one channel across playlists: no quality tags, brackets or punctuation. */
internal fun liveTvChannelMatchKey(name: String): String =
    name.lowercase()
        .replace(channelNoise, " ")
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() && it !in qualityWords }
        .joinToString("")
        .ifEmpty { name.lowercase() }

/** Groups channels that share a name, keeping the first one met and the others as alternates. */
fun List<LiveTvChannel>.mergedByChannelName(): List<LiveTvChannelEntry> =
    groupBy { liveTvChannelMatchKey(it.name) }
        .values
        .map { sameName ->
            LiveTvChannelEntry(channel = sameName.first(), alternates = sameName.drop(1).distinctBy { it.streamUrl })
        }

/** Folders the user pinned, shown in their own row at the top of the TV Hub. */
object LiveTvFavoriteFolders {
    private val _keys = MutableStateFlow<List<String>>(emptyList())
    val keys: StateFlow<List<String>> = _keys.asStateFlow()
    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _keys.value = LiveTvStorage.loadFavoriteFoldersBlob()
            ?.split('\n')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
    }

    fun toggle(key: String) {
        ensureLoaded()
        val current = _keys.value
        val updated = if (key in current) current - key else current + key
        _keys.value = updated
        LiveTvStorage.saveFavoriteFoldersBlob(updated.joinToString("\n"))
    }
}
