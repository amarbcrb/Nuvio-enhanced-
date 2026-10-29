package com.nuvio.app.features.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioInputField
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.library_categories_title
import nuvio.composeapp.generated.resources.library_categories_done
import nuvio.composeapp.generated.resources.library_category_create
import nuvio.composeapp.generated.resources.library_category_delete
import nuvio.composeapp.generated.resources.library_category_new_hint
import org.jetbrains.compose.resources.stringResource

/** Puts [item] in the user's Library categories, and creates or deletes categories. */
@Composable
internal fun LibraryCategoryPickerDialog(
    item: LibraryItem,
    onDismiss: () -> Unit,
) {
    val state by remember {
        LibraryCategoriesRepository.ensureLoaded()
        LibraryCategoriesRepository.state
    }.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    val key = item.categoryKey()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.library_categories_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.categories.forEach { category ->
                    val checked = key in category.itemKeys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { LibraryCategoriesRepository.setInCategory(category.id, item, !checked) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { LibraryCategoriesRepository.setInCategory(category.id, item, it) },
                        )
                        Text(
                            category.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { LibraryCategoriesRepository.deleteCategory(category.id) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(Res.string.library_category_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NuvioInputField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = stringResource(Res.string.library_category_new_hint),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            LibraryCategoriesRepository.createCategory(newName)?.let { category ->
                                LibraryCategoriesRepository.setInCategory(category.id, item, true)
                            }
                            newName = ""
                        },
                    ) {
                        Text(stringResource(Res.string.library_category_create))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.library_categories_done)) }
        },
    )
}
