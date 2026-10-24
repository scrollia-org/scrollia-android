package org.scrollia.app.helpers.book

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.scrollia.app.database.library.LibraryDao
import org.scrollia.app.database.library.LibraryItem
import org.scrollia.app.epub.EpubParser

/**
 * Copies a user's EPUB (any `content://` provider, any stream) into app-owned storage and
 * registers it as an imported library book. Shared by the Library import and by EPUBs opened
 * from other apps.
 *
 * Guarantees: the stream is copied to a uniquely named `.part` file next to the books (left-over
 * parts are swept at startup by [BookDownloader]); the copy is parsed as an EPUB before it is
 * published by an atomic rename under a fresh UUID-based name ([BookDownloader.createImportedFileName]),
 * so nothing is ever overwritten; the library row is inserted last. Any failure or cancellation
 * removes whatever was created, so no row points at a partial or missing file.
 */
@Singleton
class BookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryDao: LibraryDao,
    private val epubParser: EpubParser
) {
    enum class Reason {
        /** The source could not be opened or read (missing, revoked, I/O error). */
        UNREADABLE,

        /** The bytes are not an EPUB the reader can parse. */
        NOT_EPUB,

        /** Writing or publishing the copy failed (e.g. no space). */
        STORAGE,

        /** The library row could not be written. */
        DATABASE
    }

    class ImportException(val reason: Reason, cause: Throwable? = null) :
        Exception(reason.name, cause)

    data class ParsedBook(val title: String, val author: String)

    /** Display name of a document, if its provider reports one. */
    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Import [uri] and return the new library item id. [onProgress] gets a fraction in 0..1
     * when the provider reports the size, otherwise null.
     */
    suspend fun importUri(uri: Uri, onProgress: (Float?) -> Unit = {}): Int =
        withContext(Dispatchers.IO) {
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (exc: Exception) {
                if (exc is CancellationException) throw exc
                throw ImportException(Reason.UNREADABLE, exc)
            } ?: throw ImportException(Reason.UNREADABLE)
            input.use {
                importStream(
                    input = it,
                    totalBytes = sizeOf(uri),
                    booksFolder = File(context.filesDir, BookDownloader.BOOKS_FOLDER),
                    parse = { file ->
                        val book = epubParser.createEpubBook(file.absolutePath, shouldUseToc = false)
                        ParsedBook(book.title, book.author)
                    },
                    register = { book, file ->
                        libraryDao.insert(
                            LibraryItem(
                                bookId = 0,
                                title = book.title,
                                authors = book.author,
                                filePath = file.absolutePath,
                                createdAt = System.currentTimeMillis(),
                                isImported = true
                            )
                        ).toInt()
                    },
                    unregister = ::removeRow,
                    onProgress = onProgress
                )
            }
        }

    /** Undo an import that could not be opened: its row and its owned file. */
    suspend fun rollback(libraryItemId: Int) = withContext(NonCancellable + Dispatchers.IO) {
        val item = libraryDao.getItemById(libraryItemId) ?: return@withContext
        if (!item.isImported) return@withContext // never delete a downloaded book
        libraryDao.delete(item)
        File(item.filePath).delete()
    }

    private fun removeRow(libraryItemId: Int) {
        libraryDao.getItemById(libraryItemId)?.let { libraryDao.delete(it) }
    }

    private fun sizeOf(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }.getOrNull()?.takeIf { it > 0 }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024

        /**
         * The import itself, independent of Android: copy, verify, publish, register. Each step
         * cleans up after itself on failure or cancellation; see the class documentation.
         */
        internal suspend fun importStream(
            input: InputStream,
            totalBytes: Long?,
            booksFolder: File,
            parse: suspend (File) -> ParsedBook,
            register: (ParsedBook, File) -> Int,
            unregister: (Int) -> Unit,
            onProgress: (Float?) -> Unit = {},
            publish: (File, File, String) -> File = BookDownloader::publishDownloadedFile
        ): Int {
            if (!booksFolder.exists() && !booksFolder.mkdirs()) {
                throw ImportException(Reason.STORAGE)
            }
            val staging = File(booksFolder, "import-${UUID.randomUUID()}.epub.part")
            var published: File? = null
            try {
                copy(input, staging, totalBytes, onProgress)

                val book = try {
                    parse(staging)
                } catch (exc: Exception) {
                    if (exc is CancellationException) throw exc
                    throw ImportException(Reason.NOT_EPUB, exc)
                }
                currentCoroutineContext().ensureActive()

                val filename = BookDownloader.createImportedFileName(book.title)
                val file = try {
                    publish(staging, booksFolder, filename)
                } catch (exc: Exception) {
                    throw ImportException(Reason.STORAGE, exc)
                }
                published = file

                val id = withContext(NonCancellable) {
                    try {
                        register(book, file)
                    } catch (exc: Exception) {
                        throw ImportException(Reason.DATABASE, exc)
                    }
                }
                // Cancelled while the row was being written: undo it as well.
                try {
                    currentCoroutineContext().ensureActive()
                } catch (exc: CancellationException) {
                    withContext(NonCancellable) { unregister(id) }
                    throw exc
                }
                published = null
                return id
            } finally {
                staging.delete()
                published?.delete()
            }
        }

        private suspend fun copy(
            input: InputStream,
            staging: File,
            totalBytes: Long?,
            onProgress: (Float?) -> Unit
        ) {
            onProgress(if (totalBytes != null) 0f else null)
            val output = try {
                staging.outputStream()
            } catch (exc: IOException) {
                throw ImportException(Reason.STORAGE, exc)
            }
            output.use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = try {
                        input.read(buffer)
                    } catch (exc: IOException) {
                        throw ImportException(Reason.UNREADABLE, exc)
                    }
                    if (read < 0) break
                    try {
                        out.write(buffer, 0, read)
                    } catch (exc: IOException) {
                        throw ImportException(Reason.STORAGE, exc)
                    }
                    copied += read
                    if (totalBytes != null) onProgress((copied.toFloat() / totalBytes).coerceIn(0f, 1f))
                }
            }
            if (staging.length() == 0L) throw ImportException(Reason.NOT_EPUB)
        }
    }
}
