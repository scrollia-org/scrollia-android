package org.scrollia.app.ui.screens.home.viewmodels

import org.scrollia.app.database.library.LibraryItem
import org.scrollia.app.database.progress.ProgressData
import java.io.File

/** One card of Home's "Continue reading" rail. */
data class ContinueReadingItem(
    val libraryItemId: Int,
    val title: String,
    val authors: String,
    val coverFile: File?,
    val progressPercent: Int,
    val progress: Float,
)

object ContinueReading {

    /**
     * Books in progress, most recently read first. Finished books leave the rail (there is
     * nothing left to continue) and progress rows whose library item is gone are ignored.
     */
    fun inProgress(
        library: List<LibraryItem>,
        progress: List<ProgressData>
    ): List<Pair<LibraryItem, ProgressData>> {
        val byId = library.associateBy { it.id }
        return progress
            .filter { !it.finished }
            .mapNotNull { p -> byId[p.libraryItemId]?.let { it to p } }
            .sortedByDescending { (_, p) -> p.lastReadTime }
    }
}
