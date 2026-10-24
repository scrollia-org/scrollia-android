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


package org.scrollia.app.ui.screens.reader.main.viewmodel

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.scrollia.app.database.library.LibraryDao
import org.scrollia.app.database.progress.ProgressDao
import org.scrollia.app.database.progress.ProgressData
import org.scrollia.app.epub.EpubParser
import org.scrollia.app.epub.models.EpubBook
import org.scrollia.app.epub.models.EpubChapter
import org.scrollia.app.helpers.PreferenceUtil
import org.scrollia.app.helpers.book.BookImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject


data class ReaderScreenState(
    // Screen state
    val isLoading: Boolean = true,
    val shouldShowLoader: Boolean = false,
    val hasError: Boolean = false,
    val showReaderMenu: Boolean = false,
    val currentChapterIndex: Int = 0,
    val currentChapter: EpubChapter = EpubChapter("", "", "", emptyList()),
    val chapterScrollPercent: Float = 0f,
    // Book data
    val epubBook: EpubBook? = null,
    val chapters: List<EpubChapter> = emptyList(),
    // Loaded chapter content map: chapterId -> ChapterContent
    val loadedChapters: Map<String, EpubParser.ChapterContent> = emptyMap(),
    // Loaded images map: imagePath -> imageData
    val loadedImages: Map<String, ByteArray> = emptyMap(),
    // Position to restore once its chapter body is loaded; null when nothing is pending.
    val pendingRestore: ReaderAnchor? = null,
    // Library item being read; progress is saved against it.
    val libraryItemId: Int? = null,
    // An EPUB from another app that is being added to the library; null otherwise.
    val externalImport: ExternalImport? = null,
    // Typography
    val fontSize: Int = 100,
    val fontFamily: ReaderFont = ReaderFont.Literata,
    val lineHeight: Float = 1.7f,
    val readerTheme: ReaderTheme = ReaderTheme.Paper,
    val justifyText: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.SCROLL,
    // Off by default: reading respects the system display timeout (DESIGN.md §19).
    val keepScreenOn: Boolean = false,
    // Paged mode: spec the pages below were measured with, and pages per chapter index.
    val pageSpec: PageSpec? = null,
    val pages: Map<Int, List<ReaderPage>> = emptyMap(),
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val libraryDao: LibraryDao,
    private val progressDao: ProgressDao,
    private val preferenceUtil: PreferenceUtil,
    private val epubParser: EpubParser,
    private val bookImporter: BookImporter,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Mutable state flow to update the state.
    private val _state = MutableStateFlow(ReaderScreenState())
    val state = _state.asStateFlow()

    // Tracking currently loading items to prevent duplicate requests.
    private val loadingChapterIds = ConcurrentHashMap.newKeySet<String>()
    private val loadingImagePaths = ConcurrentHashMap.newKeySet<String>()

    private data class ProgressUpdate(
        val libraryItemId: Int,
        val anchor: ReaderAnchor,
        val progress: Float,
        val finished: Boolean
    )

    // Progress writes go through a single conflated collector so they are applied in order.
    private val progressUpdates = MutableStateFlow<ProgressUpdate?>(null)

    // Last position reported by the UI, in either mode; used to keep the place across
    // mode switches and repagination.
    @Volatile
    private var currentAnchor: ReaderAnchor? = null

    // Paged mode.
    private val paginationRequests = MutableStateFlow<PaginationEnvironment?>(null)
    @Volatile
    private var activePagination: PaginationEnvironment? = null
    private data class PageCacheKey(val chapterIndex: Int, val spec: PageSpec)
    private val pageCache = ConcurrentHashMap<PageCacheKey, List<ReaderPage>>()
    private val paginatingKeys = ConcurrentHashMap.newKeySet<PageCacheKey>()
    private val wantedPageChapters = ConcurrentHashMap.newKeySet<Int>()
    private val imageSizes = ConcurrentHashMap<String, IntSize>()

    // External EPUB import.
    private var externalUri: Uri? = null
    private var externalName: String? = null
    private var importJob: Job? = null
    // Imported in this session and not opened successfully yet: rolled back if opening fails.
    @Volatile
    private var unopenedImportId: Int? = null

    init {
        viewModelScope.launch {
            progressUpdates.filterNotNull().conflate().flowOn(Dispatchers.Default)
                .collect { saveProgress(it) }
        }
        viewModelScope.launch {
            paginationRequests.filterNotNull()
                .distinctUntilChangedBy { it.spec }
                .collectLatest { environment ->
                    // Debounce slider drags and resizes; the first spec applies immediately.
                    if (activePagination != null) delay(PAGINATION_DEBOUNCE_MS)
                    applyPagination(environment)
                }
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    fontSize = preferenceUtil.getInt(PreferenceUtil.READER_FONT_SIZE_INT, 100),
                    fontFamily = ReaderFont.getFontById(
                        preferenceUtil.getString(
                            PreferenceUtil.READER_FONT_STYLE_STR,
                            ReaderFont.Literata.id
                        )!!
                    ),
                    lineHeight = preferenceUtil.getFloat(PreferenceUtil.READER_LINE_HEIGHT_FLOAT, 1.7f),
                    readerTheme = ReaderTheme.getById(
                        preferenceUtil.getString(
                            PreferenceUtil.READER_THEME_STR,
                            ReaderTheme.Paper.id
                        )
                    ),
                    justifyText = preferenceUtil.getBoolean(PreferenceUtil.READER_JUSTIFY_BOOL, false),
                    readingMode = ReadingMode.getById(
                        preferenceUtil.getString(PreferenceUtil.READER_MODE_STR, ReadingMode.SCROLL.id)
                    ),
                    keepScreenOn = preferenceUtil.getBoolean(
                        PreferenceUtil.READER_KEEP_SCREEN_ON_BOOL, false
                    )
                )
            }
            // Collect the state to update the current chapter.
            _state.collect {
                if (state.value.isLoading) return@collect
                if (state.value.chapters.isEmpty()) return@collect
                val currentChapter = state.value.chapters.getOrNull(state.value.currentChapterIndex)
                if (currentChapter != null && state.value.currentChapter != currentChapter) {
                    _state.update { it.copy(currentChapter = currentChapter) }
                }
            }
        }
    }

    /**
     * Load a library book. When [startChapterIndex] is given the reader opens at the start of
     * that chapter; otherwise it restores the saved anchor, if any.
     */
    fun loadEpubBook(libraryItemId: Int, startChapterIndex: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(shouldShowLoader = true, hasError = false, libraryItemId = libraryItemId)
            }
            try {
                val libraryItem = libraryDao.getItemById(libraryItemId)
                    ?: throw IllegalStateException("Library item not found")
                val readerData = progressDao.getReaderData(libraryItemId)
                val isInternalChineseBook =
                    !libraryItem.isImported && epubParser.peekLanguage(libraryItem.filePath) == "zh"
                val epubBook = epubParser.createEpubBook(
                    libraryItem.filePath,
                    shouldUseToc = !isInternalChineseBook
                )
                val lastIndex = (epubBook.chapters.size - 1).coerceAtLeast(0)
                val restoreTarget = when {
                    startChapterIndex != null -> ReaderAnchor(
                        startChapterIndex.coerceIn(0, lastIndex), 0
                    )
                    readerData != null -> ReaderAnchor(
                        readerData.lastChapterIndex.coerceIn(0, lastIndex),
                        readerData.lastChapterItemIndex
                    )
                    else -> null
                }
                val initialChapterIndex = restoreTarget?.chapterIndex ?: 0
                _state.update {
                    it.copy(
                        epubBook = epubBook,
                        chapters = epubBook.chapters,
                        pendingRestore = restoreTarget,
                        currentChapterIndex = initialChapterIndex
                    )
                }
                loadChapterBody(initialChapterIndex)
                if (unopenedImportId == libraryItemId) unopenedImportId = null
                delay(200L)
                _state.update { it.copy(isLoading = false, shouldShowLoader = false) }
            } catch (exc: Exception) {
                if (exc is CancellationException) throw exc
                if (unopenedImportId == libraryItemId) {
                    Log.e("ReaderViewModel", "Imported book could not be opened", exc)
                    rollBackImport(libraryItemId)
                } else {
                    reportLoadError("Could not open library book", exc)
                }
            }
        }
    }

    /**
     * An EPUB [uri] from another app. Asks before importing it; after an activity recreation
     * the already imported copy is opened instead of importing again.
     */
    fun openExternal(uri: Uri) {
        externalUri = uri
        val importedId = savedStateHandle.get<Int>(KEY_IMPORTED_ID)
        when {
            importedId != null ->
                if (state.value.libraryItemId != importedId || state.value.hasError) {
                    loadEpubBook(importedId)
                }
            state.value.externalImport == null && state.value.libraryItemId == null -> {
                _state.update { it.copy(externalImport = ExternalImport.Confirm(null)) }
                viewModelScope.launch(Dispatchers.IO) {
                    val name = bookImporter.displayName(uri)
                    externalName = name
                    _state.update {
                        if (it.externalImport is ExternalImport.Confirm) {
                            it.copy(externalImport = ExternalImport.Confirm(name))
                        } else it
                    }
                }
            }
        }
    }

    /** Add the external EPUB to the library, then open it like any library book. */
    fun confirmImport() {
        val uri = externalUri ?: return
        if (importJob?.isActive == true) return
        val name = externalName
        _state.update { it.copy(externalImport = ExternalImport.Importing(name, null)) }
        importJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = bookImporter.importUri(uri) { progress ->
                    _state.update {
                        if (it.externalImport is ExternalImport.Importing) {
                            it.copy(externalImport = ExternalImport.Importing(name, progress))
                        } else it
                    }
                }
                savedStateHandle[KEY_IMPORTED_ID] = id
                unopenedImportId = id
                _state.update { it.copy(externalImport = null) }
                loadEpubBook(id)
            } catch (exc: CancellationException) {
                throw exc
            } catch (exc: Exception) {
                Log.e("ReaderViewModel", "Could not import external book", exc)
                val reason = (exc as? BookImporter.ImportException)?.reason
                    ?: BookImporter.Reason.STORAGE
                _state.update { it.copy(externalImport = ExternalImport.Failed(name, reason)) }
            }
        }
    }

    /** Stop an import in progress; the importer removes anything it created. */
    fun cancelImport() {
        importJob?.cancel()
        importJob = null
    }

    private suspend fun rollBackImport(libraryItemId: Int) {
        bookImporter.rollback(libraryItemId)
        unopenedImportId = null
        savedStateHandle.remove<Int>(KEY_IMPORTED_ID)
        _state.update {
            it.copy(
                libraryItemId = null,
                isLoading = true,
                shouldShowLoader = false,
                externalImport = ExternalImport.Failed(externalName, BookImporter.Reason.NOT_EPUB)
            )
        }
    }

    fun showLoadError() {
        _state.update { it.copy(isLoading = false, shouldShowLoader = false, hasError = true) }
    }

    private fun reportLoadError(message: String, exc: Exception) {
        if (exc is CancellationException) throw exc
        Log.e("ReaderViewModel", message, exc)
        showLoadError()
    }

    fun loadChapterBody(index: Int) {
        val currentState = state.value
        val chapter = currentState.chapters.getOrNull(index) ?: return
        if (currentState.loadedChapters.containsKey(chapter.chapterId)) return
        if (loadingChapterIds.contains(chapter.chapterId)) return

        loadingChapterIds.add(chapter.chapterId)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = epubParser.getChapterBody(state.value.epubBook?.filePath ?: "", chapter)
                _state.update {
                    it.copy(loadedChapters = it.loadedChapters + (chapter.chapterId to content))
                }
                if (wantedPageChapters.remove(index)) requestPages(index)
            } catch (exc: Exception) {
                reportLoadError("Could not load chapter", exc)
            } finally {
                loadingChapterIds.remove(chapter.chapterId)
            }
        }
    }

    fun loadImageData(imagePath: String) {
        if (state.value.loadedImages.containsKey(imagePath)) return
        if (loadingImagePaths.contains(imagePath)) return

        loadingImagePaths.add(imagePath)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = epubParser.getImageData(state.value.epubBook?.filePath ?: "", imagePath)
                if (data != null) {
                    _state.update {
                        it.copy(loadedImages = it.loadedImages + (imagePath to data))
                    }
                }
            } catch (exc: Exception) {
                if (exc is CancellationException) throw exc
                Log.e("ReaderViewModel", "Could not load EPUB image", exc)
            } finally {
                loadingImagePaths.remove(imagePath)
            }
        }
    }

    /** Called by the UI once the pending restore position has been scrolled into view. */
    fun onRestoreComplete() {
        _state.update { it.copy(pendingRestore = null) }
    }

    /** Latest position on screen (first visible item), in either reading mode. */
    fun onPositionChanged(anchor: ReaderAnchor) {
        currentAnchor = anchor
    }

    /** Open the start of [chapterIndex], in either reading mode. */
    fun jumpToChapter(chapterIndex: Int) {
        if (chapterIndex !in state.value.chapters.indices) return
        _state.update { it.copy(pendingRestore = ReaderAnchor(chapterIndex, 0)) }
        loadChapterBody(chapterIndex)
        if (state.value.readingMode == ReadingMode.PAGED) requestPages(chapterIndex)
    }

    fun setReadingMode(mode: ReadingMode) {
        if (mode == state.value.readingMode) return
        preferenceUtil.putString(PreferenceUtil.READER_MODE_STR, mode.id)
        _state.update {
            it.copy(readingMode = mode, pendingRestore = it.pendingRestore ?: currentAnchor)
        }
    }

    /** Paged mode: the UI reports the viewport and typography it lays pages out with. */
    fun setPaginationEnvironment(environment: PaginationEnvironment) {
        paginationRequests.value = environment
    }

    private fun applyPagination(environment: PaginationEnvironment) {
        val hadPages = activePagination != null
        activePagination = environment
        val cached = pageCache.filterKeys { it.spec == environment.spec }
            .mapKeys { it.key.chapterIndex }
        // Different specs never share pages; keep the cache bounded.
        if (pageCache.size > MAX_CACHED_CHAPTER_PAGES) {
            pageCache.keys.removeAll { it.spec != environment.spec }
        }
        _state.update {
            it.copy(
                pageSpec = environment.spec,
                pages = cached,
                // Repagination moves every page: return to the paragraph that was on screen.
                pendingRestore = it.pendingRestore ?: currentAnchor.takeIf { hadPages }
            )
        }
        val current = state.value
        val focus = current.pendingRestore?.chapterIndex ?: current.currentChapterIndex
        requestPages(focus)
        requestPages(focus + 1)
        requestPages(focus - 1)
    }

    /** Make sure [chapterIndex] is paginated for the active spec, loading its body if needed. */
    fun requestPages(chapterIndex: Int) {
        val environment = activePagination ?: return
        val current = state.value
        val chapter = current.chapters.getOrNull(chapterIndex) ?: return
        if (current.pageSpec == environment.spec && current.pages.containsKey(chapterIndex)) return
        val body = current.loadedChapters[chapter.chapterId]?.body
        if (body == null) {
            wantedPageChapters.add(chapterIndex)
            loadChapterBody(chapterIndex)
            // The body may have landed between the read above and registering the wish.
            val landed = state.value.loadedChapters.containsKey(chapter.chapterId)
            if (landed && wantedPageChapters.remove(chapterIndex)) requestPages(chapterIndex)
            return
        }
        val key = PageCacheKey(chapterIndex, environment.spec)
        pageCache[key]?.let { pages ->
            publishPages(environment, chapterIndex, pages)
            return
        }
        if (!paginatingKeys.add(key)) return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val pages = ChapterPaginator(environment).paginate(
                    chapterIndex = chapterIndex,
                    title = chapter.title,
                    items = body,
                    imageSize = ::imageSize
                )
                pageCache[key] = pages
                publishPages(environment, chapterIndex, pages)
            } catch (exc: Exception) {
                reportLoadError("Could not paginate chapter", exc)
            } finally {
                paginatingKeys.remove(key)
            }
        }
    }

    private fun publishPages(
        environment: PaginationEnvironment,
        chapterIndex: Int,
        pages: List<ReaderPage>
    ) {
        _state.update {
            if (it.pageSpec != environment.spec) it
            else it.copy(
                pages = it.pages + (chapterIndex to pages),
                pendingRestore = it.pendingRestore ?: currentAnchor
            )
        }
    }

    /** Pixel size of an EPUB image, loading its bytes for display at the same time. */
    private suspend fun imageSize(path: String): IntSize? {
        imageSizes[path]?.let { return it }
        val bytes = state.value.loadedImages[path] ?: withContext(Dispatchers.IO) {
            epubParser.getImageData(state.value.epubBook?.filePath ?: "", path)
        }?.also { data ->
            _state.update { it.copy(loadedImages = it.loadedImages + (path to data)) }
        } ?: return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return IntSize(options.outWidth, options.outHeight).also { imageSizes[path] = it }
    }

    /**
     * Record the reading position. [anchor] is the first visible item, [chapterFraction] how far
     * into its chapter the reader is, [lastVisible] the last visible item (used to detect the end
     * of the book). Progress is never deleted, not even at the last chapter.
     */
    fun updateReaderProgress(
        libraryItemId: Int,
        anchor: ReaderAnchor,
        chapterFraction: Float,
        lastVisible: ReaderAnchor?
    ) {
        val current = state.value
        val totalChapters = current.chapters.size
        val lastChapterItemCount = current.chapters.lastOrNull()
            ?.let { current.loadedChapters[it.chapterId]?.body?.size } ?: 0
        val finished = lastVisible != null && ReaderPosition.isFinished(
            lastVisibleChapter = lastVisible.chapterIndex,
            lastVisibleItemIndex = lastVisible.itemIndex,
            totalChapters = totalChapters,
            lastChapterItemCount = lastChapterItemCount
        )
        val progress = if (finished) 1f else ReaderPosition.bookFraction(
            anchor.chapterIndex, chapterFraction, totalChapters
        )
        progressUpdates.value = ProgressUpdate(libraryItemId, anchor, progress, finished)
    }

    // NonCancellable: a save already in flight must land even if the reader is being closed.
    private suspend fun saveProgress(update: ProgressUpdate) = withContext(NonCancellable + Dispatchers.IO) {
        val existing = progressDao.getReaderData(update.libraryItemId)
        val newData = ProgressData(
            libraryItemId = update.libraryItemId,
            lastChapterIndex = update.anchor.chapterIndex,
            lastChapterOffset = 0,
            lastReadTime = System.currentTimeMillis(),
            lastChapterItemIndex = update.anchor.itemIndex,
            progress = update.progress,
            finished = ReaderPosition.retainFinished(existing?.finished == true, update.finished)
        )
        if (existing != null) {
            newData.id = existing.id
            progressDao.update(newData)
        } else {
            progressDao.insert(newData)
        }
    }

    fun setChapterScrollPercent(percent: Float) {
        _state.update { it.copy(chapterScrollPercent = percent) }
    }

    fun setVisibleChapterIndex(index: Int) {
        if (index >= 0 && index < state.value.chapters.size) {
            if (index != state.value.currentChapterIndex) {
                _state.update { it.copy(currentChapterIndex = index) }
            }
            // Always ensure current and adjacent chapters are loading/loaded.
            loadChapterBody(index)
            loadChapterBody(index + 1)
            loadChapterBody(index - 1)
            if (state.value.readingMode == ReadingMode.PAGED) {
                requestPages(index)
                requestPages(index + 1)
                requestPages(index - 1)
            }
        }
    }

    fun toggleReaderMenu() {
        _state.update { it.copy(showReaderMenu = !it.showReaderMenu) }
    }

    fun hideReaderInfo() {
        _state.update { it.copy(showReaderMenu = false) }
    }

    fun setFontFamily(font: ReaderFont) {
        preferenceUtil.putString(PreferenceUtil.READER_FONT_STYLE_STR, font.id)
        _state.update { it.copy(fontFamily = font) }
    }

    fun setFontSize(newValue: Int) {
        preferenceUtil.putInt(PreferenceUtil.READER_FONT_SIZE_INT, newValue)
        _state.update { it.copy(fontSize = newValue) }
    }

    fun setLineHeight(newValue: Float) {
        preferenceUtil.putFloat(PreferenceUtil.READER_LINE_HEIGHT_FLOAT, newValue)
        _state.update { it.copy(lineHeight = newValue) }
    }

    fun setReaderTheme(theme: ReaderTheme) {
        preferenceUtil.putString(PreferenceUtil.READER_THEME_STR, theme.id)
        _state.update { it.copy(readerTheme = theme) }
    }

    fun setJustifyText(justify: Boolean) {
        preferenceUtil.putBoolean(PreferenceUtil.READER_JUSTIFY_BOOL, justify)
        _state.update { it.copy(justifyText = justify) }
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        preferenceUtil.putBoolean(PreferenceUtil.READER_KEEP_SCREEN_ON_BOOL, keepOn)
        _state.update { it.copy(keepScreenOn = keepOn) }
    }

    private companion object {
        const val KEY_IMPORTED_ID = "imported_library_item_id"
        const val PAGINATION_DEBOUNCE_MS = 150L
        const val MAX_CACHED_CHAPTER_PAGES = 48
    }
}