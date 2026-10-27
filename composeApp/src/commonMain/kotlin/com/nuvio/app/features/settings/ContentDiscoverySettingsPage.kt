package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioDropdownField
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.features.home.CatalogSourceRepository
import com.nuvio.app.features.search.SearchHistoryRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_page_addons
import nuvio.composeapp.generated.resources.compose_settings_page_media_servers
import nuvio.composeapp.generated.resources.compose_settings_page_plugins
import nuvio.composeapp.generated.resources.compose_settings_page_cloudstream
import nuvio.composeapp.generated.resources.settings_catalog_source_all
import nuvio.composeapp.generated.resources.settings_catalog_source_description
import nuvio.composeapp.generated.resources.settings_catalog_source_section
import nuvio.composeapp.generated.resources.settings_catalog_source_title
import nuvio.composeapp.generated.resources.settings_content_discovery_cloudstream_description
import nuvio.composeapp.generated.resources.settings_content_discovery_addons_description
import nuvio.composeapp.generated.resources.settings_content_discovery_addons_description_appstore
import nuvio.composeapp.generated.resources.settings_content_discovery_media_servers_description
import nuvio.composeapp.generated.resources.settings_content_discovery_plugins_description
import nuvio.composeapp.generated.resources.settings_content_discovery_section_sources
import nuvio.composeapp.generated.resources.settings_content_discovery_section_search
import nuvio.composeapp.generated.resources.settings_content_discovery_recent_searches
import nuvio.composeapp.generated.resources.settings_content_discovery_recent_searches_description
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.contentDiscoveryContent(
    isTablet: Boolean,
    showPluginsEntry: Boolean,
    showCloudStreamEntry: Boolean,
    onAddonsClick: () -> Unit,
    onPluginsClick: () -> Unit,
    onCloudStreamClick: () -> Unit,
    onMediaServersClick: () -> Unit,
) {
    item {
        val selectedSource by remember {
            CatalogSourceRepository.ensureLoaded()
            CatalogSourceRepository.selected
        }.collectAsStateWithLifecycle()
        val availableSources by CatalogSourceRepository.available.collectAsStateWithLifecycle()
        // Keep the saved choice listed even before the Home has reported its sources.
        val sources = (availableSources + listOfNotNull(selectedSource)).distinctBy { it.id }

        SettingsSection(
            title = stringResource(Res.string.settings_catalog_source_section),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                val allLabel = stringResource(Res.string.settings_catalog_source_all)
                SettingsDropdownRow(
                    title = stringResource(Res.string.settings_catalog_source_title),
                    description = stringResource(Res.string.settings_catalog_source_description),
                    isTablet = isTablet,
                ) {
                    NuvioDropdownField(
                        valueLabel = selectedSource?.name ?: allLabel,
                        options = listOf(NuvioDropdownOption(key = "", label = allLabel)) +
                            sources.map { NuvioDropdownOption(key = it.id, label = it.name) },
                        selectedKeys = setOf(selectedSource?.id.orEmpty()),
                        onSelect = { option ->
                            CatalogSourceRepository.select(sources.firstOrNull { it.id == option.key })
                        },
                    )
                }
            }
        }
    }

    item {
        val recentSearchesEnabled by remember {
            SearchHistoryRepository.ensureLoaded()
            SearchHistoryRepository.enabled
        }.collectAsStateWithLifecycle()

        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_search),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
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

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_sources),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_addons),
                    description = stringResource(
                        if (AppFeaturePolicy.personalMediaAddonCopyEnabled) {
                            Res.string.settings_content_discovery_addons_description_appstore
                        } else {
                            Res.string.settings_content_discovery_addons_description
                        },
                    ),
                    isTablet = isTablet,
                    onClick = onAddonsClick,
                )
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_media_servers),
                    description = stringResource(Res.string.settings_content_discovery_media_servers_description),
                    isTablet = isTablet,
                    onClick = onMediaServersClick,
                )
                if (showPluginsEntry) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_plugins),
                        description = stringResource(Res.string.settings_content_discovery_plugins_description),
                        isTablet = isTablet,
                        onClick = onPluginsClick,
                    )
                }
                if (showCloudStreamEntry) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_cloudstream),
                        description = stringResource(Res.string.settings_content_discovery_cloudstream_description),
                        isTablet = isTablet,
                        onClick = onCloudStreamClick,
                    )
                }
            }
        }
    }
}
