package com.nuvio.app.features.streams

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.core.ui.GlassCheckRow
import com.nuvio.app.core.ui.GlassSheetSectionLabel
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.streams_favorites_none_available
import nuvio.composeapp.generated.resources.streams_favorites_only_search
import nuvio.composeapp.generated.resources.streams_favorites_only_search_hint
import nuvio.composeapp.generated.resources.streams_favorites_sheet_sources
import nuvio.composeapp.generated.resources.streams_favorites_sheet_title
import org.jetbrains.compose.resources.stringResource

/** A source that can be ticked as a favorite in [FavoriteSourcesSheet]. */
internal data class FavoriteSourceEntry(val key: String, val label: String)

/**
 * Every source the user can tick: the installed sources, the groups shown for this title, and the
 * favorites already saved (even when they are not installed anymore, so they can be unticked).
 */
internal fun favoriteSheetSources(
    sourceOptions: List<StreamSourceOption>,
    groups: List<AddonStreamGroup>,
    pinnedSourceIds: List<String>,
): List<FavoriteSourceEntry> {
    val entries = LinkedHashMap<String, FavoriteSourceEntry>()
    sourceOptions.forEach { option ->
        if (!option.id.startsWith("debrid:")) entries.getOrPut(option.id) { FavoriteSourceEntry(option.id, option.name) }
    }
    groups.forEach { group ->
        val target = group.favoriteTarget() ?: return@forEach
        entries.getOrPut(target.key) { FavoriteSourceEntry(target.key, target.label) }
    }
    pinnedSourceIds.forEach { key ->
        if (key in entries) return@forEach
        val addonId = key.substringBefore('|')
        val sourceName = key.substringAfter('|', missingDelimiterValue = "").takeIf { it.isNotBlank() }
        val addonName = entries[addonId]?.label ?: addonId
        entries[key] = FavoriteSourceEntry(key, if (sourceName != null) "$sourceName ($addonName)" else addonName)
    }
    return entries.values.toList()
}

/**
 * Liquid glass sheet of the favorite sources: tick or untick each source, and choose whether stream
 * searches are limited to the ticked ones. [onDismiss] says whether anything changed, so links reload.
 */
@Composable
internal fun FavoriteSourcesSheet(
    sources: List<FavoriteSourceEntry>,
    pinnedSourceIds: List<String>,
    favoritesOnly: Boolean,
    onDismiss: (changed: Boolean) -> Unit,
) {
    val initialPinned = remember { pinnedSourceIds.toSet() }
    val initialFavoritesOnly = remember { favoritesOnly }
    var onlyFavorites by remember { mutableStateOf(favoritesOnly) }
    GlassStreamSheet(
        title = stringResource(Res.string.streams_favorites_sheet_title),
        onDismiss = {
            val hasFavorite = PinnedStreamSourcesRepository.pinnedSourceIds.value.isNotEmpty()
            StreamSearchScopeRepository.setFavoritesOnly(onlyFavorites && hasFavorite)
            val pinnedChanged = PinnedStreamSourcesRepository.pinnedSourceIds.value.toSet() != initialPinned
            val onlyChanged = StreamSearchScopeRepository.favoritesOnly.value != initialFavoritesOnly
            // The pins only reorder the links already shown, unless only favorites are searched.
            onDismiss(onlyChanged || (pinnedChanged && StreamSearchScopeRepository.favoritesOnly.value))
        },
    ) {
        GlassCheckRow(
            title = stringResource(Res.string.streams_favorites_only_search),
            subtitle = stringResource(Res.string.streams_favorites_only_search_hint),
            checked = onlyFavorites,
            icon = Icons.Rounded.FilterAlt,
            onCheckedChange = { onlyFavorites = it },
        )
        GlassSheetSectionLabel(stringResource(Res.string.streams_favorites_sheet_sources))
        if (sources.isEmpty()) {
            Text(
                text = stringResource(Res.string.streams_favorites_none_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
        }
        sources.forEach { source ->
            val isFavorite = source.key in pinnedSourceIds
            GlassCheckRow(
                title = source.label,
                checked = isFavorite,
                icon = Icons.Rounded.Star,
                onCheckedChange = { checked -> PinnedStreamSourcesRepository.setPinned(source.key, checked) },
            )
        }
    }
}
