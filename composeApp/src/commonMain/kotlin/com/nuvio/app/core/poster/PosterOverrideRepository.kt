package com.nuvio.app.core.poster

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import com.nuvio.app.features.streams.BingeGroupCacheStorage
import kotlin.concurrent.Volatile

/**
 * Posters picked by the user with a long press ("Change poster"). The poster URL shown before is
 * mapped to the chosen one, so every screen that loads that poster shows the new image. Kept per profile.
 */
object PosterOverrideRepository {
    private const val KEY = "poster_override_v1"

    @Volatile
    private var map: Map<String, String>? = null

    private fun current(): Map<String, String> {
        map?.let { return it }
        val loaded = runCatching { BingeGroupCacheStorage.load(KEY) }.getOrNull()
            ?.lineSequence()
            ?.mapNotNull { line ->
                val from = line.substringBefore('\t', "").takeIf { it.isNotBlank() }
                val to = line.substringAfter('\t', "").takeIf { it.isNotBlank() }
                if (from != null && to != null) from to to else null
            }
            ?.toMap()
            .orEmpty()
        map = loaded
        return loaded
    }

    fun reload() {
        map = null
    }

    /** The image to load instead of [url], if the user picked another poster for it. */
    fun replacementFor(url: String): String? = current()[url]

    /** The poster URL as it was before any choice, so a new choice replaces the original. */
    fun originalOf(url: String): String =
        current().entries.firstOrNull { it.value == url }?.key ?: url

    /** Shows [chosenUrl] wherever [originalUrl] was shown; null goes back to the original poster. */
    fun set(originalUrl: String, chosenUrl: String?) {
        val updated = if (chosenUrl == null || chosenUrl == originalUrl) {
            current() - originalUrl
        } else {
            current() + (originalUrl to chosenUrl)
        }
        map = updated
        runCatching {
            BingeGroupCacheStorage.save(KEY, updated.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        }
    }
}

/** Coil interceptor that loads the poster the user picked in place of the original one. */
class PosterOverrideInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String ?: return chain.proceed()
        val replacement = PosterOverrideRepository.replacementFor(url) ?: return chain.proceed()
        return chain.withRequest(chain.request.newBuilder().data(replacement).build()).proceed()
    }
}
