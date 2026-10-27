package com.nuvio.app.features.cloudstream

internal interface CloudStreamProvider {
    val id: String
    suspend fun getMainPage(page: Int): List<Pair<String, List<CloudStreamSearchItem>>>
    suspend fun search(query: String): List<CloudStreamSearchItem>
    suspend fun loadByExternalId(externalId: String): CloudStreamLoadItem? = null
    suspend fun load(data: String): CloudStreamLoadItem
    suspend fun loadLinks(data: String): List<CloudStreamPlaybackSource>

    /** Sites this provider reads, with the address each one currently uses. */
    val sites: List<CloudStreamSite>
        get() = emptyList()

    /** Points [siteName] at [url]; a null [url] restores the address shipped with the extension. */
    fun setSiteUrl(siteName: String, url: String?) = Unit
}

/** A website read by an extension; [url] differs from [defaultUrl] once the user changed it. */
data class CloudStreamSite(
    val name: String,
    val url: String,
    val defaultUrl: String,
)

/** Applies the user's site addresses, restoring the shipped address of every other site. */
internal fun CloudStreamProvider.applySiteUrlOverrides(overrides: Map<String, String>) {
    sites.forEach { site -> setSiteUrl(site.name, overrides[site.name]) }
}

/**
 * Turns what the user typed into a site address: adds https:// when missing and drops trailing
 * slashes. Returns null when the text is not a web address.
 */
fun normalizeCloudStreamSiteUrl(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val scheme = withScheme.substringBefore("://").lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = withScheme.substringAfter("://").substringBefore('/').substringBefore('?')
    if (host.isEmpty() || !host.contains('.') && !host.startsWith("localhost")) return null
    return withScheme
}

internal expect object CloudStreamPlatformRuntime {
    val supportsAndroidDex: Boolean

    fun initialize(context: Any?)
    suspend fun provider(plugin: CloudStreamPluginItem): CloudStreamProvider?
    fun unload(pluginId: String)
    fun clear()
}
