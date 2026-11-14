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
