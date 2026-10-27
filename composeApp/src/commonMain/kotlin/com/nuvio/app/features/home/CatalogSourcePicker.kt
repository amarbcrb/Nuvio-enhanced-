package com.nuvio.app.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioDropdownField
import com.nuvio.app.core.ui.NuvioSelectOption
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_catalog_source_all
import nuvio.composeapp.generated.resources.settings_catalog_source_title
import org.jetbrains.compose.resources.stringResource

/** Chooses one or several catalog sources for the Home and Search tabs, or all of them. */
@Composable
fun CatalogSourcePicker(
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    val selected by remember {
        CatalogSourceRepository.ensureLoaded()
        CatalogSourceRepository.selected
    }.collectAsStateWithLifecycle()
    val available by CatalogSourceRepository.available.collectAsStateWithLifecycle()
    // Keep the saved choice listed even before the Home has reported its sources.
    val sources = remember(available, selected) { (available + selected).distinctBy { it.id } }
    val allLabel = stringResource(Res.string.settings_catalog_source_all)

    NuvioDropdownField(
        modifier = modifier,
        label = if (showLabel) stringResource(Res.string.settings_catalog_source_title) else null,
        valueLabel = if (selected.isEmpty()) allLabel else selected.joinToString(", ") { it.name },
        options = listOf(NuvioSelectOption(key = "", label = allLabel)) +
            sources.map { NuvioSelectOption(key = it.id, label = it.name) },
        selectedKeys = if (selected.isEmpty()) setOf("") else selected.map { it.id }.toSet(),
        multiSelect = true,
        onSelect = { option ->
            if (option.key.isEmpty()) {
                CatalogSourceRepository.showAll()
            } else {
                sources.firstOrNull { it.id == option.key }?.let(CatalogSourceRepository::toggle)
            }
        },
    )
}
