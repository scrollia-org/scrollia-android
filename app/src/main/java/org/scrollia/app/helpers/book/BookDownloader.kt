/**
 * Copyright (c) [2022 - Present] Stɑrry Shivɑm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scrollia.app.helpers.book

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.scrollia.app.api.models.Book
import org.scrollia.app.database.library.LibraryDao
import org.scrollia.app.database.library.LibraryItem

class BookDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryDao: LibraryDao
) {
    companion object {
        private const val TAG = "BookDownloader"
        const val BOOKS_FOLDER = "ebooks"
        const val TEMP_FOLDER = "temp_books"
        private const val MAX_FILENAME_LENGTH = 100
        private const val DOWNLOAD_PREFS = "scrollia_active_downloads"
        private const val DOWNLOAD_RECORDS_KEY = "records"
        private const val POLL_INTERVAL_MS = 500L

        fun createFileName(title: String, identity: String): String {
            val sanitizedTitle = title
                .replace(":", ";")
                .replace("\"", "")
                .replace("/", "／")
                .replace("\\", "＼")
                .replace("?", "")
                .replace("*", "")
                .replace("<", "")
                .replace(">", "")
                .replace("|", "")
                .trim()
                .replace("\\s+".toRegex(), "_")
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(identity.toByteArray(Charsets.UTF_8))
            val hash = hashBytes.joinToString("") { "%02x".format(it) }.take(10)
            val extension = "_$hash.epub"
            val allowedTitleLength = MAX_FILENAME_LENGTH - extension.length
            val safeTitle = sanitizedTitle.ifEmpty { "unknown" }.take(allowedTitleLength)
            return "$safeTitle$extension"
        }

        fun createImportedFileName(title: String): String =
            createFileName(title, UUID.randomUUID().toString())

        internal fun copyAndPublishDownloadedFile(
            downloadFile: File,
            booksFolder: File,
            filename: String
        ): File {
            if (!booksFolder.exists() && !booksFolder.mkdirs()) {
                throw IllegalStateException("Could not create books folder")
            }
            val stagingFile = File(booksFolder, "$filename.part")
            try {
                downloadFile.inputStream().use { input ->
                    stagingFile.outputStream().use { output -> input.copyTo(output) }
                }
                return publishDownloadedFile(stagingFile, booksFolder, filename)
            } finally {
                stagingFile.delete()
            }
        }

        internal fun publishDownloadedFile(tempFile: File, booksFolder: File, filename: String): File {
            if (!booksFolder.exists() && !booksFolder.mkdirs()) {
                throw IllegalStateException("Could not create books folder")
            }
            val bookFile = File(booksFolder, filename)
            if (bookFile.exists()) throw IllegalStateException("Refusing to overwrite an existing book")
            if (!tempFile.renameTo(bookFile)) {
                throw IllegalStateException("Could not publish downloaded book atomically")
            }
            return bookFile
        }
    }

    @Serializable
    private data class PersistedDownload(
        val downloadId: Long,
        val bookId: Int,
        val title: String,
        val authors: String,
        val filename: String,
        val createdAt: Long
    )

    data class DownloadInfo(
        val downloadId: Long,
        val status: Int = DownloadManager.STATUS_PENDING,
        val progress: Float = 0f
    )

    private data class DownloadSnapshot(val status: Int, val progress: Float)

    private val json = Json { ignoreUnknownKeys = true }
    private val preferences = context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
    private val downloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val monitorJobs = ConcurrentHashMap<Int, Job>()
    private val _downloads = MutableStateFlow<Map<Int, BookDownloader.DownloadInfo>>(emptyMap())
    val downloads = _downloads.asStateFlow()

    init {
        reconcileDownloads()
    }

    fun downloadBook(book: Book) {
        val existing = synchronized(lock) { _downloads.value[book.id] }
        if (existing != null && isActive(existing.status)) return

        val downloadUri = book.formats.applicationepubzip?.takeIf { it.isNotBlank() }
        if (downloadUri == null) {
            publishStatus(book.id, DownloadInfo(-1, DownloadManager.STATUS_FAILED))
            return
        }

        val filename = createFileName(book.title, "edition:${book.id}")
        val finalFile = File(File(context.filesDir, BOOKS_FOLDER), filename)
        val record = PersistedDownload(
            downloadId = -1,
            bookId = book.id,
            title = book.title,
            authors = BookUtils.getAuthorsAsString(book.authors),
            filename = filename,
            createdAt = System.currentTimeMillis()
        )
        if (finalFile.exists()) {
            downloadScope.launch { completeDownload(record, finalFile) }
            return
        }

        val tempFolder = temporaryFolder()
        if (!tempFolder.exists() && !tempFolder.mkdirs()) {
            publishStatus(book.id, DownloadInfo(-1, DownloadManager.STATUS_FAILED))
            return
        }
        val tempFile = File(tempFolder, filename)
        tempFile.delete()

        try {
            val request = DownloadManager.Request(Uri.parse(downloadUri))
                .setTitle(book.title)
                .setDescription(record.authors)
                .setDestinationUri(Uri.fromFile(tempFile))
                .setAllowedOverRoaming(true)
                .setAllowedOverMetered(true)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val downloadId = downloadManager.enqueue(request)
            val persisted = record.copy(downloadId = downloadId)
            saveRecord(persisted)
            publishStatus(book.id, DownloadInfo(downloadId))
            monitor(persisted)
        } catch (exc: Exception) {
            Log.e(TAG, "Could not enqueue ${book.title}", exc)
            tempFile.delete()
            publishStatus(book.id, DownloadInfo(-1, DownloadManager.STATUS_FAILED))
        }
    }

    private fun reconcileDownloads() {
        val records = readRecords()
        cleanupTemporaryFiles(records.map { it.filename }.toSet())
        records.forEach { record ->
            when (val snapshot = queryDownload(record.downloadId)) {
                null -> failDownload(record, removeFromManager = false)
                else -> when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> downloadScope.launch {
                        runCatching { finalizeDownload(record) }
                            .onFailure {
                                Log.e(TAG, "Could not recover ${record.title}", it)
                                failDownload(record)
                            }
                    }
                    DownloadManager.STATUS_FAILED -> failDownload(record)
                    else -> {
                        publishStatus(
                            record.bookId,
                            DownloadInfo(record.downloadId, snapshot.status, snapshot.progress)
                        )
                        monitor(record)
                    }
                }
            }
        }
    }

    private fun monitor(record: PersistedDownload) {
        synchronized(lock) {
            val current = _downloads.value[record.bookId]
            if (current != null && current.downloadId != record.downloadId && isActive(current.status)) {
                return
            }
            if (monitorJobs[record.bookId]?.isActive == true) return
            monitorJobs[record.bookId] = downloadScope.launch {
                while (true) {
                    if (findRecord(record.downloadId) == null) break
                    val snapshot = queryDownload(record.downloadId)
                    if (snapshot == null) {
                        if (findRecord(record.downloadId) != null) failDownload(record, removeFromManager = false)
                        break
                    }
                    publishStatus(
                        record.bookId,
                        DownloadInfo(record.downloadId, snapshot.status, snapshot.progress)
                    )
                    when (snapshot.status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            try {
                                finalizeDownload(record)
                            } catch (exc: Exception) {
                                Log.e(TAG, "Could not finish ${record.title}", exc)
                                failDownload(record)
                            }
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            failDownload(record, removeFromManager = false)
                            break
                        }
                        else -> delay(POLL_INTERVAL_MS)
                    }
                }
                monitorJobs.remove(record.bookId)
            }
        }
    }

    private fun finalizeDownload(record: PersistedDownload) {
        val tempFile = File(temporaryFolder(), record.filename)
        val booksFolder = File(context.filesDir, BOOKS_FOLDER)
        val finalFile = File(booksFolder, record.filename)
        val published = when {
            finalFile.exists() -> {
                tempFile.delete()
                finalFile
            }
            tempFile.exists() -> copyAndPublishDownloadedFile(tempFile, booksFolder, record.filename)
            else -> throw IllegalStateException("Downloaded file is missing")
        }
        completeDownload(record, published)
        removeRecord(record.downloadId)
        downloadManager.remove(record.downloadId)
        publishStatus(
            record.bookId,
            DownloadInfo(record.downloadId, DownloadManager.STATUS_SUCCESSFUL, 1f)
        )
        downloadScope.launch {
            delay(POLL_INTERVAL_MS)
            removeStatus(record.bookId, record.downloadId)
        }
    }

    private fun completeDownload(record: PersistedDownload, file: File): LibraryItem {
        libraryDao.getItemByBookId(record.bookId)?.let { return it }
        val item = LibraryItem(
            bookId = record.bookId,
            title = record.title,
            authors = record.authors,
            filePath = file.absolutePath,
            createdAt = record.createdAt
        )
        libraryDao.insert(item)
        return libraryDao.getItemByBookId(record.bookId) ?: item
    }

    private fun failDownload(record: PersistedDownload, removeFromManager: Boolean = true) {
        removeRecord(record.downloadId)
        if (removeFromManager && record.downloadId >= 0) downloadManager.remove(record.downloadId)
        File(temporaryFolder(), record.filename).delete()
        publishStatus(
            record.bookId,
            DownloadInfo(record.downloadId, DownloadManager.STATUS_FAILED)
        )
    }

    fun cancelDownload(downloadId: Long?) {
        if (downloadId == null) return
        val record = findRecord(downloadId)
        val bookId = record?.bookId
            ?: _downloads.value.entries.firstOrNull { it.value.downloadId == downloadId }?.key
        if (bookId != null) monitorJobs.remove(bookId)?.cancel()
        downloadManager.remove(downloadId)
        if (record != null) {
            removeRecord(downloadId)
            File(temporaryFolder(), record.filename).delete()
        }
        if (bookId != null) removeStatus(bookId, downloadId)
    }

    fun isBookCurrentlyDownloading(bookId: Int): Boolean =
        _downloads.value[bookId]?.let { isActive(it.status) } == true

    fun getRunningDownload(bookId: Int): DownloadInfo? = _downloads.value[bookId]

    private fun isActive(status: Int) = status == DownloadManager.STATUS_PENDING ||
            status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED

    private fun publishStatus(bookId: Int, info: DownloadInfo) {
        synchronized(lock) { _downloads.value = _downloads.value + (bookId to info) }
    }

    private fun removeStatus(bookId: Int, downloadId: Long) {
        synchronized(lock) {
            if (_downloads.value[bookId]?.downloadId == downloadId) {
                _downloads.value = _downloads.value - bookId
            }
        }
    }

    @SuppressLint("Range")
    private fun queryDownload(downloadId: Long): DownloadSnapshot? {
        if (downloadId < 0) return null
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            val total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val downloaded = cursor.getLong(
                cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val progress = when {
                status == DownloadManager.STATUS_SUCCESSFUL -> 1f
                total > 0 -> (downloaded.toFloat() / total).coerceIn(0f, 1f)
                else -> 0f
            }
            return DownloadSnapshot(status, progress)
        }
    }

    private fun readRecords(): List<PersistedDownload> =
        preferences.getStringSet(DOWNLOAD_RECORDS_KEY, emptySet()).orEmpty().mapNotNull { encoded ->
            runCatching { json.decodeFromString<PersistedDownload>(encoded) }.getOrNull()
        }

    private fun findRecord(downloadId: Long): PersistedDownload? =
        readRecords().firstOrNull { it.downloadId == downloadId }

    private fun saveRecord(record: PersistedDownload) = synchronized(lock) {
        val records = readRecords().filterNot { it.bookId == record.bookId } + record
        writeRecords(records)
    }

    private fun removeRecord(downloadId: Long) = synchronized(lock) {
        writeRecords(readRecords().filterNot { it.downloadId == downloadId })
    }

    private fun writeRecords(records: List<PersistedDownload>) {
        val encoded = records.map { json.encodeToString(PersistedDownload.serializer(), it) }.toSet()
        preferences.edit().putStringSet(DOWNLOAD_RECORDS_KEY, encoded).commit()
    }

    private fun temporaryFolder(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, TEMP_FOLDER)

    private fun cleanupTemporaryFiles(activeFilenames: Set<String>) {
        temporaryFolder().listFiles()?.forEach { file ->
            if (file.isFile && file.name !in activeFilenames) file.delete()
        }
        File(context.filesDir, BOOKS_FOLDER).listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".part")) file.delete()
        }
    }
}
