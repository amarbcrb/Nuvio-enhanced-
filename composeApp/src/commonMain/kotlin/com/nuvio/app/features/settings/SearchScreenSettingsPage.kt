package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.cloudstream.CloudStreamRepository
import com.nuvio.app.features.search.SearchHistoryRepository
import com.nuvio.app.features.search.SearchScreenSettingsRepository
import com.nuvio.app.features.search.SearchSourceOption
import com.nuvio.app.features.search.searchSourceOptions
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_content_discovery_recent_searches
import nuvio.composeapp.generated.resources.settings_content_discovery_recent_searches_description
import nuvio.composeapp.generated.resources.settings_search_screen_discover
import nuvio.composeapp.generated.resources.settings_search_screen_discover_description
import nuvio.composeapp.generated.resources.settings_search_screen_move_down
import nuvio.composeapp.generated.resources.settings_search_screen_move_up
import nuvio.composeapp.generated.resources.settings_search_screen_no_sources
import nuvio.composeapp.generated.resources.settings_search_screen_section_display
import nuvio.composeapp.generated.resources.settings_search_screen_section_sources
import nuvio.composeapp.generated.resources.settings_search_screen_sources_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Page « Écran Recherche » : comme pour l'accueil, on choisit ce que la recherche affiche
 * (bloc Découvrir, recherches récentes) et quelles sources donnent des résultats, dans quel ordre.
 */
internal fun LazyListScope.searchScreenSettingsContent(isTablet: Boolean) {
    item(key = "search-screen-display") {
        val settings by remember {
            SearchScreenSettingsRepository.ensureLoaded()
            SearchScreenSettingsRepository.state
        }.collectAsStateWithLifecycle()
        val recentSearchesEnabled by remember {
            SearchHistoryRepository.ensureLoaded()
            SearchHistoryRepository.enabled
        }.collectAsStateWithLifecycle()
        SettingsSection(
            title = stringResource(Res.string.settings_search_screen_section_display),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_search_screen_discover),
                    description = stringResource(Res.string.settings_search_screen_discover_description),
                    checked = settings.showDiscover,
                    isTablet = isTablet,
                    onCheckedChange = SearchScreenSettingsRepository::setShowDiscover,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_content_discovery_recent_searches),
                    description = stringResource(Res.string.settings_content_discovery_recent_searches_description),
                    checked = recentSearchesEnabled,
                    isTablet = isTablet,
                    onCheckedChange = SearchHistoryRepository::setEnabled,
                )
            }
        }
    }
    item(key = "search-screen-sources") {
        val settings by remember {
            SearchScreenSettingsRepository.ensureLoaded()
            SearchScreenSettingsRepository.state
        }.collectAsStateWithLifecycle()
        val addonsUiState by remember {
            AddonRepository.initialize()
            AddonRepository.uiState
        }.collectAsStateWithLifecycle()
        val cloudStreamUiState by remember {
            CloudStreamRepository.initialize()
            CloudStreamRepository.uiState
        }.collectAsStateWithLifecycle()
        val sources = remember(addonsUiState.addons, cloudStreamUiState.plugins, settings.sourceOrder) {
            searchSourceOptions(addonsUiState.addons, cloudStreamUiState.plugins, settings)
        }
        val keys = remember(sources) { sources.map(SearchSourceOption::key) }
        SettingsSection(
            title = stringResource(Res.string.settings_search_screen_section_sources),
            isTablet = isTablet,
        ) {
            Text(
                text = stringResource(
                    if (sources.isEmpty()) Res.string.settings_search_screen_no_sources else Res.string.settings_search_screen_sources_hint,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            if (sources.isNotEmpty()) {
                SettingsGroup(isTablet = isTablet) {
                    sources.forEachIndexed { index, source ->
                        if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                        SearchSourceRow(
                            source = source,
                            visible = source.key !in settings.hiddenSources,
                            canMoveUp = index > 0,
                            canMoveDown = index < sources.lastIndex,
                            isTablet = isTablet,
                            onVisibleChange = { SearchScreenSettingsRepository.setSourceVisible(source.key, it) },
                            onMove = { offset -> SearchScreenSettingsRepository.moveSource(keys, source.key, offset) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSourceRow(
    source: SearchSourceOption,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isTablet: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isTablet) 20.dp else 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (visible) tokens.colors.textPrimary else tokens.colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(Res.string.settings_search_screen_move_up),
                tint = if (canMoveUp) tokens.colors.textPrimary else tokens.colors.borderDefault,
            )
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(Res.string.settings_search_screen_move_down),
                tint = if (canMoveDown) tokens.colors.textPrimary else tokens.colors.borderDefault,
            )
        }
        Switch(
            checked = visible,
            onCheckedChange = onVisibleChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tokens.colors.onAccent,
                checkedTrackColor = tokens.colors.accent,
                uncheckedThumbColor = tokens.colors.textMuted,
                uncheckedTrackColor = tokens.colors.borderDefault,
            ),
        )
    }
}
