package com.nuvio.app.features.library

import com.nuvio.app.features.streams.BingeGroupCacheStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A category the user made for the saved Library, with the titles put in it. */
@Serializable
data class LibraryCategory(
    val id: String,
    val name: String,
    val itemKeys: Set<String> = emptySet(),
)

@Serializable
data class LibraryCategoriesState(
    val favorites: Set<String> = emptySet(),
    val categories: List<LibraryCategory> = emptyList(),
)

/** Key of a saved title in [LibraryCategoriesState], stable across saves. */
internal fun LibraryItem.categoryKey(): String = "${type.lowercase()}:$id"

/** Favorites and the user's own categories of the saved (local) Library. */
object LibraryCategoriesRepository {
    private const val STORAGE_KEY = "library_categories_v1"
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(LibraryCategoriesState())
    val state: StateFlow<LibraryCategoriesState> = _state.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _state.value = BingeGroupCacheStorage.load(STORAGE_KEY)
            ?.let { raw -> runCatching { json.decodeFromString<LibraryCategoriesState>(raw) }.getOrNull() }
            ?: LibraryCategoriesState()
    }

    fun isFavorite(item: LibraryItem): Boolean {
        ensureLoaded()
        return item.categoryKey() in _state.value.favorites
    }

    fun toggleFavorite(item: LibraryItem) = update { current ->
        val key = item.categoryKey()
        current.copy(favorites = if (key in current.favorites) current.favorites - key else current.favorites + key)
    }

    /** Adds a category named [name]; returns it, or the existing one with that name. */
    fun createCategory(name: String): LibraryCategory? {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        ensureLoaded()
        _state.value.categories.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
        val category = LibraryCategory(id = newCategoryId(), name = trimmed)
        update { current -> current.copy(categories = current.categories + category) }
        return category
    }

    fun deleteCategory(categoryId: String) = update { current ->
        current.copy(categories = current.categories.filterNot { it.id == categoryId })
    }

    fun setInCategory(categoryId: String, item: LibraryItem, inCategory: Boolean) = update { current ->
        val key = item.categoryKey()
        current.copy(
            categories = current.categories.map { category ->
                if (category.id != categoryId) {
                    category
                } else if (inCategory) {
                    category.copy(itemKeys = category.itemKeys + key)
                } else {
                    category.copy(itemKeys = category.itemKeys - key)
                }
            },
        )
    }

    private fun update(transform: (LibraryCategoriesState) -> LibraryCategoriesState) {
        ensureLoaded()
        val updated = transform(_state.value)
        if (updated == _state.value) return
        _state.value = updated
        BingeGroupCacheStorage.save(STORAGE_KEY, json.encodeToString(LibraryCategoriesState.serializer(), updated))
    }

    private fun newCategoryId(): String {
        val used = _state.value.categories.map { it.id }.toSet()
        var index = _state.value.categories.size + 1
        while ("c$index" in used) index++
        return "c$index"
    }
}

/** Titles of the built-in Library categories, in the app language. */
internal data class LibraryCategoryTitles(
    val favorites: String,
    val anime: String,
    val movies: String,
    val series: String,
)

internal const val LIBRARY_FAVORITES_SECTION = "nuvio:favorites"
internal const val LIBRARY_ANIME_SECTION = "nuvio:anime"
internal const val LIBRARY_CATEGORY_SECTION_PREFIX = "nuvio:category:"

private val seriesTypes = setOf("series", "tv")

internal fun LibraryItem.isAnimeTitle(): Boolean =
    mediaCategory.equals("anime", ignoreCase = true) ||
        type.equals("anime", ignoreCase = true) ||
        genres.any { genre -> genre.equals("anime", ignoreCase = true) || genre.equals("animation", ignoreCase = true) }

/**
 * Sections of the saved Library: Favorites, Anime, Movies, Series, other types, then the
 * user's categories. A title can be in Favorites or a category and in its kind's section.
 */
internal fun buildLocalLibraryCategories(
    items: List<LibraryItem>,
    state: LibraryCategoriesState,
    titles: LibraryCategoryTitles,
): List<LibrarySection> {
    val sorted = items.sortedByDescending { it.savedAtEpochMs }
    val result = mutableListOf<LibrarySection>()
    fun add(type: String, title: String, sectionItems: List<LibraryItem>) {
        if (sectionItems.isNotEmpty()) result += LibrarySection(type = type, displayTitle = title, items = sectionItems)
    }

    add(LIBRARY_FAVORITES_SECTION, titles.favorites, sorted.filter { it.categoryKey() in state.favorites })
    val (anime, others) = sorted.partition { it.isAnimeTitle() }
    add(LIBRARY_ANIME_SECTION, titles.anime, anime)
    add("movie", titles.movies, others.filter { it.type.equals("movie", ignoreCase = true) })
    add("series", titles.series, others.filter { it.type.lowercase() in seriesTypes })
    others
        .filterNot { it.type.equals("movie", ignoreCase = true) || it.type.lowercase() in seriesTypes }
        .groupBy { it.type }
        .forEach { (type, typeItems) ->
            add(type, type.replaceFirstChar { char -> char.titlecase() }, typeItems)
        }
    state.categories.forEach { category ->
        add(LIBRARY_CATEGORY_SECTION_PREFIX + category.id, category.name, sorted.filter { it.categoryKey() in category.itemKeys })
    }
    return result
}
