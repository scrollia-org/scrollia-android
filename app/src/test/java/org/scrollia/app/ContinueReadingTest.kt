package org.scrollia.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.scrollia.app.database.library.LibraryItem
import org.scrollia.app.database.progress.ProgressData
import org.scrollia.app.ui.screens.home.viewmodels.ContinueReading

class ContinueReadingTest {

    private fun book(id: Int) = LibraryItem(
        bookId = id, title = "Book $id", authors = "A", filePath = "/b$id.epub", createdAt = 0
    ).apply { this.id = id }

    private fun progress(id: Int, time: Long, finished: Boolean = false) = ProgressData(
        libraryItemId = id, lastChapterIndex = 1, lastReadTime = time, finished = finished
    )

    @Test
    fun `orders by last read, drops finished and orphan rows`() {
        val rows = ContinueReading.inProgress(
            library = listOf(book(1), book(2), book(3)),
            progress = listOf(
                progress(1, time = 10),
                progress(2, time = 30),
                progress(3, time = 20, finished = true),
                progress(9, time = 40),
            )
        )
        assertThat(rows.map { it.first.id }).containsExactly(2, 1).inOrder()
    }

    @Test
    fun `empty library gives an empty rail`() {
        assertThat(ContinueReading.inProgress(emptyList(), listOf(progress(1, 1)))).isEmpty()
    }
}
