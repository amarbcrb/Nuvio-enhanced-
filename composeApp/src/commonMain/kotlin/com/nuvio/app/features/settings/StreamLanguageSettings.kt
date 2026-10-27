package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioDropdownField
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.streams.StreamDubMode
import com.nuvio.app.features.streams.StreamLanguageChoices
import com.nuvio.app.features.streams.StreamLanguagePreferencesRepository
import com.nuvio.app.features.streams.streamLanguageDisplayName
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_cloudstream_language_description
import nuvio.composeapp.generated.resources.settings_cloudstream_language_section
import nuvio.composeapp.generated.resources.settings_cloudstream_language_title
import nuvio.composeapp.generated.resources.settings_language_any
import nuvio.composeapp.generated.resources.settings_stream_dub_any
import nuvio.composeapp.generated.resources.settings_stream_dub_description
import nuvio.composeapp.generated.resources.settings_stream_dub_only
import nuvio.composeapp.generated.resources.settings_stream_dub_prefer
import nuvio.composeapp.generated.resources.settings_stream_dub_title
import nuvio.composeapp.generated.resources.settings_stream_language_description
import nuvio.composeapp.generated.resources.settings_stream_language_section
import nuvio.composeapp.generated.resources.settings_stream_language_subtitled_description
import nuvio.composeapp.generated.resources.settings_stream_language_subtitled_title
import nuvio.composeapp.generated.resources.settings_stream_language_title
import nuvio.composeapp.generated.resources.settings_stream_language_unknown_description
import nuvio.composeapp.generated.resources.settings_stream_language_unknown_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StreamLinkLanguageSettings(isTablet: Boolean) {
    val preferences by remember {
        StreamLanguagePreferencesRepository.ensureLoaded()
        StreamLanguagePreferencesRepository.preferences
    }.collectAsStateWithLifecycle()

    SettingsSection(
        title = stringResource(Res.string.settings_stream_language_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            LanguageChipRow(
                title = stringResource(Res.string.settings_stream_language_title),
                description = stringResource(Res.string.settings_stream_language_description),
                isTablet = isTablet,
                languages = StreamLanguageChoices.map { it.first },
                selected = preferences.linkLanguages,
                onClearSelection = {
                    StreamLanguagePreferencesRepository.update { it.copy(linkLanguages = emptySet()) }
                },
                onToggle = StreamLanguagePreferencesRepository::toggleLinkLanguage,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsChipRow(
                title = stringResource(Res.string.settings_stream_dub_title),
                description = stringResource(Res.string.settings_stream_dub_description),
                isTablet = isTablet,
            ) {
                StreamDubMode.entries.forEach { mode ->
                    Chip(
                        label = stringResource(
                            when (mode) {
                                StreamDubMode.Any -> Res.string.settings_stream_dub_any
                                StreamDubMode.PreferDubbed -> Res.string.settings_stream_dub_prefer
                                StreamDubMode.DubbedOnly -> Res.string.settings_stream_dub_only
                            },
                        ),
                        selected = preferences.dubMode == mode,
                        onClick = { StreamLanguagePreferencesRepository.update { it.copy(dubMode = mode) } },
                    )
                }
            }
            if (preferences.linkLanguages.isNotEmpty()) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_stream_language_subtitled_title),
                    description = stringResource(Res.string.settings_stream_language_subtitled_description),
                    checked = preferences.includeSubtitled,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        StreamLanguagePreferencesRepository.update { it.copy(includeSubtitled = enabled) }
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_stream_language_unknown_title),
                    description = stringResource(Res.string.settings_stream_language_unknown_description),
                    checked = preferences.hideUnknown,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        StreamLanguagePreferencesRepository.update { it.copy(hideUnknown = enabled) }
                    },
                )
            }
        }
    }
}

/** Language filter for CloudStream extensions; [availableLanguages] are the languages of the known extensions. */
@Composable
internal fun CloudStreamExtensionLanguageSettings(
    isTablet: Boolean,
    availableLanguages: List<String>,
) {
    val preferences by remember {
        StreamLanguagePreferencesRepository.ensureLoaded()
        StreamLanguagePreferencesRepository.preferences
    }.collectAsStateWithLifecycle()
    val languages = (availableLanguages + preferences.extensionLanguages).distinct()
    if (languages.isEmpty()) return

    SettingsSection(
        title = stringResource(Res.string.settings_cloudstream_language_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            LanguageChipRow(
                title = stringResource(Res.string.settings_cloudstream_language_title),
                description = stringResource(Res.string.settings_cloudstream_language_description),
                isTablet = isTablet,
                languages = languages,
                selected = preferences.extensionLanguages,
                onClearSelection = {
                    StreamLanguagePreferencesRepository.update { it.copy(extensionLanguages = emptySet()) }
                },
                onToggle = StreamLanguagePreferencesRepository::toggleExtensionLanguage,
            )
        }
    }
}

/** Multi-choice language list in a dropdown; "All" clears the choice. */
@Composable
private fun LanguageChipRow(
    title: String,
    description: String,
    isTablet: Boolean,
    languages: List<String>,
    selected: Set<String>,
    onClearSelection: () -> Unit,
    onToggle: (String) -> Unit,
) {
    val anyLabel = stringResource(Res.string.settings_language_any)
    val options = remember(languages, anyLabel) {
        listOf(NuvioDropdownOption(key = "", label = anyLabel)) +
            languages
                .map { code -> NuvioDropdownOption(key = code, label = streamLanguageDisplayName(code)) }
                .sortedBy { it.label.lowercase() }
    }
    SettingsDropdownRow(title = title, description = description, isTablet = isTablet) {
        NuvioDropdownField(
            valueLabel = if (selected.isEmpty()) {
                anyLabel
            } else {
                selected.map(::streamLanguageDisplayName).sorted().joinToString(", ")
            },
            options = options,
            selectedKeys = if (selected.isEmpty()) setOf("") else selected,
            multiSelect = true,
            onSelect = { option -> if (option.key.isEmpty()) onClearSelection() else onToggle(option.key) },
        )
    }
}

/** A settings row with a title, a description and a dropdown field below them. */
@Composable
internal fun SettingsDropdownRow(
    title: String,
    description: String,
    isTablet: Boolean,
    field: @Composable () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = if (isTablet) 16.dp else 14.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )
        }
        field()
    }
}
