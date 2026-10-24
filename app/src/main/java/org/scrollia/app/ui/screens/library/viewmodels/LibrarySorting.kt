package org.scrollia.app.ui.screens.library.viewmodels

import org.scrollia.app.database.library.LibraryItem
import org.scrollia.app.database.progress.ProgressData
import java.text.Collator
import java.util.Locale

enum class LibrarySort(val id: String) {
    RECENTLY_READ("recent"),
    TITLE("title");

    companion object {
        fun getById(id: String?) = entries.find { it.id == id } ?: RECENTLY_READ
    }
}

object LibrarySorting {

    /**
     * [LibrarySort.RECENTLY_READ]: books with progress by last read time (newest first), then
     * books never opened by date added (newest first). [LibrarySort.TITLE]: locale-aware A–Z,
     * so accented titles sort next to their base letter.
     */
    fun sort(
        items: List<LibraryItem>,
        progress: Map<Int, ProgressData>,
        sort: LibrarySort,
        locale: Locale = Locale.getDefault()
    ): List<LibraryItem> = when (sort) {
        LibrarySort.RECENTLY_READ -> {
            val (read, unread) = items.partition { progress.containsKey(it.id) }
            read.sortedByDescending { progress.getValue(it.id).lastReadTime } +
                    unread.sortedByDescending { it.createdAt }
        }

        LibrarySort.TITLE -> {
            val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
            items.sortedWith { a, b -> collator.compare(a.title, b.title) }
        }
    }
}
