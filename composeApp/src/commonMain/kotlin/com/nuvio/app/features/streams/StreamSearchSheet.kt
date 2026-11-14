package com.nuvio.app.features.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.GlassCheckRow
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
import nuvio.composeapp.generated.resources.stream_profile_saved
import nuvio.composeapp.generated.resources.stream_search_all_profile_info
import nuvio.composeapp.generated.resources.stream_search_default_profile
import nuvio.composeapp.generated.resources.stream_search_default_profile_label
import nuvio.composeapp.generated.resources.stream_search_favorites_first
import nuvio.composeapp.generated.resources.stream_search_favorites_of
import nuvio.composeapp.generated.resources.stream_search_no_favorites
import nuvio.composeapp.generated.resources.stream_search_profile_label
import nuvio.composeapp.generated.resources.stream_search_this_profile
import nuvio.composeapp.generated.resources.stream_search_title
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
 * Everything that decides the link search of the title on screen: its profile (French, Arabic,
 * English or All, remembered for the title), where to search and that profile's favorites, and the
 * profile of new titles. The link languages are set by the profile. Changes apply at once;
 * [onDismiss] says whether the links must be searched again.
 */
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
    val defaultProfile by StreamProfileRepository.defaultProfile.collectAsStateWithLifecycle()
    val profile = remember(titleProfiles, defaultProfile, titleKey) { StreamProfileRepository.profileFor(titleKey) }
    val pinnedSourceIds by rememberPinnedStreamSourceIds()
    val favoritesOnly by StreamSearchScopeRepository.favoritesOnly.collectAsStateWithLifecycle()
    val scope by StreamSearchScopeRepository.scope.collectAsStateWithLifecycle()
    var showSingleSources by remember { mutableStateOf(false) }
    val profileName = stringResource(profile.label)

    GlassStreamSheet(
        title = stringResource(Res.string.stream_search_title),
        subtitle = if (profile.hasPreferences && scope != null && appliedScopeId == null) {
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
        // 1. Profile of the title, remembered: a 2 x 2 grid.
        GlassSheetSectionLabel(stringResource(Res.string.stream_search_profile_label))
        StreamProfile.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    val optionName = stringResource(option.label)
                    ProfileTile(
                        title = optionName,
                        hint = stringResource(option.hint),
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
        }

        if (!profile.hasPreferences) {
            // 2. "All": nothing to set.
            GlassSheetSectionLabel(stringResource(Res.string.stream_search_this_profile))
            Text(
                text = stringResource(Res.string.stream_search_all_profile_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
        } else {
            // 2. Where to search.
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

            // 3. Favorite sources of that profile; new ones are added with the star in "Afficher".
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
        }

        // 4. Profile of the titles that have none of their own.
        GlassSheetSectionLabel(stringResource(Res.string.stream_search_default_profile_label))
        GlassCheckRow(
            title = stringResource(Res.string.stream_search_default_profile, profileName),
            checked = defaultProfile == profile,
            onCheckedChange = { StreamProfileRepository.setDefault(profile) },
        )
    }
}

@Composable
private fun ProfileTile(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) tokens.colors.accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (selected) tokens.colors.accent.copy(alpha = 0.7f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            maxLines = 2,
        )
    }
}
