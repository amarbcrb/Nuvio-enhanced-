package com.nuvio.app.core.licence

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Couleurs iOS (mode sombre) et verre.
private val Ink = Color(0xFF000000)
private val Blue = Color(0xFF0A84FF)
private val Teal = Color(0xFF40C8E0)
private val Red = Color(0xFFFF453A)
private val Green = Color(0xFF30D158)
private val Muted = Color(0xFF98989F)
private val GlassTop = Color.White.copy(alpha = 0.11f)
private val GlassBottom = Color.White.copy(alpha = 0.04f)
private val EdgeTop = Color.White.copy(alpha = 0.24f)
private val EdgeBottom = Color.White.copy(alpha = 0.05f)

/**
 * Écran de Nuvio Pro tant qu'il n'y a pas de clé valable : code du téléphone avec un bouton
 * pour le copier, champ pour coller la clé, bouton Activer. Toujours en sombre, style iOS.
 */
@Composable
internal fun LicenceScreen(
    deviceCode: String,
    message: String?,
    onActivate: (String) -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }

    // Deux lueurs qui dérivent doucement derrière le verre.
    val drift = rememberInfiniteTransition(label = "glow")
    val shift by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7_000), RepeatMode.Reverse),
        label = "shift",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Box(
            modifier = Modifier
                .size(460.dp)
                .graphicsLayer {
                    translationX = -160f + shift * 90f
                    translationY = -220f + shift * 60f
                }
                .background(Brush.radialGradient(listOf(Blue.copy(alpha = 0.45f), Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(420.dp)
                .graphicsLayer {
                    translationX = 180f - shift * 80f
                    translationY = 160f - shift * 70f
                }
                .background(Brush.radialGradient(listOf(Teal.copy(alpha = 0.28f), Color.Transparent))),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Blue, Teal))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Nuvio Pro", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                Text("Entre ta clé d'accès pour continuer.", color = Muted, fontSize = 17.sp)
            }

            GlassCard {
                Label("Code du téléphone")
                Text(
                    deviceCode,
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp,
                )
                Text("Envoie ce code au vendeur pour recevoir ta clé.", color = Muted, fontSize = 15.sp)
                PillButton(
                    text = if (copied) "Code copié" else "Copier le code",
                    icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    primary = false,
                    tint = if (copied) Green else Color.White,
                    onClick = {
                        copyToClipboard(context, deviceCode)
                        copied = true
                    },
                )
            }

            GlassCard {
                Label("Clé d'accès")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Colle ta clé ici", color = Muted) },
                    minLines = 3,
                    isError = message != null,
                    textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 15.sp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue,
                        unfocusedBorderColor = EdgeTop,
                        errorBorderColor = Red,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        errorTextColor = Color.White,
                        cursorColor = Blue,
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        errorContainerColor = Color.White.copy(alpha = 0.05f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
                    Text(message.orEmpty(), color = Red, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                PillButton(
                    text = "Activer",
                    icon = null,
                    primary = true,
                    enabled = input.isNotBlank(),
                    onClick = { onActivate(input) },
                )
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GlassTop, GlassBottom)))
            .border(1.dp, Brush.verticalGradient(listOf(EdgeTop, EdgeBottom)), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        color = Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun PillButton(
    text: String,
    icon: ImageVector?,
    primary: Boolean,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val fill = if (primary) {
        Brush.horizontalGradient(listOf(Blue, Color(0xFF3AA0FF)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.08f)))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = if (primary) 0.28f else 0.20f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(text, color = tint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Code du téléphone", text))
    Toast.makeText(context, "Code copié : $text", Toast.LENGTH_SHORT).show()
}
