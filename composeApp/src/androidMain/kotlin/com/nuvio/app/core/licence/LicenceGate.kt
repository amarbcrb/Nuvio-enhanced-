package com.nuvio.app.core.licence

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Affiche content() seulement si la licence est valable.
 * Dans la version gratuite (licence_required = false), affiche content() directement.
 */
@Composable
fun LicenceGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val manager = remember { LicenceManager(context) }
    if (manager.config.required) {
        ProLicenceGate(manager, content)
    } else {
        content()
    }
}

@Composable
private fun ProLicenceGate(manager: LicenceManager, content: @Composable () -> Unit) {
    var status by remember { mutableStateOf(manager.status()) }
    LaunchedEffect(manager) {
        while (true) {
            manager.refreshOnline()
            status = manager.status()
            delay(30L * 60 * 1000)
        }
    }

    if (status is LicenceStatus.Valid) {
        content()
    } else {
        val dark = isSystemInDarkTheme()
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            LicenceScreen(
                deviceCode = manager.deviceCode,
                contact = manager.config.contact,
                status = status,
                onActivate = { key -> status = manager.activate(key) },
            )
        }
    }
}

@Composable
private fun LicenceScreen(
    deviceCode: String,
    contact: String,
    status: LicenceStatus,
    onActivate: (String) -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Abonnement Nuvio Pro", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(messageFor(status), textAlign = TextAlign.Center)

            Text("Ton code appareil", style = MaterialTheme.typography.labelLarge)
            Text(
                deviceCode,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
            )
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Code appareil", deviceCode))
                copied = true
            }) {
                Text(if (copied) "Code copié" else "Copier le code")
            }
            Text(
                if (contact.isBlank()) "Envoie ce code avec ton paiement au vendeur."
                else "Envoie ce code avec ton paiement. $contact",
                textAlign = TextAlign.Center,
            )

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Clé d'activation") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onActivate(input) },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Activer")
            }
        }
    }
}

private fun messageFor(status: LicenceStatus): String = when (status) {
    LicenceStatus.Missing -> "Pour utiliser Nuvio Pro, active-le avec une clé."
    is LicenceStatus.Expired -> "Ton abonnement a pris fin le ${dayToText(status.expiryDay)}. Renouvelle-le pour recevoir une nouvelle clé."
    LicenceStatus.Invalid -> "Cette clé n'est pas valable. Vérifie que tu l'as copiée en entier."
    LicenceStatus.WrongDevice -> "Cette clé a été créée pour un autre téléphone. Elle ne marche qu'avec le code appareil affiché ci-dessous."
    LicenceStatus.ClockChanged -> "La date du téléphone est incorrecte. Active la date et l'heure automatiques, puis relance l'application."
    LicenceStatus.Blocked -> "L'accès à l'application a été suspendu. Contacte le vendeur."
    LicenceStatus.NotConfigured -> "Cette version n'est pas encore configurée. Contacte le vendeur."
    is LicenceStatus.Valid -> ""
}

private fun dayToText(day: Int): String {
    val format = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(day * LicenceFormat.DAY_MS))
}
