package com.nuvio.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository

/** Artwork already known for a title, shown while its details load instead of a black screen. */
internal data class DetailLoadingArtwork(
    val name: String?,
    val background: String?,
    val logo: String?,
)

/** Remembers the artwork of the posters the user opened, so the details screen can show it at once. */
internal object DetailPreviewStore {
    private val entries = LinkedHashMap<String, DetailLoadingArtwork>()

    fun put(preview: MetaPreview) {
        val artwork = DetailLoadingArtwork(
            name = preview.name.takeIf { it.isNotBlank() },
            background = preview.banner ?: preview.poster,
            logo = preview.logo,
        )
        if (artwork.background == null && artwork.logo == null) return
        entries.remove(key(preview.type, preview.id))
        entries[key(preview.type, preview.id)] = artwork
        while (entries.size > 64) entries.remove(entries.keys.first())
    }

    /** From the opened poster, else from the library or the playback progress of the title. */
    fun find(type: String, id: String): DetailLoadingArtwork? {
        entries[key(type, id)]?.let { return it }
        LibraryRepository.uiState.value.items.firstOrNull { it.id == id }?.let { item ->
            return DetailLoadingArtwork(item.name, item.banner ?: item.poster, item.logo)
        }
        WatchProgressRepository.uiState.value.entries.firstOrNull { it.parentMetaId == id }?.let { entry ->
            return DetailLoadingArtwork(entry.title, entry.background ?: entry.poster, entry.logo)
        }
        return null
    }

    private fun key(type: String, id: String) = "$type:$id"
}

@Composable
internal fun DetailLoadingPlaceholder(type: String, id: String, modifier: Modifier = Modifier) {
    val artwork = remember(type, id) { runCatching { DetailPreviewStore.find(type, id) }.getOrNull() }
    Box(modifier = modifier.fillMaxSize()) {
        if (artwork?.background != null) {
            AsyncImage(
                model = artwork.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.6f to Color.Black.copy(alpha = 0.6f),
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            var logoFailed by remember(artwork?.logo) { mutableStateOf(false) }
            if (artwork?.logo != null && !logoFailed) {
                AsyncImage(
                    model = artwork.logo,
                    contentDescription = artwork.name,
                    contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                    modifier = Modifier.fillMaxWidth(0.7f).height(110.dp),
                )
            } else if (artwork?.name != null) {
                Text(
                    text = artwork.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
            }
            NuvioLoadingIndicator(modifier = Modifier.size(40.dp))
        }
    }
}
