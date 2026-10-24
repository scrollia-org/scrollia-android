package org.scrollia.app.ui.screens.reader.main.viewmodel

import org.scrollia.app.helpers.book.BookImporter

/**
 * An EPUB opened from another app becomes a library book before it is read (DESIGN.md §19):
 * the reader asks first, shows the copy's progress, and offers retry on failure.
 */
sealed class ExternalImport {
    /** File name reported by the provider, if any. */
    abstract val displayName: String?

    data class Confirm(override val displayName: String?) : ExternalImport()

    /** [progress] in 0..1, or null when the provider does not report the size. */
    data class Importing(override val displayName: String?, val progress: Float?) : ExternalImport()

    data class Failed(
        override val displayName: String?,
        val reason: BookImporter.Reason
    ) : ExternalImport()
}
