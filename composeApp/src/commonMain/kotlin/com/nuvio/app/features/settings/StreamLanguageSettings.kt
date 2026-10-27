package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.features.streams.StreamLanguageChoices
import com.nuvio.app.features.streams.StreamLanguagePreferencesRepository
import com.nuvio.app.features.streams.streamLanguageDisplayName
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_cloudstream_language_description
import nuvio.composeapp.generated.resources.settings_cloudstream_language_section
import nuvio.composeapp.generated.resources.settings_cloudstream_language_title
import nuvio.composeapp.generated.resources.settings_language_any
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
    SettingsChipRow(
        title = title,
        description = description,
        isTablet = isTablet,
    ) {
        Chip(
            label = stringResource(Res.string.settings_language_any),
            selected = selected.isEmpty(),
            onClick = onClearSelection,
        )
        languages.forEach { code ->
            Chip(
                label = streamLanguageDisplayName(code),
                selected = code in selected,
                onClick = { onToggle(code) },
            )
        }
    }
}
