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

package org.scrollia.app.ui.screens.reader.main.activities

import android.content.ContentResolver
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import org.scrollia.app.R
import org.scrollia.app.helpers.Constants
import org.scrollia.app.helpers.toToast
import org.scrollia.app.ui.screens.reader.main.composables.ChaptersContent
import org.scrollia.app.ui.screens.reader.main.composables.ReaderScreen
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderAnchor
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderListEntry
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderPosition
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderScreenState
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderViewModel
import org.scrollia.app.ui.screens.settings.viewmodels.SettingsViewModel
import org.scrollia.app.ui.theme.ScrolliaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.io.FileInputStream

@AndroidEntryPoint
class ReaderActivity : AppCompatActivity() {

    private lateinit var settingsViewModel: SettingsViewModel
    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupWindowInsets() // Setup fullscreen mode.

        // Initialize settings view model.
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        // Set UI contents.
        setContent {
            ScrolliaTheme(settingsViewModel = settingsViewModel) {
                val lazyListState = rememberLazyListState()
                // Handle intent and load epub book.
                val intentData = remember {
                    handleIntent(
                        intent = intent,
                        viewModel = viewModel,
                        contentResolver = contentResolver,
                        onError = {
                            getString(R.string.error).toToast(this)
                            finish()
                        })
                }

                val state by viewModel.state.collectAsState()
                val entries = remember(state.chapters, state.loadedChapters) {
                    ReaderPosition.buildEntries(state.chapters.map {
                        state.loadedChapters[it.chapterId]?.body?.size
                    })
                }
                val entriesByKey = remember(entries) { entries.associateBy { it.key } }
                val currentEntries by rememberUpdatedState(entries)
                val currentEntriesByKey by rememberUpdatedState(entriesByKey)

                ReaderScreen(
                    viewModel = viewModel,
                    onScrollToChapter = { chapterIndex ->
                        val index = ReaderPosition.chapterStartIndex(currentEntries, chapterIndex)
                        if (index >= 0) lazyListState.scrollToItem(index)
                    },
                    chaptersContent = {
                        // Restore the saved position only once its chapter body is loaded:
                        // scrolling earlier would land on placeholder geometry.
                        LaunchedEffect(state.pendingRestore, entries) {
                            val target = state.pendingRestore ?: return@LaunchedEffect
                            val index = ReaderPosition.indexOf(entries, target)
                                ?: return@LaunchedEffect
                            lazyListState.scrollToItem(index)
                            viewModel.onRestoreComplete()
                        }

                        LaunchedEffect(lazyListState) {
                            snapshotFlow { readVisiblePosition(lazyListState, currentEntriesByKey, state) }
                                .filterNotNull()
                                .collect { position ->
                                    viewModel.setChapterScrollPercent(position.chapterFraction)
                                    viewModel.setVisibleChapterIndex(position.firstChapterIndex)
                                    position.visibleChapters.forEach { chapterIdx ->
                                        viewModel.loadChapterBody(chapterIdx)
                                        viewModel.loadChapterBody(chapterIdx + 1)
                                        viewModel.loadChapterBody(chapterIdx - 1)
                                    }
                                }
                        }

                        LaunchedEffect(lazyListState) {
                            snapshotFlow { readVisiblePosition(lazyListState, currentEntriesByKey, state) }
                                .filterNotNull()
                                .distinctUntilChanged { old, new ->
                                    old.anchor == new.anchor && old.lastVisible == new.lastVisible
                                }
                                .collect { position ->
                                    // If book was not opened from external epub file, update the
                                    // reading progress into the database.
                                    val anchor = position.anchor ?: return@collect
                                    if (!intentData.isExternalFile && intentData.libraryItemId != null) {
                                        viewModel.updateReaderProgress(
                                            libraryItemId = intentData.libraryItemId,
                                            anchor = anchor,
                                            chapterFraction = position.chapterFraction,
                                            lastVisible = position.lastVisible
                                        )
                                    }
                                }
                        }

                        // Reader content lazy column.
                        ChaptersContent(
                            state = state,
                            entries = entries,
                            lazyListState = lazyListState,
                            onToggleReaderMenu = { viewModel.toggleReaderMenu() },
                            onLoadImage = { viewModel.loadImageData(it) }
                        )

                        // Toggle system bars based on reader menu visibility.
                        LaunchedEffect(state.showReaderMenu) {
                            toggleSystemBars(state.showReaderMenu)
                        }
                    })
            }
        }
    }


    private fun setupWindowInsets() {
        // Fullscreen mode that ignores any cutout, notch etc.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.hide(WindowInsetsCompat.Type.displayCutout())

        // Set layout in display cutout mode for Android P and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Keep screen on.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun toggleSystemBars(show: Boolean) {
        when (show) {
            true -> showSystemBars()
            false -> hideSystemBars()
        }
    }

    private fun showSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.show(WindowInsetsCompat.Type.displayCutout())
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.hide(WindowInsetsCompat.Type.displayCutout())
    }
}

object ReaderConstants {
    const val EXTRA_LIBRARY_ITEM_ID = "reader_book_id"
    const val EXTRA_CHAPTER_IDX = "reader_chapter_index"
    const val DEFAULT_NONE = -100000

}

/**
 * Data class to hold intent information for ReaderActivity.
 *
 * @param libraryItemId Library item id.
 * @param chapterIndex Chapter index.
 * @param isExternalFile Is book opened from external file.
 */
data class IntentData(
    val libraryItemId: Int?, val chapterIndex: Int?, val isExternalFile: Boolean
)

/**
 * Handle intent and load epub book from given id or external file.
 *
 * @param intent Intent to handle.
 * @param viewModel ReaderViewModel to load book.
 * @param contentResolver ContentResolver to open input stream.
 * @param onError Function to handle error.
 *
 * @return IntentData object containing book id, chapter index and isExternalBook.
 */
fun handleIntent(
    intent: Intent,
    viewModel: ReaderViewModel,
    contentResolver: ContentResolver,
    onError: () -> Unit
): IntentData {
    val libraryItemId = intent.extras?.getInt(
        ReaderConstants.EXTRA_LIBRARY_ITEM_ID, ReaderConstants.DEFAULT_NONE
    )
    val chapterIndex = intent.extras?.getInt(
        ReaderConstants.EXTRA_CHAPTER_IDX, ReaderConstants.DEFAULT_NONE
    )?.takeIf { it != ReaderConstants.DEFAULT_NONE }
    val isExternalFile = intent.type == Constants.EPUB_MIME_TYPE

    // Internal book
    if (libraryItemId != null && libraryItemId != ReaderConstants.DEFAULT_NONE) {
        // Load epub book from library; restores the saved position unless a chapter was
        // explicitly requested.
        viewModel.loadEpubBook(libraryItemId = libraryItemId, startChapterIndex = chapterIndex)
    } else if (isExternalFile) {
        // External book from file.
        intent.data?.let {
            contentResolver.openInputStream(it)?.let { ips ->
                viewModel.loadEpubBookExternal(ips as FileInputStream)
            }
        }
    } else {
        onError() // Invalid intent.
    }

    return IntentData(libraryItemId, chapterIndex, isExternalFile)
}

/**
 * What is on screen right now, in semantic terms.
 *
 * @param anchor First visible item, or null if it is an unloaded chapter placeholder.
 * @param lastVisible Last visible item (null for placeholders).
 * @param firstChapterIndex Chapter of the first visible row.
 * @param visibleChapters Chapters with at least one visible row.
 * @param chapterFraction How far (0..1) the reader is into the first visible chapter.
 */
data class VisiblePosition(
    val anchor: ReaderAnchor?,
    val lastVisible: ReaderAnchor?,
    val firstChapterIndex: Int,
    val visibleChapters: Set<Int>,
    val chapterFraction: Float
)

/**
 * Read the visible position from the list layout. Returns null while a restore is pending so
 * the initial (unrestored) layout never overwrites saved progress.
 */
private fun readVisiblePosition(
    lazyListState: LazyListState,
    byKey: Map<String, ReaderListEntry>,
    state: ReaderScreenState
): VisiblePosition? {
    if (state.pendingRestore != null) return null
    val layoutInfo = lazyListState.layoutInfo
    val visible = layoutInfo.visibleItemsInfo
        .filter { it.offset + it.size > layoutInfo.viewportStartOffset }
    if (visible.isEmpty()) return null
    val first = visible.first()
    val firstEntry = byKey[first.key] ?: return null
    val lastEntry = byKey[visible.last().key]

    val chapter = state.chapters.getOrNull(firstEntry.chapterIndex)
    val itemCount = chapter?.let { state.loadedChapters[it.chapterId]?.body?.size } ?: 0
    val withinItem = if (first.size > 0) {
        (layoutInfo.viewportStartOffset - first.offset).toFloat() / first.size
    } else 0f
    val chapterFraction = when (firstEntry) {
        is ReaderListEntry.Item ->
            ReaderPosition.chapterFraction(firstEntry.itemIndex, itemCount, withinItem)
        else -> 0f
    }

    return VisiblePosition(
        anchor = ReaderPosition.anchorOf(firstEntry),
        lastVisible = ReaderPosition.anchorOf(lastEntry),
        firstChapterIndex = firstEntry.chapterIndex,
        visibleChapters = visible.mapNotNull { byKey[it.key]?.chapterIndex }.toSet(),
        chapterFraction = chapterFraction
    )
}
