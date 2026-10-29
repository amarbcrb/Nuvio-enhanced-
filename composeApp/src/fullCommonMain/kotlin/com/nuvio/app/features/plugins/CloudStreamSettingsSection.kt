package com.nuvio.app.features.plugins

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioDropdownField
import com.nuvio.app.core.ui.NuvioSelectOption
import com.nuvio.app.core.ui.NuvioIconActionButton
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.features.cloudstream.AddCloudStreamRepositoryResult
import com.nuvio.app.features.cloudstream.CloudStreamBulkInstallResult
import com.nuvio.app.features.cloudstream.CloudStreamInstallResult
import com.nuvio.app.features.cloudstream.CloudStreamPlatformSupport
import com.nuvio.app.features.cloudstream.CloudStreamPluginItem
import com.nuvio.app.features.cloudstream.CloudStreamRepository
import com.nuvio.app.features.cloudstream.CloudStreamRepositoryItem
import com.nuvio.app.features.cloudstream.CloudStreamSite
import com.nuvio.app.features.cloudstream.normalizeCloudStreamSiteUrl
import com.nuvio.app.features.settings.AppLanguage
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.streams.BingeGroupCacheStorage
import com.nuvio.app.features.streams.PinnedStreamSourcesRepository
import com.nuvio.app.features.streams.cloudStreamAddonId
import com.nuvio.app.features.streams.streamLanguageDisplayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Filters and plugin list of the CloudStream page, kept outside composition so the page header
 * and the lazily composed plugin cards share them.
 */
internal object CloudStreamSettingsListState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collecting = false

    var query by mutableStateOf("")
    var selectedLanguage by mutableStateOf<String?>(null)
    var selectedType by mutableStateOf<String?>(null)
    var installFilter by mutableStateOf(CloudStreamInstallFilter.All)
    var message by mutableStateOf<String?>(null)
    var plugins by mutableStateOf<List<CloudStreamPluginItem>>(emptyList())
        private set
    var securityWarningAccepted by mutableStateOf(false)
        private set
    var repositories by mutableStateOf<List<CloudStreamRepositoryItem>>(emptyList())
        private set
    var repositoryUrl by mutableStateOf("")
    var editingRepositoryUrl by mutableStateOf<String?>(null)
    var expandedRepositories by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Extensions not installed that the user removed from the list. */
    var hiddenPluginIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Extensions listed on the page: installed ones, and the others unless they were removed. */
    val shownPlugins: List<CloudStreamPluginItem> by derivedStateOf {
        plugins.filter { it.isInstalled || it.metadata.id.value !in hiddenPluginIds }
    }

    /** Removes every extension not installed from the list; returns how many were removed. */
    fun hideNotInstalled(): Int {
        val ids = shownPlugins.filterNot { it.isInstalled }.map { it.metadata.id.value }
        if (ids.isEmpty()) return 0
        hiddenPluginIds = hiddenPluginIds + ids
        persistHidden()
        return ids.size
    }

    fun showHidden() {
        hiddenPluginIds = emptySet()
        persistHidden()
    }

    /**
     * Lists again the removed extensions of the repository [repositoryUrl] and returns the ids of
     * its extensions to install or switch back on (not installed, outdated or disabled).
     */
    fun restoreRepository(repositoryUrl: String): List<String> {
        val repositoryPlugins = plugins.filter { it.metadata.repositoryManifestUrl == repositoryUrl }
        val ids = repositoryPlugins.map { it.metadata.id.value }.toSet()
        if (hiddenPluginIds.any { it in ids }) {
            hiddenPluginIds = hiddenPluginIds - ids
            persistHidden()
        }
        return repositoryPlugins
            .filter { item ->
                !item.isInstalling &&
                    item.metadata.status.canInstall &&
                    item.compatibility.isRunnable &&
                    (!item.isRunnable || item.hasUpdate || !item.isInstalled)
            }
            .map { it.metadata.id.value }
    }

    private fun persistHidden() {
        if (hiddenPluginIds.isEmpty()) {
            BingeGroupCacheStorage.remove(HIDDEN_PLUGINS_KEY)
        } else {
            BingeGroupCacheStorage.save(HIDDEN_PLUGINS_KEY, hiddenPluginIds.joinToString(HIDDEN_PLUGINS_SEPARATOR))
        }
    }

    private const val HIDDEN_PLUGINS_KEY = "cloudstream_hidden_plugins"
    private const val HIDDEN_PLUGINS_SEPARATOR = "\n"

    private val hasActiveFilter: Boolean
        get() = query.isNotBlank() || selectedLanguage != null || selectedType != null ||
            installFilter != CloudStreamInstallFilter.All

    /**
     * Extensions grouped by the repository they come from, like in CloudStream. While a filter is
     * active, repositories without a matching extension are left out.
     */
    val pluginGroups: List<CloudStreamPluginGroup> by derivedStateOf {
        val visibleByRepository = visiblePlugins.groupBy { it.metadata.repositoryManifestUrl }
        val allByRepository = shownPlugins.groupBy { it.metadata.repositoryManifestUrl }
        val known = repositories.map { repository ->
            val url = repository.manifest.sourceUrl
            CloudStreamPluginGroup(
                key = url,
                repository = repository,
                plugins = visibleByRepository[url].orEmpty(),
                allPlugins = allByRepository[url].orEmpty(),
            )
        }
        val knownUrls = repositories.map { it.manifest.sourceUrl }.toSet()
        val orphans = visiblePlugins.filter { it.metadata.repositoryManifestUrl !in knownUrls }
        val groups = known + listOfNotNull(
            orphans.takeIf { it.isNotEmpty() }?.let {
                CloudStreamPluginGroup(key = "", repository = null, plugins = it, allPlugins = it)
            },
        )
        if (hasActiveFilter) groups.filter { it.plugins.isNotEmpty() } else groups
    }

    /** Extensions of a repository are listed once it is opened, or right away while filtering. */
    fun isExpanded(key: String): Boolean = hasActiveFilter || key in expandedRepositories

    fun toggleExpanded(key: String) {
        expandedRepositories = if (key in expandedRepositories) expandedRepositories - key else expandedRepositories + key
    }

    val visiblePlugins: List<CloudStreamPluginItem> by derivedStateOf {
        val normalizedQuery = query.trim().lowercase()
        shownPlugins.filter { item ->
            (normalizedQuery.isBlank() || listOf(
                item.metadata.name,
                item.metadata.internalName,
                item.metadata.description.orEmpty(),
                item.metadata.authors.joinToString(" "),
            ).any { it.lowercase().contains(normalizedQuery) }) &&
                (selectedLanguage == null || item.metadata.language == selectedLanguage) &&
                (selectedType == null || selectedType in item.metadata.rawTvTypes) &&
                when (installFilter) {
                    CloudStreamInstallFilter.All -> true
                    CloudStreamInstallFilter.Installed -> item.isInstalled
                    CloudStreamInstallFilter.NotInstalled -> !item.isInstalled
                }
        }
    }

    /** Result of the last extension check, by plugin id. */
    var health by mutableStateOf<Map<String, CloudStreamHealth>>(emptyMap())
        private set
    var isCheckingHealth by mutableStateOf(false)
        private set

    /** Plugins the last check found not responding that are still installed. */
    val failingPlugins: List<CloudStreamPluginItem> by derivedStateOf {
        plugins.filter { it.isInstalled && health[it.metadata.id.value] is CloudStreamHealth.Failing }
    }

    // Extensions can block their thread and ignore cancellation, so each check runs detached and is
    // abandoned after its timeout instead of holding the whole check back.
    private val checkScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Asks every enabled extension for its home page, or a search when it has none. */
    fun checkExtensions() {
        if (isCheckingHealth) return
        val targets = plugins.filter(CloudStreamPluginItem::isRunnable)
        if (targets.isEmpty()) return
        isCheckingHealth = true
        health = targets.associate { it.metadata.id.value to CloudStreamHealth.Checking }
        scope.launch {
            val permits = Semaphore(HEALTH_CHECK_CONCURRENCY)
            targets.map { plugin ->
                launch {
                    val id = plugin.metadata.id.value
                    val result = permits.withPermit { checkExtension(id) }
                    health = health + (id to result)
                }
            }.joinAll()
            isCheckingHealth = false
        }
    }

    private suspend fun checkExtension(pluginId: String): CloudStreamHealth {
        val mainPage = detachedWithTimeout { CloudStreamRepository.getMainPage(pluginId, 1) }
        if (mainPage?.getOrNull()?.any { (_, items) -> items.isNotEmpty() } == true) return CloudStreamHealth.Working
        val search = detachedWithTimeout {
            CloudStreamRepository.search(HEALTH_CHECK_QUERY, pluginId).firstOrNull()
        }
        if (search?.getOrNull()?.isNotEmpty() == true) return CloudStreamHealth.Working
        val reason = search?.exceptionOrNull()?.message
            ?: mainPage?.exceptionOrNull()?.message
            ?: if (mainPage == null && search == null) "Timeout" else null
        return CloudStreamHealth.Failing(reason)
    }

    private suspend fun <T> detachedWithTimeout(block: suspend () -> T): T? {
        val task = checkScope.async { block() }
        return withTimeoutOrNull(HEALTH_CHECK_TIMEOUT_MS) { task.await() }
            .also { if (it == null) task.cancel() }
    }

    fun disableFailing(): Int {
        val failing = failingPlugins.filter { it.enabled }
        failing.forEach { CloudStreamRepository.setPluginEnabled(it.metadata.id.value, false) }
        return failing.size
    }

    fun removeFailing(): Int {
        val failing = failingPlugins
        failing.forEach { CloudStreamRepository.removePlugin(it.metadata.id.value) }
        health = health - failing.map { it.metadata.id.value }.toSet()
        return failing.size
    }

    private const val HEALTH_CHECK_CONCURRENCY = 4
    private const val HEALTH_CHECK_TIMEOUT_MS = 25_000L
    private const val HEALTH_CHECK_QUERY = "the"

    fun ensureCollecting() {
        if (collecting) return
        collecting = true
        hiddenPluginIds = BingeGroupCacheStorage.load(HIDDEN_PLUGINS_KEY)
            ?.split(HIDDEN_PLUGINS_SEPARATOR)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            .orEmpty()
        CloudStreamRepository.initialize()
        scope.launch {
            CloudStreamRepository.uiState.collect { state ->
                plugins = state.plugins
                repositories = state.repositories
                securityWarningAccepted = state.securityWarningAccepted
            }
        }
    }
}

internal data class CloudStreamPluginGroup(
    val key: String,
    val repository: CloudStreamRepositoryItem?,
    /** Extensions of the repository that match the filters. */
    val plugins: List<CloudStreamPluginItem>,
    val allPlugins: List<CloudStreamPluginItem>,
)

internal sealed interface CloudStreamHealth {
    data object Checking : CloudStreamHealth
    data object Working : CloudStreamHealth
    data class Failing(val reason: String?) : CloudStreamHealth
}

/** One plugin card of the CloudStream page, composed only while it is on screen. */
@Composable
internal fun CloudStreamPluginListItem(plugin: CloudStreamPluginItem) {
    val selectedAppLanguage by ThemeSettingsRepository.selectedAppLanguage.collectAsStateWithLifecycle()
    val pinnedSourceIdList by remember {
        PinnedStreamSourcesRepository.ensureLoaded()
        PinnedStreamSourcesRepository.pinnedSourceIds
    }.collectAsStateWithLifecycle()
    val copy = remember(selectedAppLanguage) { CloudStreamSettingsCopy.forLanguage(selectedAppLanguage) }
    val pinnedSourceIds = remember(pinnedSourceIdList) { pinnedSourceIdList.toSet() }
    CloudStreamPluginCard(
        plugin = plugin,
        securityWarningAccepted = CloudStreamSettingsListState.securityWarningAccepted,
        pinnedSourceIds = pinnedSourceIds,
        copy = copy,
        onMessage = { CloudStreamSettingsListState.message = it },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CloudStreamSettingsSection() {
    LaunchedEffect(Unit) {
        ThemeSettingsRepository.ensureLoaded()
        CloudStreamRepository.initialize()
    }
    val state by CloudStreamRepository.uiState.collectAsStateWithLifecycle()
    val selectedAppLanguage by ThemeSettingsRepository.selectedAppLanguage.collectAsStateWithLifecycle()
    val copy = remember(selectedAppLanguage) { CloudStreamSettingsCopy.forLanguage(selectedAppLanguage) }
    val scope = rememberCoroutineScope()
    var repositoryUrl by CloudStreamSettingsListState::repositoryUrl
    var editingRepositoryUrl by CloudStreamSettingsListState::editingRepositoryUrl
    var isAddingRepository by remember { mutableStateOf(false) }
    var query by CloudStreamSettingsListState::query
    var selectedLanguage by CloudStreamSettingsListState::selectedLanguage
    var selectedType by CloudStreamSettingsListState::selectedType
    var installFilter by CloudStreamSettingsListState::installFilter
    var message by CloudStreamSettingsListState::message
    var bulkInstallMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isBulkInstalling by remember { mutableStateOf(false) }

    val languages = remember(state.plugins) {
        state.plugins.mapNotNull { it.metadata.language }.distinct().sorted()
    }
    val types = remember(state.plugins) {
        state.plugins.flatMap { it.metadata.rawTvTypes }.distinct().sorted()
    }
    val visiblePlugins = CloudStreamSettingsListState.visiblePlugins
    val bulkActionPlugins = remember(visiblePlugins) {
        visiblePlugins.filter { item ->
            !item.isInstalling &&
                item.metadata.status.canInstall &&
                item.compatibility.isRunnable &&
                (!item.isRunnable || item.hasUpdate || !item.isInstalled || !item.verified)
        }
    }

    NuvioSectionLabel(copy.sectionTitle)
    NuvioSurfaceCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            CloudStreamStat(state.repositories.size, copy.statRepositories, Modifier.weight(1f))
            CloudStreamStat(state.plugins.count { it.isInstalled }, copy.statInstalled, Modifier.weight(1f))
            CloudStreamStat(state.plugins.count { it.isRunnable }, copy.statEnabled, Modifier.weight(1f))
        }
    }

    if (!state.securityWarningAccepted) {
        NuvioSurfaceCard {
            Text(
                text = copy.securityWarningTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = copy.securityWarningBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            NuvioPrimaryButton(
                text = copy.acceptSecurityWarning,
                onClick = CloudStreamRepository::acceptSecurityWarning,
            )
        }
    }

    NuvioSurfaceCard {
        Text(
            text = if (editingRepositoryUrl == null) copy.addRepositoryTitle else copy.editRepositoryTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        NuvioInputField(
            value = repositoryUrl,
            onValueChange = {
                repositoryUrl = it
                message = null
            },
            placeholder = "https://github.com/Kraptor123/cs-kraptor",
        )
        Spacer(Modifier.height(12.dp))
        NuvioPrimaryButton(
            text = when {
                isAddingRepository -> copy.repositoryLoading
                editingRepositoryUrl != null -> copy.applyRepositoryChange
                else -> copy.addRepository
            },
            enabled = repositoryUrl.isNotBlank() && !isAddingRepository,
            onClick = {
                val requested = repositoryUrl.trim()
                val previous = editingRepositoryUrl
                isAddingRepository = true
                message = null
                scope.launch {
                    if (previous != null && requested == previous) {
                        CloudStreamRepository.refreshRepository(previous)
                        message = copy.repositoryRefreshing
                    } else {
                        when (val result = CloudStreamRepository.addRepository(requested)) {
                            is AddCloudStreamRepositoryResult.Success -> {
                                previous?.let(CloudStreamRepository::removeRepository)
                                repositoryUrl = ""
                                editingRepositoryUrl = null
                                message = copy.repositoryAdded(result.repository.name)
                            }
                            is AddCloudStreamRepositoryResult.Error -> message = result.message
                        }
                    }
                    isAddingRepository = false
                }
            },
        )
        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (state.plugins.isNotEmpty()) {
        NuvioSectionLabel(copy.providersSectionTitle)
        NuvioSurfaceCard {
            Text(copy.searchAndFilterTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            NuvioInputField(
                value = query,
                onValueChange = { query = it },
                placeholder = copy.searchPlaceholder,
            )
            Spacer(Modifier.height(10.dp))
            NuvioDropdownField(
                label = copy.installationLabel,
                valueLabel = copy.installFilterLabel(installFilter),
                options = CloudStreamInstallFilter.entries.map { NuvioSelectOption(it.name, copy.installFilterLabel(it)) },
                selectedKeys = setOf(installFilter.name),
                onSelect = { option -> installFilter = CloudStreamInstallFilter.valueOf(option.key) },
            )
            Spacer(Modifier.height(8.dp))
            NuvioDropdownField(
                label = copy.languageLabel,
                valueLabel = selectedLanguage?.let(::streamLanguageDisplayName) ?: copy.allLanguages,
                options = listOf(NuvioSelectOption("", copy.allLanguages)) +
                    languages
                        .map { NuvioSelectOption(it, streamLanguageDisplayName(it)) }
                        .sortedBy { it.label.lowercase() },
                selectedKeys = setOf(selectedLanguage.orEmpty()),
                onSelect = { option -> selectedLanguage = option.key.ifEmpty { null } },
            )
            if (types.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                NuvioDropdownField(
                    label = copy.typeLabel,
                    valueLabel = selectedType ?: copy.allTypes,
                    options = listOf(NuvioSelectOption("", copy.allTypes)) +
                        types.map { NuvioSelectOption(it, it) },
                    selectedKeys = setOf(selectedType.orEmpty()),
                    onSelect = { option -> selectedType = option.key.ifEmpty { null } },
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = state.securityWarningAccepted && !isBulkInstalling && bulkActionPlugins.isNotEmpty(),
                onClick = {
                    val targetIds = bulkActionPlugins.map { it.metadata.id.value }
                    isBulkInstalling = true
                    bulkInstallMessage = null
                    message = null
                    scope.launch {
                        bulkInstallMessage = runCatching {
                            CloudStreamRepository.installAndEnablePlugins(targetIds).toUserMessage(copy)
                        }.getOrElse { error ->
                            error.message ?: copy.bulkInstallFailed
                        }
                        isBulkInstalling = false
                    }
                },
            ) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        isBulkInstalling -> copy.bulkInstalling
                        bulkActionPlugins.isEmpty() -> copy.allInstalledAndActive
                        else -> copy.installAndEnableAll(bulkActionPlugins.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            bulkInstallMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val notInstalledCount = CloudStreamSettingsListState.shownPlugins.count { !it.isInstalled }
            if (notInstalledCount > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { message = copy.notInstalledHidden(CloudStreamSettingsListState.hideNotInstalled()) },
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(copy.hideNotInstalled(notInstalledCount), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val hiddenCount = CloudStreamSettingsListState.hiddenPluginIds.size
            if (hiddenCount > 0) {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = CloudStreamSettingsListState::showHidden,
                ) {
                    Text(copy.restoreHidden(hiddenCount), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        CloudStreamHealthCard(
            enabledCount = state.plugins.count { it.isRunnable },
            copy = copy,
            onMessage = { message = it },
        )
    }
}

@Composable
private fun CloudStreamStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A repository with its extension counts; opening it lists its extensions below. */
@Composable
internal fun CloudStreamRepositoryGroupCard(group: CloudStreamPluginGroup) {
    val selectedAppLanguage by ThemeSettingsRepository.selectedAppLanguage.collectAsStateWithLifecycle()
    val copy = remember(selectedAppLanguage) { CloudStreamSettingsCopy.forLanguage(selectedAppLanguage) }
    val listState = CloudStreamSettingsListState
    val expanded = listState.isExpanded(group.key)
    val repository = group.repository
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var isRestoring by remember(group.key) { mutableStateOf(false) }
    var repositoryMessage by remember(group.key) { mutableStateOf<String?>(null) }
    NuvioSurfaceCard(modifier = Modifier.clickable { listState.toggleExpanded(group.key) }) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    repository?.manifest?.name ?: copy.otherExtensions,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    copy.repositoryPluginCounts(
                        total = group.allPlugins.size,
                        installed = group.allPlugins.count { it.isInstalled },
                        enabled = group.allPlugins.count { it.isRunnable },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                repository?.errorMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
            )
        }
        if (repository != null && expanded) {
            val repositoryUrl = repository.manifest.sourceUrl
            Spacer(Modifier.height(4.dp))
            Text(
                repositoryUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(repositoryUrl))
                        repositoryMessage = copy.repositoryLinkCopied
                    },
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = copy.copyRepositoryLink)
                }
                IconButton(
                    enabled = !isRestoring,
                    onClick = {
                        val ids = listState.restoreRepository(repositoryUrl)
                        if (ids.isEmpty()) {
                            repositoryMessage = copy.repositoryRestored
                        } else {
                            isRestoring = true
                            repositoryMessage = copy.repositoryRestoring
                            scope.launch {
                                repositoryMessage = runCatching {
                                    CloudStreamRepository.installAndEnablePlugins(ids).toUserMessage(copy)
                                }.getOrElse { error -> error.message ?: copy.bulkInstallFailed }
                                isRestoring = false
                            }
                        }
                    },
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = copy.restoreRepository)
                }
                IconButton(
                    onClick = {
                        listState.repositoryUrl = repository.manifest.sourceUrl
                        listState.editingRepositoryUrl = repository.manifest.sourceUrl
                    },
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = copy.editRepositoryContentDescription)
                }
                IconButton(onClick = { CloudStreamRepository.refreshRepository(repository.manifest.sourceUrl) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = copy.refreshRepositoryContentDescription)
                }
                IconButton(onClick = { CloudStreamRepository.removeRepository(repository.manifest.sourceUrl) }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = copy.removeRepositoryContentDescription,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            repositoryMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Checks which enabled extensions still answer and offers to switch off or remove the others. */
@Composable
private fun CloudStreamHealthCard(
    enabledCount: Int,
    copy: CloudStreamSettingsCopy,
    onMessage: (String) -> Unit,
) {
    val listState = CloudStreamSettingsListState
    val results = listState.health.values
    val checked = results.count { it !is CloudStreamHealth.Checking }
    val working = results.count { it is CloudStreamHealth.Working }
    val failing = listState.failingPlugins
    NuvioSurfaceCard {
        Text(copy.healthTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            copy.healthDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        NuvioPrimaryButton(
            text = when {
                listState.isCheckingHealth -> copy.healthChecking(checked, results.size)
                else -> copy.healthCheck(enabledCount)
            },
            enabled = !listState.isCheckingHealth && enabledCount > 0,
            onClick = listState::checkExtensions,
        )
        if (results.isNotEmpty() && !listState.isCheckingHealth) {
            Spacer(Modifier.height(10.dp))
            Text(
                copy.healthSummary(working = working, failing = results.size - working),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (failing.isNotEmpty() && !listState.isCheckingHealth) {
            Spacer(Modifier.height(4.dp))
            Text(
                failing.joinToString { it.metadata.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onMessage(copy.healthDisabled(listState.disableFailing())) },
                ) {
                    Text(copy.healthDisableFailing, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onMessage(copy.healthRemoved(listState.removeFailing())) },
                ) {
                    Text(copy.healthRemoveFailing, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun CloudStreamBulkInstallResult.toUserMessage(copy: CloudStreamSettingsCopy): String {
    if (requestedCount == 0) return copy.noPluginsToInstall
    val enabledText = copy.enabledPluginCount(enabledCount)
    val installText = if (installedCount > 0) copy.installedOrUpdatedCount(installedCount) else ""
    val skippedText = if (skippedCount > 0) copy.pendingOperationCount(skippedCount) else ""
    val failureText = failures.firstOrNull()?.let { first ->
        copy.skippedFailureCount(failures.size, first.pluginName, first.message.localizedCloudStreamFailure(copy))
    }.orEmpty()
    return when {
        enabledCount > 0 -> "$enabledText$installText$skippedText$failureText."
        failures.isNotEmpty() -> {
            val first = failures.first()
            copy.noPluginEnabled(first.pluginName, first.message.localizedCloudStreamFailure(copy))
        }
        skippedCount > 0 -> copy.onlyPendingOperations(skippedCount)
        else -> copy.noPluginsToInstall
    }
}

@Composable
private fun CloudStreamPluginCard(
    plugin: CloudStreamPluginItem,
    securityWarningAccepted: Boolean,
    pinnedSourceIds: Set<String>,
    copy: CloudStreamSettingsCopy,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val sourceId = remember(plugin.metadata.id.value) { cloudStreamAddonId(plugin.metadata.id.value) }
    val isPinnedSource = sourceId in pinnedSourceIds
    var showSites by rememberSaveable(plugin.metadata.id.value) { mutableStateOf(false) }
    val canInstall = securityWarningAccepted && !plugin.isInstalling
    fun installOrUpdate() {
        if (!canInstall) return
        scope.launch {
            val result = if (plugin.hasUpdate) {
                CloudStreamRepository.updatePlugin(plugin.metadata.id.value)
            } else {
                CloudStreamRepository.installPlugin(plugin.metadata.id.value)
            }
            onMessage(
                when (result) {
                    is CloudStreamInstallResult.Success -> copy.pluginInstalled(result.plugin.metadata.name)
                    is CloudStreamInstallResult.Error -> result.message.localizedCloudStreamFailure(copy)
                },
            )
        }
    }
    // Name and language, then the actions: copy link, pin, install or update, remove, on/off.
    NuvioSurfaceCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = plugin.metadata.iconUrl,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plugin.metadata.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    plugin.metadata.language?.let(::streamLanguageDisplayName) ?: copy.unknownLanguage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Switch(
                checked = plugin.enabled,
                enabled = plugin.isInstalled && plugin.verified && plugin.compatibility.isRunnable,
                onCheckedChange = { CloudStreamRepository.setPluginEnabled(plugin.metadata.id.value, it) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NuvioIconActionButton(
                icon = Icons.Rounded.ContentCopy,
                contentDescription = copy.copyLink,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {
                    clipboardManager.setText(AnnotatedString(plugin.metadata.packageUrl))
                    onMessage(copy.linkCopied(plugin.metadata.name))
                },
            )
            if (plugin.isRunnable) {
                NuvioIconActionButton(
                    icon = Icons.Rounded.Link,
                    contentDescription = copy.siteAddressTitle,
                    tint = if (showSites || plugin.siteUrlOverrides.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = { showSites = !showSites },
                )
            }
            NuvioIconActionButton(
                icon = Icons.Rounded.PushPin,
                contentDescription = if (isPinnedSource) copy.unpinSource else copy.pinSource,
                tint = if (isPinnedSource) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                onClick = {
                    PinnedStreamSourcesRepository.setPinned(sourceId, !isPinnedSource)
                },
            )
            if (!plugin.isInstalled || plugin.hasUpdate) {
                NuvioIconActionButton(
                    icon = if (plugin.hasUpdate) Icons.Rounded.Refresh else Icons.Rounded.CloudDownload,
                    contentDescription = when {
                        plugin.isInstalling -> copy.downloading
                        plugin.hasUpdate -> copy.update
                        else -> copy.install
                    },
                    tint = if (canInstall) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    onClick = { installOrUpdate() },
                )
            }
            if (plugin.isInstalled) {
                NuvioIconActionButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = copy.remove,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        CloudStreamRepository.removePlugin(plugin.metadata.id.value)
                        onMessage(copy.pluginRemoved(plugin.metadata.name))
                    },
                )
            }
        }
        if (CloudStreamSettingsListState.health[plugin.metadata.id.value] is CloudStreamHealth.Failing) {
            Text(copy.healthBadgeFailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        plugin.errorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (showSites && plugin.isRunnable) {
            CloudStreamSiteUrlEditor(plugin = plugin, copy = copy, onMessage = onMessage)
        }
    }
}

/** Lets the user point an extension at the new address of a site that moved. */
@Composable
private fun CloudStreamSiteUrlEditor(
    plugin: CloudStreamPluginItem,
    copy: CloudStreamSettingsCopy,
    onMessage: (String) -> Unit,
) {
    val pluginId = plugin.metadata.id.value
    var sites by remember(pluginId) { mutableStateOf<List<CloudStreamSite>?>(null) }
    var loadError by remember(pluginId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pluginId, plugin.siteUrlOverrides) {
        CloudStreamRepository.sites(pluginId)
            .onSuccess {
                sites = it
                loadError = null
            }
            .onFailure { loadError = it.message ?: copy.siteLoadFailed }
    }
    Spacer(Modifier.height(12.dp))
    Text(copy.siteAddressTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(
        copy.siteAddressDescription,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    val loadedSites = sites
    when {
        loadError != null -> Text(
            loadError.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        loadedSites == null -> Text(copy.siteLoading, style = MaterialTheme.typography.bodySmall)
        loadedSites.isEmpty() -> Text(copy.noSites, style = MaterialTheme.typography.bodySmall)
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            loadedSites.forEach { site ->
                CloudStreamSiteUrlRow(pluginId = pluginId, site = site, copy = copy, onMessage = onMessage)
            }
        }
    }
}

@Composable
private fun CloudStreamSiteUrlRow(
    pluginId: String,
    site: CloudStreamSite,
    copy: CloudStreamSettingsCopy,
    onMessage: (String) -> Unit,
) {
    var input by remember(site.name, site.url) { mutableStateOf(site.url) }
    val isChanged = site.url.trimEnd('/') != site.defaultUrl.trimEnd('/')
    Column {
        Text(site.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (isChanged) {
            Text(
                copy.originalSiteUrl(site.defaultUrl),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        NuvioInputField(
            value = input,
            onValueChange = { input = it },
            placeholder = site.defaultUrl,
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = input.trim().trimEnd('/') != site.url.trimEnd('/'),
                onClick = {
                    val normalized = normalizeCloudStreamSiteUrl(input)
                    if (normalized == null) {
                        onMessage(copy.invalidSiteUrl)
                    } else {
                        val override = normalized.takeIf { it != site.defaultUrl.trimEnd('/') }
                        CloudStreamRepository.setSiteUrl(pluginId, site.name, override)
                        onMessage(copy.siteUrlSaved(site.name))
                    }
                },
            ) {
                Text(copy.saveSiteUrl)
            }
            if (isChanged) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        CloudStreamRepository.setSiteUrl(pluginId, site.name, null)
                        onMessage(copy.siteUrlRestored(site.name))
                    },
                ) {
                    Text(copy.restoreSiteUrl)
                }
            }
        }
    }
}

private class CloudStreamSettingsCopy private constructor(
    private val turkish: Boolean,
    private val french: Boolean,
) {
    private fun text(english: String, french: String, turkish: String): String = when {
        this.turkish -> turkish
        this.french -> french
        else -> english
    }

    val installationLabel: String = text(english = "Installation", french = "Installation", turkish = "Kurulum")
    val languageLabel: String = text(english = "Language", french = "Langue", turkish = "Dil")
    val typeLabel: String = text(english = "Type", french = "Type", turkish = "Tür")
    val otherExtensions: String = text(english = "Other extensions", french = "Autres extensions", turkish = "Diğer eklentiler")

    fun repositoryPluginCounts(total: Int, installed: Int, enabled: Int): String = text(
        english = "$total extensions · $installed installed · $enabled enabled",
        french = "$total extensions · $installed installées · $enabled activées",
        turkish = "$total eklenti · $installed kurulu · $enabled etkin",
    )

    val healthTitle: String = text(
        english = "Check extensions",
        french = "Vérifier les extensions",
        turkish = "Eklentileri kontrol et",
    )
    val healthDescription: String = text(
        english = "Asks every enabled extension for its home page (or a search) to find the ones that no longer answer.",
        french = "Interroge chaque extension activée (page d'accueil ou recherche) pour trouver celles qui ne répondent plus.",
        turkish = "Artık yanıt vermeyenleri bulmak için etkin her eklentiden ana sayfasını (veya bir aramayı) ister.",
    )
    val healthDisableFailing: String = text(english = "Disable them", french = "Les désactiver", turkish = "Devre dışı bırak")
    val healthRemoveFailing: String = text(english = "Remove them", french = "Les supprimer", turkish = "Kaldır")
    val healthBadgeChecking: String = text(english = "Checking...", french = "Test en cours...", turkish = "Kontrol ediliyor...")
    val healthBadgeWorking: String = text(english = "Works", french = "Fonctionne", turkish = "Çalışıyor")
    val healthBadgeFailing: String = text(english = "Not responding", french = "Ne répond pas", turkish = "Yanıt vermiyor")
    val copyLink: String = text(english = "Copy extension link", french = "Copier le lien de l'extension", turkish = "Eklenti bağlantısını kopyala")
    val copyRepositoryLink: String = text(english = "Copy repository link", french = "Copier le lien du dépôt", turkish = "Depo bağlantısını kopyala")
    val repositoryLinkCopied: String = text(english = "Repository link copied.", french = "Lien du dépôt copié.", turkish = "Depo bağlantısı kopyalandı.")
    val restoreRepository: String = text(
        english = "Reinstall every extension of this repository",
        french = "Réinstaller toutes les extensions du dépôt",
        turkish = "Bu deponun tüm eklentilerini yeniden kur",
    )
    val repositoryRestoring: String = text(english = "Reinstalling...", french = "Réinstallation...", turkish = "Yeniden kuruluyor...")
    val repositoryRestored: String = text(
        english = "Every extension of this repository is listed, installed and enabled.",
        french = "Toutes les extensions du dépôt sont affichées, installées et activées.",
        turkish = "Bu deponun tüm eklentileri listelendi, kuruldu ve etkin.",
    )
    val unknownLanguage: String = text(english = "Language not specified", french = "Langue non indiquée", turkish = "Dil belirtilmemiş")
    val statRepositories: String = text(english = "Repositories", french = "Dépôts", turkish = "Depolar")
    val statInstalled: String = text(english = "Installed", french = "Installées", turkish = "Kurulu")
    val statEnabled: String = text(english = "Enabled", french = "Activées", turkish = "Etkin")

    fun linkCopied(name: String): String = text(
        english = "Link of $name copied.",
        french = "Lien de $name copié.",
        turkish = "$name bağlantısı kopyalandı.",
    )

    fun hideNotInstalled(count: Int): String = text(
        english = "Remove the $count extensions not installed",
        french = "Supprimer les $count extensions non installées",
        turkish = "Kurulu olmayan $count eklentiyi kaldır",
    )

    fun notInstalledHidden(count: Int): String = text(
        english = "$count extensions not installed removed from the list.",
        french = "$count extensions non installées retirées de la liste.",
        turkish = "Kurulu olmayan $count eklenti listeden kaldırıldı.",
    )

    fun restoreHidden(count: Int): String = text(
        english = "Show the $count removed extensions again",
        french = "Réafficher les $count extensions retirées",
        turkish = "Kaldırılan $count eklentiyi yeniden göster",
    )

    fun healthCheck(count: Int): String = text(
        english = "Check the $count enabled extensions",
        french = "Tester les $count extensions activées",
        turkish = "Etkin $count eklentiyi kontrol et",
    )

    fun healthChecking(done: Int, total: Int): String = text(
        english = "Checking... $done/$total",
        french = "Test en cours... $done/$total",
        turkish = "Kontrol ediliyor... $done/$total",
    )

    fun healthSummary(working: Int, failing: Int): String = text(
        english = "$working work, $failing do not respond.",
        french = "$working fonctionnent, $failing ne répondent pas.",
        turkish = "$working çalışıyor, $failing yanıt vermiyor.",
    )

    fun healthDisabled(count: Int): String = text(
        english = "$count extensions disabled.",
        french = "$count extensions désactivées.",
        turkish = "$count eklenti devre dışı bırakıldı.",
    )

    fun healthRemoved(count: Int): String = text(
        english = "$count extensions removed.",
        french = "$count extensions supprimées.",
        turkish = "$count eklenti kaldırıldı.",
    )

    val siteAddressTitle: String = text(
        english = "Site address",
        french = "Adresse du site",
        turkish = "Site adresi",
    )
    val siteAddressDescription: String = text(
        english = "If the site moved to a new address, enter it here. Restore the original address when the extension is updated.",
        french = "Si le site a changé d'adresse, saisis la nouvelle ici. Remets l'adresse d'origine quand l'extension est mise à jour.",
        turkish = "Site yeni bir adrese taşındıysa buraya girin. Eklenti güncellendiğinde orijinal adresi geri yükleyin.",
    )
    val siteLoading: String = text(
        english = "Loading the extension...",
        french = "Chargement de l'extension...",
        turkish = "Eklenti yükleniyor...",
    )
    val siteLoadFailed: String = text(
        english = "The extension could not be loaded.",
        french = "L'extension n'a pas pu être chargée.",
        turkish = "Eklenti yüklenemedi.",
    )
    val noSites: String = text(
        english = "This extension does not expose a site address.",
        french = "Cette extension n'indique pas d'adresse de site.",
        turkish = "Bu eklenti bir site adresi sunmuyor.",
    )
    val invalidSiteUrl: String = text(
        english = "Enter a web address such as https://example.com",
        french = "Saisis une adresse web, par exemple https://exemple.com",
        turkish = "https://ornek.com gibi bir web adresi girin",
    )
    val saveSiteUrl: String = text(english = "Save", french = "Enregistrer", turkish = "Kaydet")
    val restoreSiteUrl: String = text(english = "Restore original", french = "Adresse d'origine", turkish = "Orijinale dön")

    fun originalSiteUrl(url: String): String = text(
        english = "Original: $url",
        french = "D'origine : $url",
        turkish = "Orijinal: $url",
    )

    fun siteUrlSaved(name: String): String = text(
        english = "$name now uses the new address.",
        french = "$name utilise maintenant la nouvelle adresse.",
        turkish = "$name artık yeni adresi kullanıyor.",
    )

    fun siteUrlRestored(name: String): String = text(
        english = "$name uses its original address again.",
        french = "$name utilise de nouveau son adresse d'origine.",
        turkish = "$name yeniden orijinal adresini kullanıyor.",
    )

    val sectionTitle: String =
        if (turkish) "CloudStream / CS3" else "CloudStream / CS3"
    val repositoriesSectionTitle: String =
        if (turkish) "CS3 repositoryleri" else "CS3 repositories"
    val providersSectionTitle: String =
        if (turkish) "CS3 providerları" else "CS3 providers"
    val securityWarningTitle: String =
        if (turkish) "Üçüncü taraf kod güvenlik uyarısı" else "Third-party code security warning"
    val securityWarningBody: String =
        if (turkish) {
            "Repository ve eklentiler Nuvio tarafından yönetilmez. Yalnızca güvendiğiniz kaynakları ekleyin. Paket denetlenmeden provider etkinleştirilemez."
        } else {
            "Repositories and plugins are not managed by Nuvio. Only add sources you trust. A provider cannot be enabled until its package is checked."
        }
    val acceptSecurityWarning: String =
        if (turkish) "Uyarıyı okudum ve kabul ediyorum" else "I have read and accept the warning"
    val addRepositoryTitle: String =
        if (turkish) "CloudStream repository ekle" else "Add CloudStream repository"
    val editRepositoryTitle: String =
        if (turkish) "Repository bağlantısını düzenle" else "Edit repository link"
    val repositoryLoading: String =
        if (turkish) "Repository okunuyor…" else "Reading repository..."
    val applyRepositoryChange: String =
        if (turkish) "Değişikliği uygula" else "Apply changes"
    val addRepository: String =
        if (turkish) "Repository ekle" else "Add repository"
    val repositoryRefreshing: String =
        if (turkish) "Repository yenileniyor." else "Refreshing repository."
    val editRepositoryContentDescription: String =
        if (turkish) "Repository düzenle" else "Edit repository"
    val refreshRepositoryContentDescription: String =
        if (turkish) "Repository yenile" else "Refresh repository"
    val removeRepositoryContentDescription: String =
        if (turkish) "Repository kaldır" else "Remove repository"
    val searchAndFilterTitle: String =
        if (turkish) "Eklenti ara ve filtrele" else "Search and filter plugins"
    val searchPlaceholder: String =
        if (turkish) "Eklenti, açıklama veya yazar ara" else "Search plugin, description, or author"
    val allLanguages: String =
        if (turkish) "Tüm diller" else "All languages"
    val allTypes: String =
        if (turkish) "Tüm türler" else "All types"

    fun installFilterLabel(filter: CloudStreamInstallFilter): String = when (filter) {
        CloudStreamInstallFilter.All -> if (turkish) "Tümü" else "All"
        CloudStreamInstallFilter.Installed -> if (turkish) "Kurulu" else "Installed"
        CloudStreamInstallFilter.NotInstalled -> if (turkish) "Kurulu değil" else "Not installed"
    }

    fun pluginRemoved(name: String): String =
        if (turkish) "$name kaldırıldı" else "$name removed"
    val bulkInstallFailed: String =
        if (turkish) "Toplu kurulum tamamlanamadı." else "Bulk install could not be completed."
    val bulkInstalling: String =
        if (turkish) "Toplu kurulum yapılıyor…" else "Bulk install in progress..."
    val allInstalledAndActive: String =
        if (turkish) "Tümü kurulu ve aktif" else "Everything is installed and active"
    val noPluginsToInstall: String =
        if (turkish) "Kurulacak eklenti bulunamadı." else "No plugins to install."
    val unknownAuthor: String =
        if (turkish) "Yazar belirtilmemiş" else "Author not specified"
    val noDescription: String =
        if (turkish) "Açıklama yok." else "No description."
    val sha256Verified: String =
        if (turkish) "SHA-256 doğrulandı" else "SHA-256 verified"
    val hashUnverified: String =
        if (turkish) "Hash doğrulanmadı" else "Hash unverified"
    val packageChecked: String =
        if (turkish) "Paket denetlendi" else "Package checked"
    val packageUnchecked: String =
        if (turkish) "Paket denetlenmedi" else "Package unchecked"
    val androidAndIosCompatible: String =
        if (turkish) "Android + iOS uyumlu" else "Android + iOS compatible"
    val androidCompatible: String =
        if (turkish) "Android uyumlu" else "Android compatible"
    val runtimeIncompatible: String =
        if (turkish) "Runtime uyumsuz" else "Runtime incompatible"
    val downloading: String =
        if (turkish) "İndiriliyor…" else "Downloading..."
    val update: String =
        if (turkish) "Güncelle" else "Update"
    val reinstall: String =
        if (turkish) "Yeniden kur" else "Reinstall"
    val install: String =
        if (turkish) "Kur" else "Install"
    val remove: String =
        if (turkish) "Kaldır" else "Remove"
    val pinSource: String =
        if (turkish) "Kaynağı sabitle" else "Pin source"
    val unpinSource: String =
        if (turkish) "Kaynak sabitlemesini kaldır" else "Unpin source"
    val acceptSecurityWarningFailure: String =
        if (turkish) "Üçüncü taraf eklenti güvenlik uyarısını kabul edin." else "Accept the third-party plugin security warning."
    val pluginNotFoundFailure: String =
        if (turkish) "Eklenti bulunamadı." else "Plugin was not found."
    val pluginDownFailure: String =
        if (turkish) "Bu eklenti şu anda kapalı görünüyor." else "This plugin appears to be down."
    val packageEnableFailure: String =
        if (turkish) {
            "Paket kuruldu ama doğrulama/uyumluluk şartları nedeniyle aktif edilemedi."
        } else {
            "The package was installed, but could not be enabled because verification or compatibility checks failed."
        }

    fun repositoryCount(count: Int): String =
        if (turkish) "$count repository" else "$count repositories"

    fun installedCount(count: Int): String =
        if (turkish) "$count kurulu" else "$count installed"

    fun enabledProviderCount(count: Int): String =
        if (turkish) "$count etkin provider" else "$count enabled providers"

    fun repositoryAdded(name: String): String =
        if (turkish) "$name eklendi." else "$name added."

    fun installAndEnableAll(count: Int): String =
        if (turkish) "Tümünü kur ve aktif et ($count)" else "Install and enable all ($count)"

    fun enabledPluginCount(count: Int): String =
        if (turkish) "$count eklenti aktif edildi" else "$count plugins enabled"

    fun installedOrUpdatedCount(count: Int): String =
        if (turkish) ", $count paket kuruldu/güncellendi" else ", $count packages installed/updated"

    fun pendingOperationCount(count: Int): String =
        if (turkish) ", $count işlem zaten sürüyor" else ", $count operations already in progress"

    fun skippedFailureCount(count: Int, pluginName: String, message: String): String =
        if (turkish) {
            ". $count eklenti atlandı: $pluginName - $message"
        } else {
            ". $count plugins skipped: $pluginName - $message"
        }

    fun noPluginEnabled(pluginName: String, message: String): String =
        if (turkish) {
            "Hiçbir eklenti aktif edilemedi: $pluginName - $message"
        } else {
            "No plugins could be enabled: $pluginName - $message"
        }

    fun onlyPendingOperations(count: Int): String =
        if (turkish) "$count işlem zaten sürüyor." else "$count operations are already in progress."

    fun currentVersion(version: Int): String =
        if (turkish) "Güncel v$version" else "Current v$version"

    fun installedVersion(version: Int): String =
        if (turkish) "Kurulu v$version" else "Installed v$version"

    fun pluginInstalled(name: String): String =
        if (turkish) "$name paketi denetlendi ve kuruldu." else "$name package was checked and installed."

    companion object {
        fun forLanguage(language: AppLanguage): CloudStreamSettingsCopy =
            CloudStreamSettingsCopy(
                turkish = language == AppLanguage.TURKISH,
                french = language == AppLanguage.FRENCH,
            )
    }
}

private fun String.localizedCloudStreamFailure(copy: CloudStreamSettingsCopy): String =
    when (this) {
        "Accept the third-party plugin security warning before installation",
        "Accept the third-party plugin security warning.",
        "Üçüncü taraf eklenti güvenlik uyarısını kabul edin." -> copy.acceptSecurityWarningFailure
        "CloudStream plugin was not found",
        "Plugin was not found.",
        "Eklenti bulunamadı." -> copy.pluginNotFoundFailure
        "This CloudStream plugin is marked as down",
        "This plugin appears to be down.",
        "Bu eklenti şu anda kapalı görünüyor." -> copy.pluginDownFailure
        "The package was installed, but could not be enabled because verification or compatibility checks failed.",
        "Paket kuruldu ama doğrulama/uyumluluk şartları nedeniyle aktif edilemedi." -> copy.packageEnableFailure
        else -> this
    }

internal enum class CloudStreamInstallFilter {
    All,
    Installed,
    NotInstalled,
}
