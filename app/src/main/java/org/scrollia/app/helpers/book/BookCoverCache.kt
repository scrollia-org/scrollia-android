package org.scrollia.app.helpers.book

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.scrollia.app.database.library.LibraryItem
import org.scrollia.app.epub.EpubParser
import java.io.File

/**
 * Extracts the cover of downloaded/imported books once and keeps it as a file in the cache
 * directory, so lists (Home rail, Library) can show covers offline without parsing the EPUB.
 */
class BookCoverCache(context: Context, private val epubParser: EpubParser) {

    private val coversDir = File(context.cacheDir, "library_covers")

    /** Cover file for [item], or null if the EPUB has no cover. Safe to call repeatedly. */
    suspend fun coverFile(item: LibraryItem): File? = withContext(Dispatchers.IO) {
        val file = File(coversDir, "${item.id}_${File(item.filePath).name.hashCode()}")
        val missingMarker = File(coversDir, file.name + ".none")
        when {
            file.exists() -> return@withContext file
            missingMarker.exists() -> return@withContext null
        }
        coversDir.mkdirs()
        val bytes = try {
            epubParser.peekCoverImage(item.filePath)
        } catch (exc: Exception) {
            Log.w(TAG, "Could not read cover of ${item.filePath}", exc)
            null
        }
        if (bytes == null || bytes.isEmpty()) {
            missingMarker.createNewFile()
            null
        } else {
            file.writeBytes(bytes)
            file
        }
    }

    private companion object {
        const val TAG = "BookCoverCache"
    }
}
