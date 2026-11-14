package com.nuvio.app.features.streams

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Menu
import com.nuvio.app.core.ui.MenuItem
import com.nuvio.app.core.ui.GlassCheckRow
import com.nuvio.app.core.ui.GlassPill
import com.nuvio.app.core.ui.GlassSheetSectionLabel
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.collections_tab_all
import nuvio.composeapp.generated.resources.streams_pin_source
import nuvio.composeapp.generated.resources.streams_pinned_source
import nuvio.composeapp.generated.resources.streams_refresh
import nuvio.composeapp.generated.resources.streams_favorites_only
import nuvio.composeapp.generated.resources.streams_language_filter_all
import nuvio.composeapp.generated.resources.streams_language_filter_title
import nuvio.composeapp.generated.resources.streams_search_scope_all
import nuvio.composeapp.generated.resources.streams_search_scope_title
import nuvio.composeapp.generated.resources.streams_search_scope_unavailable
import nuvio.composeapp.generated.resources.streams_sources_menu
import nuvio.composeapp.generated.resources.streams_languages_menu
import nuvio.composeapp.generated.resources.streams_language_menu
import nuvio.composeapp.generated.resources.settings_stream_dub_any
import nuvio.composeapp.generated.resources.settings_stream_dub_prefer
import nuvio.composeapp.generated.resources.settings_stream_dub_only
import nuvio.composeapp.generated.resources.settings_stream_dub_title
import nuvio.composeapp.generated.resources.streams_unpin_source
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.core.ui.glass.ambientLiquidGlass

@Composable
internal fun ProviderFilterRow(
    groups: List<AddonStreamGroup>,
    selectedFilter: String?,
    onFilterSelected: (String?) -> Unit,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = groups.any { it.isLoading },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    spacing: Dp = 8.dp,
    refreshChip: @Composable (Boolean, () -> Unit) -> Unit = { isLoading, onClick ->
        RefreshChip(isLoading = isLoading, onClick = onClick)
    },
    sourceOptions: List<StreamSourceOption> = emptyList(),
    searchScopeId: String? = null,
    onSearchScopeSelected: ((StreamSourceOption?) -> Unit)? = null,
    filterChip: (@Composable (AddonStreamGroup?, Boolean, () -> Unit) -> Unit)? = null,
) {
    val addonGroups = groups.filter { it.streams.isNotEmpty() || it.isLoading }
    val storedScope by remember {
        StreamSearchScopeRepository.ensureLoaded()
        StreamSearchScopeRepository.scope
    }.collectAsStateWithLifecycle()
    var showSearchScopeSheet by remember { mutableStateOf(false) }
    val favoritesOnly by StreamSearchScopeRepository.favoritesOnly.collectAsStateWithLifecycle()
    val showSearchScopeChip = onSearchScopeSelected != null && (sourceOptions.size > 1 || storedScope != null)
    val languagePreferences by remember {
        StreamLanguagePreferencesRepository.ensureLoaded()
        StreamLanguagePreferencesRepository.preferences
    }.collectAsStateWithLifecycle()
    val languageFilterPaused by StreamLanguagePreferencesRepository.linkFilterPaused.collectAsStateWithLifecycle()
    val pinnedSourceIds by rememberPinnedStreamSourceIds()
    var pinSheetTarget by remember { mutableStateOf<PinTarget?>(null) }
    var showFavoritesSheet by remember { mutableStateOf(false) }
    LaunchedEffect(addonGroups, selectedFilter) {
        if (selectedFilter != null && addonGroups.none { it.addonId == selectedFilter }) {
            onFilterSelected(null)
        }
    }
    if (addonGroups.isEmpty() && onRefresh == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (onRefresh != null) {
            refreshChip(isRefreshing, onRefresh)
        }
        // One drop-down for the sources (and where to search), one for the language.
        val selectedGroup = addonGroups.firstOrNull { it.addonId == selectedFilter }
        var sourceMenuExpanded by remember { mutableStateOf(false) }
        Box {
            if (filterChip != null) {
                filterChip(selectedGroup, selectedGroup != null) { sourceMenuExpanded = true }
            } else {
                FilterChip(
                    label = selectedGroup?.addonName ?: stringResource(Res.string.streams_sources_menu),
                    isSelected = selectedGroup != null,
                    isPinned = selectedGroup != null && selectedGroup.favoriteTarget()?.key in pinnedSourceIds,
                    trailingIcon = Icons.Rounded.KeyboardArrowDown,
                    onClick = { sourceMenuExpanded = true },
                    onLongClick = selectedGroup?.let { group -> { pinSheetTarget = group.favoriteTarget() } },
                )
            }
        }
        if (sourceMenuExpanded) {
            StreamSourcesSheet(
                groups = addonGroups,
                selectedFilter = selectedFilter,
                pinnedSourceIds = pinnedSourceIds,
                scopeLabel = if (showSearchScopeChip) {
                    sourceOptions.firstOrNull { it.id == searchScopeId }?.name
                        ?: stringResource(Res.string.streams_search_scope_all)
                } else {
                    null
                },
                onFilterSelected = onFilterSelected,
                onOpenSearchScope = { showSearchScopeSheet = true },
                onDismiss = { sourceMenuExpanded = false },
            )
        }
        if (onRefresh != null) {
            LanguageMenuChip(
                preferences = languagePreferences,
                filterPaused = languageFilterPaused,
                onChanged = onRefresh,
            )
            // Opens the favorite sources: tick or untick each one, and choose whether only they are searched.
            FilterChip(
                label = stringResource(Res.string.streams_favorites_only),
                icon = if (favoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                isSelected = favoritesOnly,
                onClick = { showFavoritesSheet = true },
            )
        }
    }

    if (showFavoritesSheet && onRefresh != null) {
        FavoriteSourcesSheet(
            sources = favoriteSheetSources(sourceOptions, addonGroups, pinnedSourceIds),
            pinnedSourceIds = pinnedSourceIds,
            favoritesOnly = favoritesOnly,
            onDismiss = { changed ->
                showFavoritesSheet = false
                if (changed) onRefresh()
            },
        )
    }

    StreamSourcePinSheet(
        target = pinSheetTarget,
        onDismiss = { pinSheetTarget = null },
    )

    if (showSearchScopeSheet && onSearchScopeSelected != null) {
        StreamSearchScopeSheet(
            options = sourceOptions,
            storedScope = storedScope,
            appliedScopeId = searchScopeId,
            onSelected = { option ->
                showSearchScopeSheet = false
                onSearchScopeSelected(option)
            },
            onDismiss = { showSearchScopeSheet = false },
        )
    }
}

@Composable
private fun StreamSearchScopeSheet(
    options: List<StreamSourceOption>,
    storedScope: StreamSourceOption?,
    appliedScopeId: String?,
    onSelected: (StreamSourceOption?) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf<StreamSourceOption?>(null) }
    var picked by remember { mutableStateOf(false) }
    GlassStreamSheet(
        title = stringResource(Res.string.streams_search_scope_title),
        subtitle = if (storedScope != null && appliedScopeId == null) {
            stringResource(Res.string.streams_search_scope_unavailable, storedScope.name)
        } else {
            null
        },
        // The choice is applied once the sheet has closed, as before.
        onDismiss = { if (picked) onSelected(chosen) else onDismiss() },
    ) { close ->
        GlassCheckRow(
            title = stringResource(Res.string.streams_search_scope_all),
            checked = storedScope == null,
            onCheckedChange = {
                chosen = null
                picked = true
                close()
            },
        )
        options.forEach { option ->
            GlassCheckRow(
                title = option.name,
                checked = storedScope?.id == option.id,
                onCheckedChange = {
                    chosen = option
                    picked = true
                    close()
                },
            )
        }
    }
}

@Composable
internal fun rememberPinnedStreamSourceIds(): State<List<String>> {
    LaunchedEffect(Unit) { PinnedStreamSourcesRepository.ensureLoaded() }
    return PinnedStreamSourcesRepository.pinnedSourceIds.collectAsStateWithLifecycle()
}

internal fun AddonStreamGroup.soleSourcePin(): PinTarget? {
    if (addonId.startsWith("debrid:")) return null
    val playableStreams = streams.filterNot { it.isAddonDebridCandidate && it.isDirectDebridStream }
    val sourceNames = playableStreams
        .map { it.sourceName?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
    if (sourceNames.size > 1) return null
    val sourceName = sourceNames.firstOrNull()
    return PinTarget(
        key = PinnedStreamSourcesRepository.sourceKeyFor(addonId = addonId, sourceName = sourceName),
        label = sourceName ?: addonName,
    )
}

internal data class PinTarget(val key: String, val label: String)

/**
 * Language drop-down of the stream screens: the saved link languages or every language, and how
 * dubbed versions are favoured. Any change reloads the links through [onChanged].
 */
@Composable
private fun LanguageMenuChip(
    preferences: StreamLanguagePreferences,
    filterPaused: Boolean,
    onChanged: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasLanguages = preferences.linkLanguages.isNotEmpty()
    val savedLanguages = preferences.linkLanguages.sorted().joinToString(", ") { it.uppercase() }
    Box {
        FilterChip(
            label = if (hasLanguages && !filterPaused) savedLanguages else stringResource(Res.string.streams_language_menu),
            isSelected = hasLanguages && !filterPaused,
            trailingIcon = Icons.Rounded.KeyboardArrowDown,
            onClick = { expanded = true },
        )
    }
    if (expanded) {
        GlassStreamSheet(
            title = stringResource(Res.string.streams_language_filter_title),
            onDismiss = { expanded = false },
        ) { close ->
            if (hasLanguages) {
                GlassCheckRow(
                    title = stringResource(Res.string.streams_languages_menu, savedLanguages),
                    checked = !filterPaused,
                    icon = Icons.Rounded.Translate,
                    onCheckedChange = {
                        close()
                        if (filterPaused) {
                            StreamLanguagePreferencesRepository.setLinkFilterPaused(false)
                            onChanged()
                        }
                    },
                )
                GlassCheckRow(
                    title = stringResource(Res.string.streams_language_filter_all),
                    checked = filterPaused,
                    icon = Icons.Rounded.Language,
                    onCheckedChange = {
                        close()
                        if (!filterPaused) {
                            StreamLanguagePreferencesRepository.setLinkFilterPaused(true)
                            onChanged()
                        }
                    },
                )
            }
            GlassSheetSectionLabel(stringResource(Res.string.settings_stream_dub_title))
            StreamDubMode.entries.forEach { mode ->
                GlassCheckRow(
                    title = stringResource(
                        when (mode) {
                            StreamDubMode.Any -> Res.string.settings_stream_dub_any
                            StreamDubMode.PreferDubbed -> Res.string.settings_stream_dub_prefer
                            StreamDubMode.DubbedOnly -> Res.string.settings_stream_dub_only
                        },
                    ),
                    checked = preferences.dubMode == mode,
                    onCheckedChange = {
                        close()
                        if (preferences.dubMode != mode) {
                            StreamLanguagePreferencesRepository.update { it.copy(dubMode = mode) }
                            onChanged()
                        }
                    },
                )
            }
        }
    }
}

/** Liquid glass sheet listing the stream sources: pick one to filter, star it as a favorite. */
@Composable
private fun StreamSourcesSheet(
    groups: List<AddonStreamGroup>,
    selectedFilter: String?,
    pinnedSourceIds: List<String>,
    scopeLabel: String?,
    onFilterSelected: (String?) -> Unit,
    onOpenSearchScope: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassStreamSheet(
        title = stringResource(Res.string.streams_sources_menu),
        onDismiss = onDismiss,
    ) { close ->
        GlassCheckRow(
            title = stringResource(Res.string.collections_tab_all),
            checked = selectedFilter == null,
            onCheckedChange = {
                close()
                onFilterSelected(null)
            },
        )
        groups.forEach { group ->
            val target = group.favoriteTarget()
            GlassCheckRow(
                title = sourceMenuLabel(group = group, isFavorite = false),
                checked = selectedFilter == group.addonId,
                onCheckedChange = {
                    close()
                    onFilterSelected(group.addonId)
                },
                trailing = if (target != null) {
                    { FavoriteSourceStar(target = target, isFavorite = target.key in pinnedSourceIds, size = 20.dp) }
                } else {
                    null
                },
            )
        }
        if (scopeLabel != null) {
            GlassSheetSectionLabel(stringResource(Res.string.streams_search_scope_title))
            GlassPill(onClick = {
                close()
                onOpenSearchScope()
            }) {
                Text(
                    text = scopeLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.nuvio.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.nuvio.colors.textMuted,
                )
            }
        }
    }
}

/** Liquid glass bottom sheet with a title; [content] gets a function that closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassStreamSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val close: () -> Unit = {
        coroutineScope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
    }
    NuvioModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = nuvioSafeBottomPadding(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.nuvio.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = if (subtitle == null) 4.dp else 0.dp),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.nuvio.colors.textMuted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            content(close)
        }
    }
}

/** "★ Name (12)" for a source of the drop-down list, with "…" while it is still searching. */
internal fun sourceMenuLabel(group: AddonStreamGroup, isFavorite: Boolean): String = buildString {
    if (isFavorite) append("★ ")
    append(group.addonName)
    if (group.isLoading) append(" …") else append(" (${group.streams.size})")
}

/**
 * The favorite (pinned) source a group header's star stands for: the whole source (addon,
 * extension or plugin group), the same key as the pin on the CloudStream settings page.
 */
internal fun AddonStreamGroup.favoriteTarget(): PinTarget? = when {
    // Headers that stand for no source, like the stream shared in a watch party.
    streams.isEmpty() && !isLoading -> null
    addonId.startsWith("debrid:") -> null
    else -> PinTarget(key = addonId, label = addonName)
}

/** Star button that adds [target] to or removes it from the favorite sources. */
@Composable
internal fun FavoriteSourceStar(
    target: PinTarget,
    isFavorite: Boolean,
    size: Dp,
) {
    Icon(
        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
        contentDescription = stringResource(
            if (isFavorite) Res.string.streams_unpin_source else Res.string.streams_pin_source,
        ),
        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { PinnedStreamSourcesRepository.setPinned(target.key, !isFavorite) }
            .padding(6.dp)
            .size(size),
    )
}

@Composable
internal fun StreamSourcePinSheet(
    target: PinTarget?,
    onDismiss: () -> Unit,
) {
    if (target == null) return
    val pinnedSourceIds by rememberPinnedStreamSourceIds()
    val isPinned = target.key in pinnedSourceIds

    GlassStreamSheet(title = target.label, onDismiss = onDismiss) { close ->
        GlassCheckRow(
            title = stringResource(Res.string.streams_pin_source),
            checked = isPinned,
            icon = Icons.Rounded.Star,
            onCheckedChange = { pinned ->
                PinnedStreamSourcesRepository.setPinned(target.key, pinned)
                close()
            },
        )
    }
}

@Composable
internal fun rememberRefreshRotation(isLoading: Boolean): Animatable<Float, AnimationVector1D> {
    val rotation = remember { Animatable(0f) }
    var hasCompletedFullRotation by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(100)
            hasCompletedFullRotation = false
            rotation.animateTo(360f, remainingTurn(rotation.value))
            hasCompletedFullRotation = true
            rotation.snapTo(0f)
            rotation.animateTo(360f, infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)))
        } else {
            if (hasCompletedFullRotation && rotation.value > 0f) {
                rotation.animateTo(360f, remainingTurn(rotation.value))
            }
            rotation.snapTo(0f)
            hasCompletedFullRotation = false
        }
    }
    return rotation
}

private fun remainingTurn(rotation: Float) =
    tween<Float>(durationMillis = ((360f - rotation) / 360f * 1000).toInt(), easing = LinearEasing)

@Composable
private fun RefreshChip(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val rotation = rememberRefreshRotation(isLoading)
    FilterChip(
        icon = Icons.Rounded.Refresh,
        contentDescription = stringResource(Res.string.streams_refresh),
        iconModifier = Modifier.graphicsLayer { rotationZ = rotation.value },
        isSelected = false,
        onClick = onClick,
    )
}

@Composable
private fun FilterChip(
    label: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    iconModifier: Modifier = Modifier,
    isSelected: Boolean,
    isPinned: Boolean = false,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "filter_chip_scale",
    )
    // Liquid glass: a translucent pill with a light edge, tinted with the accent when selected.
    val accent = MaterialTheme.nuvio.colors.accent
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(durationMillis = 180),
        label = "filter_chip_container",
    )
    val edgeColor by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.16f),
        animationSpec = tween(durationMillis = 180),
        label = "filter_chip_edge",
    )
    val contentColor = Color.White
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(36.dp)
            .clip(RoundedCornerShape(16.dp))
            .ambientLiquidGlass(
                shape = RoundedCornerShape(16.dp),
                tint = containerColor,
                fallback = containerColor,
                blurRadius = 14.dp,
                refractionHeight = 8.dp,
            )
            .border(1.dp, edgeColor, RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp).then(iconModifier),
                )
            }
            if (isPinned) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = stringResource(Res.string.streams_pinned_source),
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        letterSpacing = 0.1.sp,
                    ),
                    color = contentColor,
                    maxLines = 1,
                )
            }
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
