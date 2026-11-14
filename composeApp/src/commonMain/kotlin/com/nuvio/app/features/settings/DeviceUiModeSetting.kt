package com.nuvio.app.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DeviceUiMode
import com.nuvio.app.core.ui.DeviceUiModeRepository
import com.nuvio.app.core.ui.GlassCheckRow
import com.nuvio.app.features.streams.GlassStreamSheet
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_device_ui_mode
import nuvio.composeapp.generated.resources.settings_device_ui_mode_auto
import nuvio.composeapp.generated.resources.settings_device_ui_mode_auto_hint
import nuvio.composeapp.generated.resources.settings_device_ui_mode_phone
import nuvio.composeapp.generated.resources.settings_device_ui_mode_phone_hint
import nuvio.composeapp.generated.resources.settings_device_ui_mode_section
import nuvio.composeapp.generated.resources.settings_device_ui_mode_tv
import nuvio.composeapp.generated.resources.settings_device_ui_mode_tv_hint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val DeviceUiMode.labelRes: StringResource
    get() = when (this) {
        DeviceUiMode.PHONE -> Res.string.settings_device_ui_mode_phone
        DeviceUiMode.TV -> Res.string.settings_device_ui_mode_tv
        DeviceUiMode.AUTO -> Res.string.settings_device_ui_mode_auto
    }

private val DeviceUiMode.hintRes: StringResource
    get() = when (this) {
        DeviceUiMode.PHONE -> Res.string.settings_device_ui_mode_phone_hint
        DeviceUiMode.TV -> Res.string.settings_device_ui_mode_tv_hint
        DeviceUiMode.AUTO -> Res.string.settings_device_ui_mode_auto_hint
    }

/** Settings > Appearance: phone (touch) or TV (remote) interface, or picked from the device. */
@Composable
internal fun DeviceUiModeSetting(isTablet: Boolean) {
    val mode by DeviceUiModeRepository.mode.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    SettingsSection(
        title = stringResource(Res.string.settings_device_ui_mode_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_device_ui_mode),
                description = stringResource(mode.labelRes),
                isTablet = isTablet,
                onClick = { showSheet = true },
            )
        }
    }
    if (showSheet) {
        GlassStreamSheet(
            title = stringResource(Res.string.settings_device_ui_mode),
            onDismiss = { showSheet = false },
        ) { close ->
            DeviceUiMode.entries.forEach { option ->
                GlassCheckRow(
                    title = stringResource(option.labelRes),
                    subtitle = stringResource(option.hintRes),
                    checked = option == mode,
                    icon = when (option) {
                        DeviceUiMode.PHONE -> Icons.Rounded.PhoneAndroid
                        DeviceUiMode.TV -> Icons.Rounded.Tv
                        DeviceUiMode.AUTO -> Icons.Rounded.AutoMode
                    },
                    onCheckedChange = {
                        DeviceUiModeRepository.setMode(option)
                        close()
                    },
                )
            }
        }
    }
}
