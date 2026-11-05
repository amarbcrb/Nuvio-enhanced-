package com.nuvio.app.core.licence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Abonnement de Nuvio Pro, affiché dans les réglages. */
data class SubscriptionInfo(
    /** Jours d'accès restants, aujourd'hui compris (1 = dernier jour). */
    val daysLeft: Int,
    /** Dernier jour d'accès, déjà formaté (par exemple « 4 décembre 2026 »). */
    val endDateText: String,
    /** Début de la clé de renouvellement déjà entrée, s'il y en a une. */
    val nextStartText: String?,
)

/**
 * Pont entre l'écran de clé (Android) et les réglages (code commun).
 * Reste vide dans Nuvio Plus.
 */
object LicenceInfo {
    private val subscription = MutableStateFlow<SubscriptionInfo?>(null)
    val state: StateFlow<SubscriptionInfo?> = subscription.asStateFlow()

    /** Ouvre la fenêtre pour entrer une nouvelle clé. Fourni par Nuvio Pro. */
    var openKeyEntry: (() -> Unit)? = null

    fun update(info: SubscriptionInfo?) {
        subscription.value = info
    }
}
