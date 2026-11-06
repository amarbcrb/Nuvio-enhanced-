package com.nuvio.app.features.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.GlassCheckRow
import com.nuvio.app.core.ui.GlassSheetSectionLabel
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.library_categories_done
import nuvio.composeapp.generated.resources.library_categories_empty
import nuvio.composeapp.generated.resources.library_categories_section_library
import nuvio.composeapp.generated.resources.library_categories_section_mine
import nuvio.composeapp.generated.resources.library_categories_title
import nuvio.composeapp.generated.resources.library_category_anime
import nuvio.composeapp.generated.resources.library_category_create
import nuvio.composeapp.generated.resources.library_category_delete
import nuvio.composeapp.generated.resources.library_category_favorites
import nuvio.composeapp.generated.resources.library_category_movies
import nuvio.composeapp.generated.resources.library_category_new_hint
import nuvio.composeapp.generated.resources.library_category_series
import org.jetbrains.compose.resources.stringResource

/**
 * Liquid glass sheet that puts [item] in the Library's existing categories: Favorites, Anime,
 * Movies, Series (and its own kind), then the user's categories, which can be created or deleted.
 * A title put in a category is saved in the Library too, since categories only list saved titles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryCategoryPickerDialog(
    item: LibraryItem,
    onDismiss: () -> Unit,
) {
    val state by remember {
        LibraryCategoriesRepository.ensureLoaded()
        LibraryCategoriesRepository.state
    }.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val tokens = MaterialTheme.nuvio
    var newName by remember { mutableStateOf("") }
    val key = item.categoryKey()

    val close: () -> Unit = { scope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) } }
    val setFavorite: (Boolean) -> Unit = { favorite ->
        if (favorite) LibraryRepository.saveLocallyIfMissing(item)
        if (favorite != (key in state.favorites)) LibraryCategoriesRepository.toggleFavorite(item)
    }
    val setInSection: (String, Boolean) -> Unit = { section, inSection ->
        if (inSection) LibraryRepository.saveLocallyIfMissing(item)
        LibraryCategoriesRepository.setInSection(section, item, inSection)
    }
    val setInCategory: (String, Boolean) -> Unit = { categoryId, inCategory ->
        if (inCategory) LibraryRepository.saveLocallyIfMissing(item)
        LibraryCategoriesRepository.setInCategory(categoryId, item, inCategory)
    }

    val builtInSections = buildList {
        add(BuiltInSection(LIBRARY_ANIME_SECTION, stringResource(Res.string.library_category_anime), Icons.Rounded.Animation))
        add(BuiltInSection("movie", stringResource(Res.string.library_category_movies), Icons.Rounded.Movie))
        add(BuiltInSection("series", stringResource(Res.string.library_category_series), Icons.Rounded.Tv))
        val ownSection = item.librarySectionType()
        if (none { it.type == ownSection }) {
            add(BuiltInSection(ownSection, ownSection.replaceFirstChar { it.titlecase() }, Icons.AutoMirrored.Rounded.Label))
        }
    }

    NuvioModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = nuvioSafeBottomPadding(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.library_categories_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textPrimary,
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = close) {
                    Text(
                        text = stringResource(Res.string.library_categories_done),
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.accent,
                    )
                }
            }

            GlassSheetSectionLabel(stringResource(Res.string.library_categories_section_library))
            GlassCheckRow(
                title = stringResource(Res.string.library_category_favorites),
                checked = key in state.favorites,
                onCheckedChange = setFavorite,
                icon = Icons.Rounded.Star,
            )
            builtInSections.forEach { section ->
                GlassCheckRow(
                    title = section.title,
                    checked = state.isInSection(item, section.type),
                    onCheckedChange = { setInSection(section.type, it) },
                    icon = section.icon,
                )
            }

            GlassSheetSectionLabel(stringResource(Res.string.library_categories_section_mine))
            if (state.categories.isEmpty()) {
                Text(
                    text = stringResource(Res.string.library_categories_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            state.categories.forEach { category ->
                GlassCheckRow(
                    title = category.name,
                    checked = key in category.itemKeys,
                    onCheckedChange = { setInCategory(category.id, it) },
                    icon = Icons.AutoMirrored.Rounded.Label,
                    trailing = {
                        IconButton(
                            onClick = { LibraryCategoriesRepository.deleteCategory(category.id) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(Res.string.library_category_delete),
                                tint = tokens.colors.danger,
                            )
                        }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
                            setInCategory(category.id, true)
                        }
                        newName = ""
                    },
                ) {
                    Text(stringResource(Res.string.library_category_create), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private class BuiltInSection(
    val type: String,
    val title: String,
    val icon: ImageVector,
)
