package com.nuvio.app.core.licence

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val context = LocalContext.current
    var status by remember { mutableStateOf(manager.status()) }
    var showKeyEntry by remember { mutableStateOf(false) }
    var renewals by remember { mutableStateOf(0) }
    // Liste de blocage : relue à chaque retour sur l'appli, puis toutes les minutes tant qu'elle est
    // à l'écran. Un blocage ou un déblocage arrive donc en une minute environ. Rien en arrière-plan.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(manager, resumed) {
        if (!resumed) return@LaunchedEffect
        manager.refreshTime()
        while (true) {
            manager.refreshBlocklist()
            status = manager.status()
            delay(BLOCKLIST_CHECK_MS)
        }
    }

    // Les réglages affichent les jours restants et ouvrent la fenêtre de nouvelle clé.
    val current = status
    LaunchedEffect(current, renewals) {
        LicenceInfo.update(
            (current as? LicenceStatus.Valid)?.let {
                SubscriptionInfo(
                    clientName = it.clientName,
                    daysLeft = it.daysLeft + 1,
                    endDateText = dayToText(it.expiryDay),
                    nextStartText = manager.nextStart()?.let(::dayToText),
                )
            },
        )
    }
    DisposableEffect(Unit) {
        LicenceInfo.openKeyEntry = { showKeyEntry = true }
        onDispose { LicenceInfo.openKeyEntry = null }
    }

    // Rappel à l'ouverture quand il reste 3 jours ou moins.
    LaunchedEffect(Unit) {
        val valid = status as? LicenceStatus.Valid ?: return@LaunchedEffect
        val left = valid.daysLeft + 1
        if (left <= 3 && manager.nextStart() == null) {
            val text = if (left == 1) "Nuvio Pro : dernier jour d'accès" else "Nuvio Pro : plus que $left jours d'accès"
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    if (status is LicenceStatus.Valid) {
        content()
        if (showKeyEntry) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                KeyEntryDialog(
                    deviceCode = manager.deviceCode,
                    onDismiss = { showKeyEntry = false },
                    onActivate = { key ->
                        val result = manager.activate(key)
                        when (result) {
                            is LicenceStatus.Valid -> {
                                status = result
                                showKeyEntry = false
                                null
                            }
                            is LicenceStatus.NotYetValid -> {
                                status = manager.status()
                                renewals++
                                showKeyEntry = false
                                Toast.makeText(context, "Nouvelle clé enregistrée : elle commence le ${dayToText(result.startDay)}", Toast.LENGTH_LONG).show()
                                null
                            }
                            else -> messageFor(result)
                        }
                    },
                )
            }
        }
    } else {
        LicenceScreen(
            deviceCode = manager.deviceCode,
            message = messageFor(status),
            onActivate = { key ->
                val result = manager.activate(key)
                status = result
                if (result is LicenceStatus.Valid && result.clientName != null) {
                    Toast.makeText(context, "Bienvenue ${result.clientName}", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

/** Entrer une nouvelle clé sans quitter l'appli (renouvellement). Renvoie un message d'erreur ou null. */
@Composable
private fun KeyEntryDialog(
    deviceCode: String,
    onDismiss: () -> Unit,
    onActivate: (String) -> String?,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle clé d'accès") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    label = { Text("Clé d'accès") },
                    isError = error != null,
                    supportingText = if (error != null) { { Text(error.orEmpty()) } } else null,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Code du téléphone : $deviceCode",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onActivate(input) }, enabled = input.isNotBlank()) { Text("Activer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

/** Message sous le champ, seulement quand quelque chose ne va pas. */
private fun messageFor(status: LicenceStatus): String? = when (status) {
    LicenceStatus.Missing, is LicenceStatus.Valid -> null
    is LicenceStatus.NotYetValid -> "Clé valable à partir du ${dayToText(status.startDay)}."
    is LicenceStatus.Expired -> "Accès terminé le ${dayToText(status.expiryDay)}."
    LicenceStatus.Invalid -> "Clé non valable."
    LicenceStatus.WrongDevice -> "Cette clé est pour un autre téléphone."
    LicenceStatus.ClockChanged -> "Date du téléphone incorrecte. Active la date et l'heure automatiques."
    LicenceStatus.Blocked -> "Accès suspendu."
    LicenceStatus.NotConfigured -> "Nuvio Pro n'est pas encore configuré."
}

private const val BLOCKLIST_CHECK_MS = 60_000L

private fun dayToText(day: Int): String {
    val format = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(day * LicenceFormat.DAY_MS))
}
