package com.nuvio.app.core.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How the interface is laid out: for a touch screen, for a TV remote, or picked from the device. */
enum class DeviceUiMode(val storageId: String) {
    PHONE("phone"),
    TV("tv"),
    AUTO("auto"),
    ;

    companion object {
        fun fromStorageId(id: String?): DeviceUiMode = entries.firstOrNull { it.storageId == id } ?: PHONE
    }
}

/** Where the chosen [DeviceUiMode] is kept; the platform installs a persistent one at startup. */
interface DeviceUiModeStorage {
    fun load(): String?
    fun save(value: String)
}

/**
 * The interface mode chosen in Settings > Appearance. Phone is the default: TV mode is only used
 * when the user picks it, or picks Automatic on a device that reports itself as a TV.
 */
object DeviceUiModeRepository {
    private val _mode = MutableStateFlow(DeviceUiMode.PHONE)
    val mode: StateFlow<DeviceUiMode> = _mode.asStateFlow()

    private var storage: DeviceUiModeStorage? = null

    /** Whether the device itself is a TV, as reported by the platform. */
    var platformIsTelevision: () -> Boolean = { false }

    fun initialize(storage: DeviceUiModeStorage, isTelevision: () -> Boolean) {
        this.storage = storage
        platformIsTelevision = isTelevision
        _mode.value = DeviceUiMode.fromStorageId(runCatching { storage.load() }.getOrNull())
    }

    fun setMode(mode: DeviceUiMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        runCatching { storage?.save(mode.storageId) }
    }

    fun isTvMode(mode: DeviceUiMode = _mode.value): Boolean = when (mode) {
        DeviceUiMode.PHONE -> false
        DeviceUiMode.TV -> true
        DeviceUiMode.AUTO -> runCatching { platformIsTelevision() }.getOrDefault(false)
    }
}

/** True when the interface is laid out for a TV remote. */
val LocalTvMode = compositionLocalOf { false }

@Composable
fun rememberIsTvMode(): Boolean {
    val mode by DeviceUiModeRepository.mode.collectAsStateWithLifecycle()
    return DeviceUiModeRepository.isTvMode(mode)
}

/**
 * Indication used in TV mode: a bright outline and a light veil on the focused element, so the
 * remote's position is always visible. Elements that clip themselves get the outline in their shape.
 */
object TvFocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TvFocusIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x7f0c05
}

private class TvFocusIndicationNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            var focusCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount = (focusCount - 1).coerceAtLeast(0)
                }
                val nowFocused = focusCount > 0
                if (nowFocused != focused) {
                    focused = nowFocused
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        val stroke = 3.dp.toPx()
        val radius = CornerRadius(12.dp.toPx())
        drawRoundRect(color = Color.White.copy(alpha = 0.16f), cornerRadius = radius)
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            cornerRadius = radius,
            style = Stroke(width = stroke),
        )
    }
}
