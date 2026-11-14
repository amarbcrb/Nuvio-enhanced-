package com.nuvio.app.features.streams

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.GlassCheckRow
import com.nuvio.app.core.ui.GlassSheetSectionLabel
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.streams_favorites_none_available
import nuvio.composeapp.generated.resources.streams_favorites_only_search
import nuvio.composeapp.generated.resources.streams_favorites_only_search_hint
import nuvio.composeapp.generated.resources.streams_favorites_sheet_sources
import nuvio.composeapp.generated.resources.streams_favorites_sheet_title
import nuvio.composeapp.generated.resources.streams_search_scope_all
import nuvio.composeapp.generated.resources.streams_search_scope_title
import nuvio.composeapp.generated.resources.streams_search_scope_unavailable
import nuvio.composeapp.generated.resources.streams_search_single_source
import nuvio.composeapp.generated.resources.streams_search_single_source_hint
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
