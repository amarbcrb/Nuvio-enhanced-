package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PlayerGestureController {
    fun currentBrightness(): Float?
    fun setBrightness(level: Float): Float?
    fun currentVolume(): PlayerAudioLevel?
    fun setVolume(level: Float): PlayerAudioLevel?
}

data class PlayerAudioLevel(
    val fraction: Float,
    val isMuted: Boolean,
)

/** How the player is held: landscape, portrait, or following the phone's rotation. */
enum class PlayerOrientationMode(val storageId: String) {
    LANDSCAPE("landscape"),
    PORTRAIT("portrait"),
    AUTO("auto"),
}

/**
 * The player orientation picked with the rotate button. It is kept for the next videos (and across
 * launches once the platform installs [persist]/[load]).
 */
object PlayerOrientationPreference {
    private val _mode = MutableStateFlow(PlayerOrientationMode.LANDSCAPE)
    val mode: StateFlow<PlayerOrientationMode> = _mode.asStateFlow()

    private val _portrait = MutableStateFlow(false)
    val portrait: StateFlow<Boolean> = _portrait.asStateFlow()

    private var persist: ((String) -> Unit)? = null

    fun initialize(stored: String?, persist: (String) -> Unit) {
        this.persist = persist
        set(PlayerOrientationMode.entries.firstOrNull { it.storageId == stored } ?: PlayerOrientationMode.LANDSCAPE, save = false)
    }

    /** Landscape -> portrait -> automatic rotation -> landscape. */
    fun toggle() {
        val next = when (_mode.value) {
            PlayerOrientationMode.LANDSCAPE -> PlayerOrientationMode.PORTRAIT
            PlayerOrientationMode.PORTRAIT -> PlayerOrientationMode.AUTO
            PlayerOrientationMode.AUTO -> PlayerOrientationMode.LANDSCAPE
        }
        set(next, save = true)
    }

    private fun set(mode: PlayerOrientationMode, save: Boolean) {
        _mode.value = mode
        _portrait.value = mode == PlayerOrientationMode.PORTRAIT
        if (save) runCatching { persist?.invoke(mode.storageId) }
    }

    /** Called when the player closes: the choice is kept for the next video. */
    internal fun reset() = Unit
}

@Composable
expect fun LockPlayerToLandscape()

@Composable
expect fun rememberPlayerOrientationToggleSupported(): Boolean

@Composable
expect fun FullscreenPlayerDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
)

@Composable
expect fun HidePlayerSystemBars()

@Composable
expect fun EnterImmersivePlayerMode(keepScreenAwake: Boolean)

@Composable
expect fun ManagePlayerPictureInPicture(
    isPlaying: Boolean,
    videoSize: IntSize,
)

@Composable
expect fun rememberIsInPictureInPicture(): Boolean

@Composable
fun rememberPlayerGestureController(restoreBrightness: Boolean): PlayerGestureController? {
    val controller = rememberPlatformPlayerGestureController() ?: return null
    DisposableEffect(controller, restoreBrightness) {
        if (restoreBrightness) {
            PlayerSettingsStorage.loadPlaybackBrightness()
                ?.takeIf { it in 0f..1f }
                ?.let(controller::setBrightness)
        }
        onDispose {}
    }
    return remember(controller) {
        object : PlayerGestureController by controller {
            override fun setBrightness(level: Float): Float? {
                if (!level.isFinite()) return null
                return controller.setBrightness(level.coerceIn(0f, 1f))
                    ?.also(PlayerSettingsStorage::savePlaybackBrightness)
            }
        }
    }
}

@Composable
internal expect fun rememberPlatformPlayerGestureController(): PlayerGestureController?
