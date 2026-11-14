package com.nuvio.app.core.licence

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.ui.text.style.TextAlign
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_icon_original
import org.jetbrains.compose.resources.painterResource
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
private val Red = Color(0xFFFF453A)
private val Green = Color(0xFF30D158)
private val Muted = Color(0xFF98989F)
private val GlassTop = Color.White.copy(alpha = 0.11f)
private val GlassBottom = Color.White.copy(alpha = 0.04f)
private val EdgeTop = Color.White.copy(alpha = 0.24f)
private val EdgeBottom = Color.White.copy(alpha = 0.05f)

private enum class LicenceStep { Welcome, RequestKey, EnterKey }

/**
 * Écran de Nuvio Pro tant qu'il n'y a pas de clé valable. Accueil sobre (logo, nom, deux boutons),
 * puis l'étape choisie : « Demander ma clé » montre le code du téléphone à envoyer au vendeur,
 * « J'ai une clé d'activation » ouvre le champ de la clé et le bouton Activer. Toujours en sombre.
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
    var step by remember { mutableStateOf(LicenceStep.Welcome) }
    // Une clé refusée ramène sur le champ de la clé, avec le message.
    LaunchedEffect(message) {
        if (message != null) step = LicenceStep.EnterKey
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    BackHandler(enabled = step != LicenceStep.Welcome) { step = LicenceStep.Welcome }

    // Une lueur bleue qui respire doucement derrière le logo.
    val drift = rememberInfiniteTransition(label = "glow")
    val glow by drift.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(4_000), RepeatMode.Reverse),
        label = "glow",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
                .size(420.dp)
                .background(Brush.radialGradient(listOf(Blue.copy(alpha = glow * 0.6f), Color.Transparent))),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(if (step == LicenceStep.Welcome) 120.dp else 48.dp))
            Image(
                painter = painterResource(Res.drawable.app_icon_original),
                contentDescription = "Nuvio",
                modifier = Modifier
                    .size(if (step == LicenceStep.Welcome) 104.dp else 76.dp)
                    .clip(RoundedCornerShape(26.dp)),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text("Nuvio Pro", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                when (step) {
                    LicenceStep.Welcome -> "Active ton accès pour commencer."
                    LicenceStep.RequestKey -> "Envoie ce code au vendeur pour recevoir ta clé."
                    LicenceStep.EnterKey -> "Colle la clé reçue du vendeur."
                },
                color = Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(if (step == LicenceStep.Welcome) 96.dp else 32.dp))

            when (step) {
                LicenceStep.Welcome -> {
                    PillButton(
                        text = "J'ai une clé d'activation",
                        icon = Icons.Rounded.Key,
                        primary = true,
                        onClick = { step = LicenceStep.EnterKey },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlineButton(
                        text = "Demander ma clé",
                        onClick = { step = LicenceStep.RequestKey },
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        "La clé est liée à ce téléphone. Demande-la au vendeur avec le code de ton téléphone.",
                        color = Muted.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                LicenceStep.RequestKey -> {
                    GlassCard {
                        Label("Code du téléphone")
                        Text(
                            deviceCode,
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
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
                    Spacer(modifier = Modifier.height(16.dp))
                    PillButton(
                        text = "J'ai reçu ma clé",
                        icon = Icons.Rounded.Key,
                        primary = true,
                        onClick = { step = LicenceStep.EnterKey },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BackLink { step = LicenceStep.Welcome }
                }

                LicenceStep.EnterKey -> {
                    GlassCard {
                        Label("Clé d'activation")
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
                    Spacer(modifier = Modifier.height(8.dp))
                    TextLink("Pas encore de clé ? Demander ma clé") { step = LicenceStep.RequestKey }
                    BackLink { step = LicenceStep.Welcome }
                }
            }
        }
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Blue,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun BackLink(onClick: () -> Unit) {
    Text(
        "Retour",
        color = Muted,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
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
