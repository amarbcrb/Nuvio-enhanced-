package com.nuvio.app.features.streams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.GlassCheckRow
import com.nuvio.app.core.ui.GlassChip
import com.nuvio.app.core.ui.GlassSheetSectionLabel
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_stream_dub_any
import nuvio.composeapp.generated.resources.settings_stream_dub_only
import nuvio.composeapp.generated.resources.settings_stream_dub_prefer
import nuvio.composeapp.generated.resources.settings_stream_dub_subtitled_first
import nuvio.composeapp.generated.resources.settings_stream_dub_title
import nuvio.composeapp.generated.resources.stream_profile_saved
import nuvio.composeapp.generated.resources.stream_search_favorites_first
import nuvio.composeapp.generated.resources.stream_search_favorites_of
import nuvio.composeapp.generated.resources.stream_search_languages_all
import nuvio.composeapp.generated.resources.stream_search_languages_of
import nuvio.composeapp.generated.resources.stream_search_once_all_languages
import nuvio.composeapp.generated.resources.stream_search_profile_label
import nuvio.composeapp.generated.resources.stream_search_title
import nuvio.composeapp.generated.resources.stream_search_no_favorites
import nuvio.composeapp.generated.resources.streams_favorites_only_search
import nuvio.composeapp.generated.resources.streams_search_scope_title
import nuvio.composeapp.generated.resources.streams_search_scope_unavailable
import nuvio.composeapp.generated.resources.streams_search_single_source
import nuvio.composeapp.generated.resources.streams_search_single_source_hint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

internal val StreamDubMode.labelRes: StringResource
    get() = when (this) {
        StreamDubMode.Any -> Res.string.settings_stream_dub_any
        StreamDubMode.PreferDubbed -> Res.string.settings_stream_dub_prefer
        StreamDubMode.DubbedOnly -> Res.string.settings_stream_dub_only
        StreamDubMode.SubtitledFirst -> Res.string.settings_stream_dub_subtitled_first
    }

/** What the search of this title depends on, to know whether the links must be searched again. */
private fun searchSignature(titleKey: String?): String = listOf(
    StreamProfileRepository.profileFor(titleKey).id,
    StreamLanguagePreferencesRepository.requestSignature(),
    StreamSearchScopeRepository.favoritesSignature(),
    StreamSearchScopeRepository.scope.value?.id.orEmpty(),
).joinToString("|")

/**
 * Everything that decides the link search of the title on screen, in one sheet: its watching profile
 * (remembered for the title), that profile's link languages, where to search, that profile's favorite
 * sources, and a one-time "every language". Changes apply at once; [onDismiss] says whether the links
 * must be searched again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StreamSearchSheet(
    sourceOptions: List<StreamSourceOption>,
    groups: List<AddonStreamGroup>,
    searchOptions: List<StreamSourceOption>,
    appliedScopeId: String?,
    onDismiss: (changed: Boolean) -> Unit,
) {
    val titleKey = StreamProfileRepository.currentTitleKey
    val initialSignature = remember { searchSignature(titleKey) }
    val titleProfiles by StreamProfileRepository.titleProfiles.collectAsStateWithLifecycle()
    val profile = remember(titleProfiles, titleKey) { StreamProfileRepository.profileFor(titleKey) }
    val preferences by remember {
        StreamLanguagePreferencesRepository.ensureLoaded()
        StreamLanguagePreferencesRepository.preferences
    }.collectAsStateWithLifecycle()
    val filterPaused by StreamLanguagePreferencesRepository.linkFilterPaused.collectAsStateWithLifecycle()
    val pinnedSourceIds by rememberPinnedStreamSourceIds()
    val favoritesOnly by StreamSearchScopeRepository.favoritesOnly.collectAsStateWithLifecycle()
    val scope by StreamSearchScopeRepository.scope.collectAsStateWithLifecycle()
    var showSingleSources by remember { mutableStateOf(false) }
    val profileName = stringResource(profile.label)

    GlassStreamSheet(
        title = stringResource(Res.string.stream_search_title),
        subtitle = if (scope != null && appliedScopeId == null) {
            stringResource(Res.string.streams_search_scope_unavailable, scope?.name.orEmpty())
        } else {
            null
        },
        onDismiss = {
            if (favoritesOnly && PinnedStreamSourcesRepository.pinnedSourceIds.value.isEmpty()) {
                StreamSearchScopeRepository.setFavoritesOnly(false)
            }
            onDismiss(searchSignature(titleKey) != initialSignature)
        },
    ) {
        // 1. Profile of the title, remembered.
        GlassSheetSectionLabel(stringResource(Res.string.stream_search_profile_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StreamProfile.entries.forEach { option ->
                val optionName = stringResource(option.label)
                GlassChip(
                    label = optionName,
                    selected = option == profile,
                    onClick = {
                        if (option != profile && titleKey != null) {
                            StreamProfileRepository.setForTitle(titleKey, option)
                            MainScope().launch {
                                NuvioToastController.show(getString(Res.string.stream_profile_saved, optionName))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 2. Link languages of that profile.
        GlassSheetSectionLabel(stringResource(Res.string.stream_search_languages_of, profileName))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            GlassChip(
                label = stringResource(Res.string.stream_search_languages_all),
                selected = preferences.linkLanguages.isEmpty(),
                onClick = { StreamLanguagePreferencesRepository.update { it.copy(linkLanguages = emptySet()) } },
            )
            // Only the languages of the two profiles and English.
            SearchSheetLanguages.forEach { code ->
                GlassChip(
                    label = code.uppercase(),
                    selected = code in preferences.linkLanguages,
                    onClick = { StreamLanguagePreferencesRepository.toggleLinkLanguage(code) },
                )
            }
        }
        Text(
            text = stringResource(Res.string.settings_stream_dub_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.nuvio.colors.textMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StreamDubMode.entries.forEach { mode ->
                GlassChip(
                    label = stringResource(mode.labelRes),
                    selected = preferences.dubMode == mode,
                    onClick = { StreamLanguagePreferencesRepository.update { it.copy(dubMode = mode) } },
                )
            }
        }

        // 3. Where to search.
        GlassSheetSectionLabel(stringResource(Res.string.streams_search_scope_title))
        GlassCheckRow(
            title = stringResource(Res.string.stream_search_favorites_first),
            checked = scope == null && !favoritesOnly,
            icon = Icons.Rounded.Public,
            onCheckedChange = {
                StreamSearchScopeRepository.setScope(null)
                StreamSearchScopeRepository.setFavoritesOnly(false)
                showSingleSources = false
            },
        )
        GlassCheckRow(
            title = stringResource(Res.string.streams_favorites_only_search),
            checked = scope == null && favoritesOnly,
            icon = Icons.Rounded.Star,
            onCheckedChange = {
                StreamSearchScopeRepository.setScope(null)
                StreamSearchScopeRepository.setFavoritesOnly(true)
                showSingleSources = false
            },
        )
        if (searchOptions.isNotEmpty() || scope != null) {
            GlassCheckRow(
                title = stringResource(Res.string.streams_search_single_source),
                subtitle = scope?.name ?: stringResource(Res.string.streams_search_single_source_hint),
                checked = scope != null,
                icon = Icons.Rounded.FilterAlt,
                onCheckedChange = { showSingleSources = !showSingleSources },
            )
            if (showSingleSources) {
                searchOptions.forEach { option ->
                    GlassCheckRow(
                        title = option.name,
                        checked = scope?.id == option.id,
                        modifier = Modifier.padding(start = 24.dp),
                        onCheckedChange = {
                            StreamSearchScopeRepository.setFavoritesOnly(false)
                            StreamSearchScopeRepository.setScope(option)
                            showSingleSources = false
                        },
                    )
                }
            }
        }

        // 4. Favorite sources of that profile; new favorites are added with the star in "Afficher".
        GlassSheetSectionLabel(stringResource(Res.string.stream_search_favorites_of, profileName))
        val sources = favoriteSheetSources(sourceOptions, groups, pinnedSourceIds)
            .filter { it.key in pinnedSourceIds }
        if (sources.isEmpty()) {
            Text(
                text = stringResource(Res.string.stream_search_no_favorites),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
        }
        sources.forEach { source ->
            GlassCheckRow(
                title = source.label,
                checked = source.key in pinnedSourceIds,
                onCheckedChange = { checked -> PinnedStreamSourcesRepository.setPinned(source.key, checked) },
            )
        }

        // 5. One-time help: every language, without changing the profile.
        GlassCheckRow(
            title = stringResource(Res.string.stream_search_once_all_languages),
            checked = filterPaused,
            icon = Icons.Rounded.Language,
            onCheckedChange = { paused -> StreamLanguagePreferencesRepository.setLinkFilterPaused(paused) },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Link languages offered in the search sheet: French, Arabic and English. */
private val SearchSheetLanguages = listOf("fr", "ar", "en")
