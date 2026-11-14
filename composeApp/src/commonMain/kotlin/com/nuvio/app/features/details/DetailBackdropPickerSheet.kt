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
    var options by remember(type, id) { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(type, id) {
        options = runCatching { TmdbMetadataService.fetchBackdropOptions(id, type) }.getOrDefault(emptyList())
    }
    val chosen = remember(type, id) { DetailBackdropOverrideRepository.get(type, id) }
    GlassStreamSheet(
        title = org.jetbrains.compose.resources.stringResource(Res.string.details_change_background),
        onDismiss = onDismiss,
    ) { close ->
        GlassCheckRow(
            title = org.jetbrains.compose.resources.stringResource(Res.string.details_background_automatic),
            checked = chosen == null,
            onCheckedChange = {
                DetailBackdropOverrideRepository.set(type, id, null)
                MetaDetailsRepository.load(type, id)
                MetaDetailsRepository.refreshChosenBackdrop()
                close()
            },
        )
        val list = options
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                NuvioLoadingIndicator()
            }
            list.isEmpty() -> Text(
                text = org.jetbrains.compose.resources.stringResource(Res.string.details_background_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
            else -> list.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { (thumb, full) ->
                        val selected = full == (chosen ?: currentBackground)
                        AsyncImage(
                            model = thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.nuvio.colors.accent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    DetailBackdropOverrideRepository.set(type, id, full)
                                    MetaDetailsRepository.refreshChosenBackdrop()
                                    close()
                                },
                        )
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}
