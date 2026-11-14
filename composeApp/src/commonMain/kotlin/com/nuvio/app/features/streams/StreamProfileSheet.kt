package com.nuvio.app.features.streams

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.core.ui.GlassCheckRow
import com.nuvio.app.core.ui.NuvioToastController
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.stream_profile_arabic
import nuvio.composeapp.generated.resources.stream_profile_arabic_hint
import nuvio.composeapp.generated.resources.stream_profile_arabic_short
import nuvio.composeapp.generated.resources.stream_profile_french
import nuvio.composeapp.generated.resources.stream_profile_french_hint
import nuvio.composeapp.generated.resources.stream_profile_french_short
import nuvio.composeapp.generated.resources.stream_profile_note
import nuvio.composeapp.generated.resources.stream_profile_saved
import nuvio.composeapp.generated.resources.stream_profile_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

internal val StreamProfile.label: StringResource
    get() = when (this) {
        StreamProfile.FRENCH_DUB -> Res.string.stream_profile_french
        StreamProfile.ARABIC_SUB -> Res.string.stream_profile_arabic
    }

internal val StreamProfile.shortLabel: StringResource
    get() = when (this) {
        StreamProfile.FRENCH_DUB -> Res.string.stream_profile_french_short
        StreamProfile.ARABIC_SUB -> Res.string.stream_profile_arabic_short
    }

private val StreamProfile.hint: StringResource
    get() = when (this) {
        StreamProfile.FRENCH_DUB -> Res.string.stream_profile_french_hint
        StreamProfile.ARABIC_SUB -> Res.string.stream_profile_arabic_hint
    }

/**
 * Picks the watching profile of the title on screen. The choice is remembered for the title (every
 * episode of a series); [onDismiss] says whether it changed, so the links reload with it.
 */
@Composable
internal fun StreamProfileSheet(onDismiss: (changed: Boolean) -> Unit) {
    val titleKey = StreamProfileRepository.currentTitleKey
    val initial = remember(titleKey) { StreamProfileRepository.profileFor(titleKey) }
    var selected by remember(titleKey) { mutableStateOf(initial) }
    GlassStreamSheet(
        title = stringResource(Res.string.stream_profile_title),
        subtitle = stringResource(Res.string.stream_profile_note),
        onDismiss = {
            val changed = selected != initial && titleKey != null
            if (changed && titleKey != null) {
                StreamProfileRepository.setForTitle(titleKey, selected)
                MainScope().launch {
                    NuvioToastController.show(getString(Res.string.stream_profile_saved, getString(selected.label)))
                }
            }
            onDismiss(changed)
        },
    ) { close ->
        StreamProfile.entries.forEach { profile ->
            GlassCheckRow(
                title = stringResource(profile.label),
                subtitle = stringResource(profile.hint),
                checked = selected == profile,
                icon = if (profile == StreamProfile.ARABIC_SUB) Icons.Rounded.Subtitles else Icons.Rounded.RecordVoiceOver,
                onCheckedChange = {
                    selected = profile
                    close()
                },
            )
        }
    }
}
