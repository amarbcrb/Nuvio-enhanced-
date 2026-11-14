package com.nuvio.app.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.streams.BingeGroupCacheStorage
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.library_history_clear
import nuvio.composeapp.generated.resources.library_history_clear_confirm
import nuvio.composeapp.generated.resources.library_history_clear_message
import nuvio.composeapp.generated.resources.library_history_empty_message
import nuvio.composeapp.generated.resources.library_history_empty_title
import nuvio.composeapp.generated.resources.library_history_episode
import nuvio.composeapp.generated.resources.library_history_remove
import nuvio.composeapp.generated.resources.library_history_today
import nuvio.composeapp.generated.resources.library_history_watched
import nuvio.composeapp.generated.resources.library_history_yesterday
import org.jetbrains.compose.resources.stringResource

/** One title of the watch history: the latest thing watched for it, and when. */
internal data class LibraryHistoryEntry(
    val contentId: String,
    val contentType: String,
    val title: String,
    val poster: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val progressFraction: Float?,
    val watchedAtEpochMs: Long,
) {
    fun toMetaPreview(): MetaPreview = MetaPreview(id = contentId, type = contentType, name = title, poster = poster)
}

/**
 * Titles removed from the history, with the time they were removed: a title watched again later
 * comes back. Only the history list is affected, never the progress or the watched marks.
 */
internal object LibraryHistoryHiddenStore {
    private const val KEY = "library_history_hidden_v1"
    private val _hidden = MutableStateFlow<Map<String, Long>>(emptyMap())
    val hidden: StateFlow<Map<String, Long>> = _hidden.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        reload()
    }

    fun reload() {
        loaded = true
        _hidden.value = BingeGroupCacheStorage.load(KEY)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val id = line.substringBeforeLast('\t', missingDelimiterValue = "").takeIf { it.isNotBlank() }
                val at = line.substringAfterLast('\t').toLongOrNull()
                if (id != null && at != null) id to at else null
            }
            ?.toMap()
            .orEmpty()
    }

    fun hide(contentId: String, atEpochMs: Long) {
        ensureLoaded()
        save(_hidden.value + (contentId to atEpochMs))
    }

    /** Empties the history: every title listed now is hidden until it is watched again. */
    fun hideAll(entries: List<LibraryHistoryEntry>) {
        ensureLoaded()
        save(_hidden.value + entries.associate { it.contentId to it.watchedAtEpochMs })
    }

    private fun save(updated: Map<String, Long>) {
        _hidden.value = updated
        BingeGroupCacheStorage.save(KEY, updated.entries.joinToString("\n") { "${it.key}\t${it.value}" })
    }
}

/** Newest first, one row per title, from the playback progress and the watched marks. */
internal fun buildLibraryHistory(
    progress: List<WatchProgressEntry>,
    watched: List<WatchedItem>,
    hidden: Map<String, Long>,
): List<LibraryHistoryEntry> {
    val byContent = HashMap<String, LibraryHistoryEntry>()
    fun offer(entry: LibraryHistoryEntry) {
        if (entry.contentId.isBlank() || entry.watchedAtEpochMs <= 0L) return
        val current = byContent[entry.contentId]
        if (current == null || entry.watchedAtEpochMs > current.watchedAtEpochMs) {
            // Keep the poster when the newer entry has none.
            byContent[entry.contentId] = if (entry.poster == null && current?.poster != null) {
                entry.copy(poster = current.poster)
            } else {
                entry
            }
        }
    }
    progress.forEach { entry ->
        offer(
            LibraryHistoryEntry(
                contentId = entry.parentMetaId,
                contentType = entry.parentMetaType.ifBlank { entry.contentType },
                title = entry.title,
                poster = entry.poster,
                seasonNumber = entry.seasonNumber,
                episodeNumber = entry.episodeNumber,
                episodeTitle = entry.episodeTitle,
                progressFraction = if (entry.isEffectivelyCompleted) {
                    1f
                } else {
                    entry.normalizedProgressPercent?.div(100f)
                        ?: if (entry.durationMs > 0L) (entry.lastPositionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) else null
                },
                watchedAtEpochMs = entry.lastUpdatedEpochMs,
            ),
        )
    }
    watched.forEach { item ->
        offer(
            LibraryHistoryEntry(
                contentId = item.id,
                contentType = item.type,
                title = item.name,
                poster = item.poster,
                seasonNumber = item.season,
                episodeNumber = item.episode,
                episodeTitle = null,
                progressFraction = 1f,
                watchedAtEpochMs = item.markedAtEpochMs,
            ),
        )
    }
    return byContent.values
        .filter { entry -> hidden[entry.contentId]?.let { entry.watchedAtEpochMs > it } ?: true }
        .sortedByDescending { it.watchedAtEpochMs }
}

internal fun LazyListScope.libraryHistoryContent(
    entries: List<LibraryHistoryEntry>,
    nowEpochMs: Long,
    onOpen: (LibraryHistoryEntry) -> Unit,
    onRemove: (LibraryHistoryEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        item(key = "library-history-empty") {
            HomeEmptyStateCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                title = stringResource(Res.string.library_history_empty_title),
                message = stringResource(Res.string.library_history_empty_message),
            )
        }
        return
    }
    items(entries, key = { "library-history:" + it.contentId }) { entry ->
        LibraryHistoryRow(
            entry = entry,
            nowEpochMs = nowEpochMs,
            onClick = { onOpen(entry) },
            onRemove = { onRemove(entry) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun LibraryHistoryRow(
    entry: LibraryHistoryEntry,
    nowEpochMs: Long,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.poster != null) {
                AsyncImage(
                    model = entry.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(Icons.Rounded.History, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.seasonNumber != null && entry.episodeNumber != null) {
                val episodeLabel = stringResource(Res.string.library_history_episode, entry.seasonNumber, entry.episodeNumber)
                Text(
                    text = listOfNotNull(episodeLabel, entry.episodeTitle?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = historyDateLabel(entry.watchedAtEpochMs, nowEpochMs),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
            val fraction = entry.progressFraction
            if (fraction != null && fraction > 0f) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.library_history_remove),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LibraryClearHistoryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NuvioStatusModal(
        title = stringResource(Res.string.library_history_clear),
        message = stringResource(Res.string.library_history_clear_message),
        isVisible = true,
        destructive = true,
        confirmText = stringResource(Res.string.library_history_clear_confirm),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** "Today 21:05", "Yesterday 18:40" or "Watched on 12/11/2026". */
@Composable
private fun historyDateLabel(epochMs: Long, nowEpochMs: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val at = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
    val today = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(zone).date
    val time = "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    return when (at.date) {
        today -> stringResource(Res.string.library_history_today, time)
        LocalDate.fromEpochDays(today.toEpochDays() - 1) -> stringResource(Res.string.library_history_yesterday, time)
        else -> stringResource(
            Res.string.library_history_watched,
            "${at.day.toString().padStart(2, '0')}/${at.month.number.toString().padStart(2, '0')}/${at.year}",
        )
    }
}
