package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.updates_github_api_error
import org.jetbrains.compose.resources.getString

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
internal data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long? = null,
    @SerialName("content_type") val contentType: String? = null,
)

internal class NoChannelReleaseException : IllegalStateException()

internal const val GITHUB_API_BASE = "https://api.github.com"
internal const val GITHUB_OWNER = "amarbcrb"
internal const val GITHUB_REPO = "Nuvio-enhanced-"

/** APK names published by CI for each edition (see licence/LISEZMOI.md). */
internal const val PLUS_APK_PREFIX = "NuvioPlus"
internal const val PRO_APK_PREFIX = "NuvioPro"

internal object AppUpdaterRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun getLatestChannelUpdate(channel: UpdateChannel): Result<AppUpdate> = runCatching {
        val response = httpRequestRaw(
            method = "GET",
            url = "$GITHUB_API_BASE/repos/$GITHUB_OWNER/$GITHUB_REPO/${releasePath(channel)}",
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to "NuvioMobile",
            ),
            body = "",
        )
        currentCoroutineContext().ensureActive()
        if (response.status == 404) throw NoChannelReleaseException()
        if (response.status !in 200..299) {
            error(getString(Res.string.updates_github_api_error, response.status))
        }
        selectUpdate(response.body, channel, AppUpdaterPlatform.getSupportedAbis())
            ?: throw NoChannelReleaseException()
    }

    internal fun releasePath(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> "releases/latest"
        UpdateChannel.BETA -> "releases?per_page=100"
    }

    internal fun selectUpdate(
        responseBody: String,
        channel: UpdateChannel,
        supportedAbis: List<String>,
        edition: String = AppVersionConfig.EDITION,
    ): AppUpdate? {
        val releases = when (channel) {
            UpdateChannel.STABLE -> listOf(json.decodeFromString<GitHubReleaseDto>(responseBody))
            UpdateChannel.BETA -> json.decodeFromString<List<GitHubReleaseDto>>(responseBody)
        }
        return ReleaseSelector.eligibleReleases(releases, channel).firstNotNullOfOrNull { release ->
            val asset = chooseBestApkAsset(release.assets, supportedAbis, edition) ?: return@firstNotNullOfOrNull null
            val tag = release.tagName?.takeIf { VersionUtils.parse(it) != null }
                ?: release.name.orEmpty()
            AppUpdate(
                tag = tag,
                title = release.name?.takeIf { it.isNotBlank() } ?: tag,
                notes = release.body.orEmpty(),
                releaseUrl = release.htmlUrl,
                assetName = asset.name,
                assetUrl = asset.browserDownloadUrl,
                assetSizeBytes = asset.size,
            )
        }
    }

    private fun chooseBestApkAsset(
        assets: List<GitHubAssetDto>,
        supportedAbis: List<String>,
        edition: String,
    ): GitHubAssetDto? {
        val allApks = assets.filter { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType == "application/vnd.android.package-archive"
        }
        // A release can hold both NuvioPlus and NuvioPro: each edition only takes its own APK.
        val editionApks = allApks.filter { asset ->
            asset.name.startsWith(PLUS_APK_PREFIX, ignoreCase = true) ||
                asset.name.startsWith(PRO_APK_PREFIX, ignoreCase = true)
        }
        val ownPrefix = if (edition == "pro") PRO_APK_PREFIX else PLUS_APK_PREFIX
        val apkAssets = if (editionApks.isEmpty()) {
            allApks
        } else {
            editionApks.filter { it.name.startsWith(ownPrefix, ignoreCase = true) }
        }
        for (abi in supportedAbis) {
            apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }
        return apkAssets.firstOrNull { asset ->
            val name = asset.name.lowercase()
            name.contains("universal") || name.contains("all")
        } ?: apkAssets.firstOrNull()
    }
}
