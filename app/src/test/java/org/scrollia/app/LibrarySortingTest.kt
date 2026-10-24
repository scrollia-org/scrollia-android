package org.scrollia.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.scrollia.app.database.library.LibraryItem
import org.scrollia.app.database.progress.ProgressData
import org.scrollia.app.ui.screens.library.viewmodels.LibrarySort
import org.scrollia.app.ui.screens.library.viewmodels.LibrarySorting
import java.util.Locale

class LibrarySortingTest {

    private fun book(id: Int, title: String, createdAt: Long) = LibraryItem(
        bookId = id, title = title, authors = "", filePath = "", createdAt = createdAt
    ).apply { this.id = id }

    private val items = listOf(
        book(1, "Zadig", createdAt = 100),
        book(2, "Ética", createdAt = 300),
        book(3, "Emma", createdAt = 200),
        book(4, "Árbol", createdAt = 400),
    )

    private val progress = mapOf(
        1 to ProgressData(libraryItemId = 1, lastChapterIndex = 0, lastReadTime = 50),
        3 to ProgressData(libraryItemId = 3, lastChapterIndex = 0, lastReadTime = 90),
    )

    @Test
    fun `recently read puts read books first, then newest downloads`() {
        val sorted = LibrarySorting.sort(items, progress, LibrarySort.RECENTLY_READ)
        assertThat(sorted.map { it.id }).containsExactly(3, 1, 4, 2).inOrder()
    }

    @Test
    fun `title sort is accent aware`() {
        val sorted = LibrarySorting.sort(items, progress, LibrarySort.TITLE, Locale("es"))
        assertThat(sorted.map { it.title }).containsExactly("Árbol", "Emma", "Ética", "Zadig")
            .inOrder()
    }

    @Test
    fun `unknown stored sort falls back to recently read`() {
        assertThat(LibrarySort.getById("nope")).isEqualTo(LibrarySort.RECENTLY_READ)
    }
}
