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
import androidx.compose.runtime.Composable
import org.scrollia.app.ui.screens.reader.main.composables.ChaptersContent
import org.scrollia.app.ui.screens.reader.main.composables.PagedChaptersContent
import org.scrollia.app.ui.screens.reader.main.composables.ReaderScreen
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderListEntry
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderPosition
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderScreenState
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderViewModel
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReadingMode
import org.scrollia.app.ui.screens.reader.main.viewmodel.VisiblePosition
import org.scrollia.app.ui.screens.settings.viewmodels.SettingsViewModel
import org.scrollia.app.ui.theme.ScrolliaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

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
                // Handle intent and load epub book.
                remember {
                    handleIntent(intent = intent, viewModel = viewModel) {
                        viewModel.showLoadError()
                    }
                }

                val state by viewModel.state.collectAsState()
                LaunchedEffect(state.keepScreenOn) { applyKeepScreenOn(state.keepScreenOn) }

                val onPositionChanged: (VisiblePosition) -> Unit = remember {
                    { position ->
                        viewModel.setChapterScrollPercent(position.chapterFraction)
                        viewModel.setVisibleChapterIndex(position.firstChapterIndex)
                        position.anchor?.let { viewModel.onPositionChanged(it) }
                    }
                }
                val onSaveProgress: (VisiblePosition) -> Unit = remember {
                    { position ->
                        val anchor = position.anchor
                        val libraryItemId = viewModel.state.value.libraryItemId
                        if (anchor != null && libraryItemId != null) {
                            viewModel.updateReaderProgress(
                                libraryItemId = libraryItemId,
                                anchor = anchor,
                                chapterFraction = position.chapterFraction,
                                lastVisible = position.lastVisible
                            )
                        }
                    }
                }

                ReaderScreen(
                    viewModel = viewModel,
                    onScrollToChapter = { chapterIndex -> viewModel.jumpToChapter(chapterIndex) },
                    onRetry = { recreate() },
                    onBack = { finish() },
                    chaptersContent = {
                        when (state.readingMode) {
                            ReadingMode.SCROLL -> ScrollReaderContent(
                                state = state,
                                viewModel = viewModel,
                                onPositionChanged = onPositionChanged,
                                onSaveProgress = onSaveProgress
                            )

                            ReadingMode.PAGED -> PagedChaptersContent(
                                state = state,
                                onToggleReaderMenu = { viewModel.toggleReaderMenu() },
                                onPaginationEnvironment = { viewModel.setPaginationEnvironment(it) },
                                onRequestPages = { viewModel.requestPages(it) },
                                onRestoreComplete = { viewModel.onRestoreComplete() },
                                onPositionChanged = { position ->
                                    onPositionChanged(position)
                                    onSaveProgress(position)
                                },
                                onLoadImage = { viewModel.loadImageData(it) }
                            )
                        }

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
    }

    private fun applyKeepScreenOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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
 * Load the book requested by [intent]: a library item by id, or an EPUB shared by another app
 * through a `content://` URI, which is offered for import first (DESIGN.md §19).
 */
fun handleIntent(intent: Intent, viewModel: ReaderViewModel, onError: () -> Unit) {
    val libraryItemId = intent.extras?.getInt(
        ReaderConstants.EXTRA_LIBRARY_ITEM_ID, ReaderConstants.DEFAULT_NONE
    )?.takeIf { it != ReaderConstants.DEFAULT_NONE }
    val chapterIndex = intent.extras?.getInt(
        ReaderConstants.EXTRA_CHAPTER_IDX, ReaderConstants.DEFAULT_NONE
    )?.takeIf { it != ReaderConstants.DEFAULT_NONE }
    val externalUri = intent.data?.takeIf {
        intent.action == Intent.ACTION_VIEW && it.scheme == ContentResolver.SCHEME_CONTENT
    }

    when {
        // Restores the saved position unless a chapter was explicitly requested.
        libraryItemId != null ->
            viewModel.loadEpubBook(libraryItemId = libraryItemId, startChapterIndex = chapterIndex)
        externalUri != null -> viewModel.openExternal(externalUri)
        else -> onError()
    }
}

/** Continuous scroll mode: the whole book as one lazy column. */
@Composable
private fun ScrollReaderContent(
    state: ReaderScreenState,
    viewModel: ReaderViewModel,
    onPositionChanged: (VisiblePosition) -> Unit,
    onSaveProgress: (VisiblePosition) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val entries = remember(state.chapters, state.loadedChapters) {
        ReaderPosition.buildEntries(state.chapters.map {
            state.loadedChapters[it.chapterId]?.body?.size
        })
    }
    val entriesByKey = remember(entries) { entries.associateBy { it.key } }
    val currentEntriesByKey by rememberUpdatedState(entriesByKey)
    val currentState by rememberUpdatedState(state)

    // Restore the saved position only once its chapter body is loaded:
    // scrolling earlier would land on placeholder geometry.
    LaunchedEffect(state.pendingRestore, entries) {
        val target = state.pendingRestore ?: return@LaunchedEffect
        val index = ReaderPosition.indexOf(entries, target) ?: return@LaunchedEffect
        lazyListState.scrollToItem(index)
        viewModel.onRestoreComplete()
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { readVisiblePosition(lazyListState, currentEntriesByKey, currentState) }
            .filterNotNull()
            .collect { position ->
                onPositionChanged(position)
                position.visibleChapters.forEach { chapterIdx ->
                    viewModel.loadChapterBody(chapterIdx)
                    viewModel.loadChapterBody(chapterIdx + 1)
                    viewModel.loadChapterBody(chapterIdx - 1)
                }
            }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { readVisiblePosition(lazyListState, currentEntriesByKey, currentState) }
            .filterNotNull()
            .distinctUntilChanged { old, new ->
                old.anchor == new.anchor && old.lastVisible == new.lastVisible
            }
            .collect { position -> onSaveProgress(position) }
    }

    ChaptersContent(
        state = state,
        entries = entries,
        lazyListState = lazyListState,
        onToggleReaderMenu = { viewModel.toggleReaderMenu() },
        onLoadImage = { viewModel.loadImageData(it) }
    )
}

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
