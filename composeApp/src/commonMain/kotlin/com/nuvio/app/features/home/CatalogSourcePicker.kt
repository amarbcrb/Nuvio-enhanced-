package com.nuvio.app.features.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.GlassCheckRow
import com.nuvio.app.core.ui.GlassChip
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.library_categories_done
import nuvio.composeapp.generated.resources.settings_catalog_source_all
import nuvio.composeapp.generated.resources.settings_catalog_source_title
import nuvio.composeapp.generated.resources.source_picker_no_match
import nuvio.composeapp.generated.resources.source_picker_search_hint
import nuvio.composeapp.generated.resources.source_picker_sort_az
import nuvio.composeapp.generated.resources.source_picker_sort_default
import nuvio.composeapp.generated.resources.source_picker_sort_recent
import org.jetbrains.compose.resources.stringResource

/** Chooses one or several catalog sources for the Home tab, or all of them. */
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

    SourcePickerField(
        modifier = modifier,
        label = if (showLabel) stringResource(Res.string.settings_catalog_source_title) else null,
        sheetTitle = stringResource(Res.string.settings_catalog_source_title),
        sources = sources,
        selectedIds = selected.map { it.id }.toSet(),
        onToggle = CatalogSourceRepository::toggle,
        onShowAll = CatalogSourceRepository::showAll,
    )
}

/** How the sources are listed in the picker. */
internal enum class SourcePickerSort { Default, Alphabetical, RecentlyAdded }

/**
 * A field showing the chosen sources; a tap opens a liquid glass sheet listing every source, with a
 * search by name and A-Z or last-added order. No source chosen means every source.
 */
@Composable
internal fun SourcePickerField(
    sheetTitle: String,
    sources: List<CatalogSourceOption>,
    selectedIds: Set<String>,
    onToggle: (CatalogSourceOption) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    compact: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    var open by remember { mutableStateOf(false) }
    val allLabel = stringResource(Res.string.settings_catalog_source_all)
    val chosen = sources.filter { it.id in selectedIds }
    val valueLabel = if (chosen.isEmpty()) allLabel else chosen.joinToString(", ") { it.name }
    // Liquid glass pill, like the categories and source sheets: translucent, light edge, accent when filtered.
    val shape = if (compact) RoundedCornerShape(50) else RoundedCornerShape(NuvioTokens.Radius.lg)
    Surface(
        modifier = modifier.then(if (compact) Modifier else Modifier.fillMaxWidth()).clickable { open = true },
        shape = shape,
        color = if (chosen.isEmpty()) Color.White.copy(alpha = 0.08f) else tokens.colors.accent.copy(alpha = 0.22f),
        border = BorderStroke(
            1.dp,
            if (chosen.isEmpty()) Color.White.copy(alpha = 0.14f) else tokens.colors.accent.copy(alpha = 0.7f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(modifier = if (compact) Modifier else Modifier.weight(1f)) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.colors.textMuted,
                    )
                }
                Text(
                    text = valueLabel,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = tokens.colors.textMuted,
            )
        }
    }
    if (open) {
        SourcePickerSheet(
            title = sheetTitle,
            sources = sources,
            selectedIds = selectedIds,
            onToggle = onToggle,
            onShowAll = onShowAll,
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePickerSheet(
    title: String,
    sources: List<CatalogSourceOption>,
    selectedIds: Set<String>,
    onToggle: (CatalogSourceOption) -> Unit,
    onShowAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SourcePickerSort.Default) }
    val close: () -> Unit = { scope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) } }
    val listed = remember(sources, query, sort) { sources.filteredAndSortedForPicker(query, sort) }

    NuvioModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = nuvioSafeBottomPadding(12.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = close) {
                    Text(
                        text = stringResource(Res.string.library_categories_done),
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.accent,
                    )
                }
            }
            NuvioInputField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(Res.string.source_picker_search_hint),
                trailingContent = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = tokens.colors.textMuted)
                        }
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.colors.textMuted)
                    }
                },
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassChip(
                    label = stringResource(Res.string.source_picker_sort_default),
                    selected = sort == SourcePickerSort.Default,
                    onClick = { sort = SourcePickerSort.Default },
                )
                GlassChip(
                    label = stringResource(Res.string.source_picker_sort_az),
                    selected = sort == SourcePickerSort.Alphabetical,
                    onClick = { sort = SourcePickerSort.Alphabetical },
                )
                GlassChip(
                    label = stringResource(Res.string.source_picker_sort_recent),
                    selected = sort == SourcePickerSort.RecentlyAdded,
                    onClick = { sort = SourcePickerSort.RecentlyAdded },
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (query.isBlank()) {
                    item(key = "all") {
                        GlassCheckRow(
                            title = stringResource(Res.string.settings_catalog_source_all),
                            checked = selectedIds.isEmpty(),
                            onCheckedChange = { onShowAll() },
                        )
                    }
                }
                items(listed, key = { it.id }) { source ->
                    GlassCheckRow(
                        title = source.name,
                        checked = source.id in selectedIds,
                        onCheckedChange = { onToggle(source) },
                    )
                }
                if (listed.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(Res.string.source_picker_no_match, query.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Sources whose name contains [query], in the [sort] order. */
internal fun List<CatalogSourceOption>.filteredAndSortedForPicker(
    query: String,
    sort: SourcePickerSort,
): List<CatalogSourceOption> {
    val needle = query.trim()
    val filtered = if (needle.isEmpty()) this else filter { it.name.contains(needle, ignoreCase = true) }
    return when (sort) {
        SourcePickerSort.Default -> filtered
        SourcePickerSort.Alphabetical -> filtered.sortedBy { it.name.lowercase() }
        SourcePickerSort.RecentlyAdded -> filtered.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<CatalogSourceOption>> { SourceAddedOrder.firstSeen(it.value.id) ?: Long.MIN_VALUE }
                    .thenByDescending { it.index },
            )
            .map { it.value }
    }
}
