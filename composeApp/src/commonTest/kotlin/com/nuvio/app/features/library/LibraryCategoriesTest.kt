package com.nuvio.app.features.library

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryCategoriesTest {
    private val titles = LibraryCategoryTitles(favorites = "Favorites", anime = "Anime", movies = "Movies", series = "Series")

    private fun item(id: String, type: String, saved: Long, genres: List<String> = emptyList()) =
        LibraryItem(id = id, type = type, name = id, genres = genres, savedAtEpochMs = saved)

    @Test
    fun `sections come as favorites anime movies series then the user's categories`() {
        val aot = item("aot", "series", 5, genres = listOf("Animation", "Action"))
        val dune = item("dune", "movie", 4)
        val got = item("got", "series", 3)
        val state = LibraryCategoriesState(
            favorites = setOf(dune.categoryKey()),
            categories = listOf(LibraryCategory(id = "c1", name = "Weekend", itemKeys = setOf(got.categoryKey()))),
        )

        val sections = buildLocalLibraryCategories(listOf(got, dune, aot), state, titles)

        assertEquals(
            listOf(
                LIBRARY_FAVORITES_SECTION to listOf("dune"),
                LIBRARY_ANIME_SECTION to listOf("aot"),
                "movie" to listOf("dune"),
                "series" to listOf("got"),
                LIBRARY_CATEGORY_SECTION_PREFIX + "c1" to listOf("got"),
            ),
            sections.map { section -> section.type to section.items.map { it.id } },
        )
        assertEquals("Weekend", sections.last().displayTitle)
    }

    @Test
    fun `a title put in or taken out of a built-in section by hand moves with it`() {
        val dune = item("dune", "movie", 2)
        val got = item("got", "series", 1)
        val state = LibraryCategoriesState(
            addedToSection = mapOf(LIBRARY_ANIME_SECTION to setOf(got.categoryKey())),
            removedFromSection = mapOf("movie" to setOf(dune.categoryKey())),
        )

        val sections = buildLocalLibraryCategories(listOf(got, dune), state, titles)

        assertEquals(
            listOf(LIBRARY_ANIME_SECTION to listOf("got"), "series" to listOf("got")),
            sections.map { section -> section.type to section.items.map { it.id } },
        )
    }

    @Test
    fun `empty categories are not shown`() {
        val sections = buildLocalLibraryCategories(
            items = listOf(item("dune", "movie", 1)),
            state = LibraryCategoriesState(categories = listOf(LibraryCategory(id = "c1", name = "Empty"))),
            titles = titles,
        )

        assertEquals(listOf("movie"), sections.map { it.type })
    }
}
