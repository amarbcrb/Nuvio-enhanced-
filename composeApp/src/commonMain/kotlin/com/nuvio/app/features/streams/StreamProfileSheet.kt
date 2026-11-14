package com.nuvio.app.features.streams

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.stream_profile_arabic
import nuvio.composeapp.generated.resources.stream_profile_arabic_hint
import nuvio.composeapp.generated.resources.stream_profile_arabic_short
import nuvio.composeapp.generated.resources.stream_profile_french
import nuvio.composeapp.generated.resources.stream_profile_all_hint
import nuvio.composeapp.generated.resources.stream_profile_all_short
import nuvio.composeapp.generated.resources.stream_profile_all
import nuvio.composeapp.generated.resources.stream_profile_english_hint
import nuvio.composeapp.generated.resources.stream_profile_english_short
import nuvio.composeapp.generated.resources.stream_profile_english
import nuvio.composeapp.generated.resources.stream_profile_french_hint
import nuvio.composeapp.generated.resources.stream_profile_french_short
import org.jetbrains.compose.resources.StringResource

internal val StreamProfile.label: StringResource
    get() = when (this) {
        StreamProfile.FRENCH_DUB -> Res.string.stream_profile_french
        StreamProfile.ARABIC_SUB -> Res.string.stream_profile_arabic
        StreamProfile.ENGLISH -> Res.string.stream_profile_english
        StreamProfile.ALL -> Res.string.stream_profile_all
    }

internal val StreamProfile.shortLabel: StringResource
    get() = when (this) {
        StreamProfile.FRENCH_DUB -> Res.string.stream_profile_french_short
        StreamProfile.ARABIC_SUB -> Res.string.stream_profile_arabic_short
        StreamProfile.ENGLISH -> Res.string.stream_profile_english_short
        StreamProfile.ALL -> Res.string.stream_profile_all_short
    }

internal val StreamProfile.hint: StringResource
    get() = when (this) {
        StreamProfile.FRENCH_DUB -> Res.string.stream_profile_french_hint
        StreamProfile.ARABIC_SUB -> Res.string.stream_profile_arabic_hint
        StreamProfile.ENGLISH -> Res.string.stream_profile_english_hint
        StreamProfile.ALL -> Res.string.stream_profile_all_hint
    }
