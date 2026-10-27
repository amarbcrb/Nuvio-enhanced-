package com.nuvio.app.features.home

private val releaseYearRegex = Regex("""(?:19|20)\d{2}""")

/**
 * Remembers which titles were already shown so the same film or series coming from several sources
 * (addons, Kitsu, CloudStream...) appears only once. Titles match on their name, ignoring case and
 * punctuation; two titles with the same name but different known years are kept apart (remakes).
 */
internal class HomeTitleRegistry {
    private val yearsByTitle = mutableMapOf<String, MutableSet<Int?>>()

    /** Returns true the first time a title is seen, false for later duplicates. */
    fun claim(item: MetaPreview): Boolean {
        val key = item.name.lowercase().filter(Char::isLetterOrDigit)
        if (key.isEmpty()) return true
        val year = item.releaseInfo?.let { releaseYearRegex.find(it)?.value?.toIntOrNull() }
        val seenYears = yearsByTitle[key]
        if (seenYears == null) {
            yearsByTitle[key] = mutableSetOf(year)
            return true
        }
        if (year == null || null in seenYears || year in seenYears) return false
        seenYears += year
        return true
    }
}

/** Keeps each title in the first row that shows it and drops rows left empty. */
internal fun List<HomeCatalogSection>.withoutDuplicateTitles(): List<HomeCatalogSection> {
    val registry = HomeTitleRegistry()
    return mapNotNull { section ->
        val items = section.items.filter(registry::claim)
        when {
            items.isEmpty() -> null
            items.size == section.items.size -> section
            else -> section.copy(items = items)
        }
    }
}

internal fun List<MetaPreview>.withoutDuplicateItemTitles(): List<MetaPreview> {
    val registry = HomeTitleRegistry()
    return filter(registry::claim)
}
