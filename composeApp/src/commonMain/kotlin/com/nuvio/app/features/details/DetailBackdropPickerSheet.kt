package com.nuvio.app.features.details

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.GlassCheckRow
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.streams.GlassStreamSheet
import com.nuvio.app.features.tmdb.TmdbMetadataService
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.details_background_automatic
import nuvio.composeapp.generated.resources.details_background_none
import nuvio.composeapp.generated.resources.details_change_background
import nuvio.composeapp.generated.resources.details_change_poster
import org.jetbrains.compose.resources.stringResource

/**
 * Lists the backgrounds TMDB has for the title (all languages) and keeps the one the user taps for
 * this title. "Automatic" goes back to the background the app picks itself.
 */
@Composable
internal fun DetailBackdropPickerSheet(
    type: String,
    id: String,
    currentBackground: String?,
    onDismiss: () -> Unit,
) {
    val chosen = remember(type, id) { DetailBackdropOverrideRepository.get(type, id) }
    ArtworkPickerSheet(
        title = stringResource(Res.string.details_change_background),
        loadOptions = { TmdbMetadataService.fetchBackdropOptions(id, type) },
        selectedUrl = chosen ?: currentBackground,
        automaticSelected = chosen == null,
        columns = 2,
        aspectRatio = 16f / 9f,
        onAutomatic = {
            DetailBackdropOverrideRepository.set(type, id, null)
            MetaDetailsRepository.load(type, id)
            MetaDetailsRepository.refreshChosenBackdrop()
        },
        onPick = { full ->
            DetailBackdropOverrideRepository.set(type, id, full)
            MetaDetailsRepository.refreshChosenBackdrop()
        },
        onDismiss = onDismiss,
    )
}

/**
 * Lists the posters TMDB has for the title; the one tapped replaces [currentPosterUrl] on every
 * screen. "Automatic" goes back to the original poster.
 */
@Composable
internal fun PosterPickerSheet(
    type: String,
    id: String,
    currentPosterUrl: String?,
    onDismiss: () -> Unit,
) {
    val originalUrl = remember(currentPosterUrl) {
        currentPosterUrl?.let { com.nuvio.app.core.poster.PosterOverrideRepository.originalOf(it) }
    }
    val chosen = remember(originalUrl) {
        originalUrl?.let { com.nuvio.app.core.poster.PosterOverrideRepository.replacementFor(it) }
    }
    ArtworkPickerSheet(
        title = stringResource(Res.string.details_change_poster),
        loadOptions = { TmdbMetadataService.fetchPosterOptions(id, type) },
        selectedUrl = chosen ?: originalUrl,
        automaticSelected = chosen == null,
        columns = 3,
        aspectRatio = 2f / 3f,
        onAutomatic = { originalUrl?.let { com.nuvio.app.core.poster.PosterOverrideRepository.set(it, null) } },
        onPick = { full ->
            originalUrl?.let { com.nuvio.app.core.poster.PosterOverrideRepository.set(it, full) }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ArtworkPickerSheet(
    title: String,
    loadOptions: suspend () -> List<Pair<String, String>>,
    selectedUrl: String?,
    automaticSelected: Boolean,
    columns: Int,
    aspectRatio: Float,
    onAutomatic: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var options by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) {
        options = runCatching { loadOptions() }.getOrDefault(emptyList())
    }
    GlassStreamSheet(title = title, onDismiss = onDismiss) { close ->
        GlassCheckRow(
            title = stringResource(Res.string.details_background_automatic),
            checked = automaticSelected,
            onCheckedChange = {
                onAutomatic()
                close()
            },
        )
        val list = options
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                NuvioLoadingIndicator()
            }
            list.isEmpty() -> Text(
                text = stringResource(Res.string.details_background_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
            else -> list.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (thumb, full) ->
                        val selected = full == selectedUrl
                        AsyncImage(
                            model = thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(aspectRatio)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.nuvio.colors.accent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    onPick(full)
                                    close()
                                },
                        )
                    }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}
