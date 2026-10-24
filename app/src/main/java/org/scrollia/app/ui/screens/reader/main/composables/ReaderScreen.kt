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


package org.scrollia.app.ui.screens.reader.main.composables

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.scrollia.app.R
import kotlin.math.roundToInt
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderScreenState
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderViewModel
import org.scrollia.app.ui.theme.interFont
import kotlinx.coroutines.launch


@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onScrollToChapter: suspend (Int) -> Unit,
    chaptersContent: @Composable (paddingValues: PaddingValues) -> Unit
) {
    val state = viewModel.state.collectAsState().value
    // Hide reader menu on back press.
    BackHandler(state.showReaderMenu) {
        viewModel.hideReaderInfo()
    }

    val showFontDialog = remember { mutableStateOf(false) }
    ReaderFontChooserDialog(
        showFontDialog = showFontDialog,
        fontFamily = state.fontFamily,
        onFontFamilyChanged = { viewModel.setFontFamily(it) }
    )

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    BackHandler(drawerState.isOpen) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        }
    }

    ChaptersDrawer(
        drawerState = drawerState,
        chapters = state.chapters,
        currentChapterIndex = state.currentChapterIndex,
        onScrollToChapter = { coroutineScope.launch { onScrollToChapter(it) } },
    ) {
        Scaffold(
            containerColor = state.readerTheme.backgroundColor,
            topBar = {
                ReaderTopAppBar(
                    state = state,
                    onChapterListClicked = { coroutineScope.launch { drawerState.open() } },
                    onPreviousChapter = {
                        coroutineScope.launch { onScrollToChapter(state.currentChapterIndex - 1) }
                    },
                    onNextChapter = {
                        coroutineScope.launch { onScrollToChapter(state.currentChapterIndex + 1) }
                    }
                )
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = state.showReaderMenu,
                    enter = expandVertically(initialHeight = { 0 }) + fadeIn(),
                    exit = shrinkVertically(targetHeight = { 0 }) + fadeOut(),
                ) {
                    ReaderBottomBar(
                        state = state,
                        showFontDialog = showFontDialog,
                        onFontSizeChanged = { viewModel.setFontSize(it) },
                        onLineHeightChanged = { viewModel.setLineHeight(it) },
                        onReaderThemeChanged = { viewModel.setReaderTheme(it) }
                    )
                }
            },
            content = { paddingValues ->
                Crossfade(
                    targetState = state.isLoading,
                    label = "reader content loading cross fade"
                ) { loading ->
                    if (loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 65.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.shouldShowLoader) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            chaptersContent(paddingValues)
                            ReaderProgressLine(
                                progress = state.chapterScrollPercent,
                                state = state,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .windowInsetsPadding(WindowInsets.displayCutout)
                            )
                        }
                    }
                }
            }
        )
    }
}

/** Discreet chapter progress (DESIGN.md §3.6): a 2 dp line in the reader's ink color. */
@Composable
private fun ReaderProgressLine(
    progress: Float,
    state: ReaderScreenState,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp),
        color = state.readerTheme.textColor.copy(alpha = 0.45f),
        trackColor = state.readerTheme.textColor.copy(alpha = 0.08f),
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopAppBar(
    state: ReaderScreenState,
    onChapterListClicked: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    AnimatedVisibility(
        visible = state.showReaderMenu,
        enter = expandVertically(initialHeight = { 0 }, expandFrom = Alignment.Top)
                + fadeIn(),
        exit = shrinkVertically(targetHeight = { 0 }, shrinkTowards = Alignment.Top)
                + fadeOut(),
    ) {
        Surface(color = state.readerTheme.backgroundColor) {
            Column(modifier = Modifier.displayCutoutPadding()) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = state.readerTheme.backgroundColor,
                        scrolledContainerColor = state.readerTheme.backgroundColor,
                    ),
                    title = {
                        Text(
                            text = state.epubBook?.title ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.animateContentSize(),
                            color = state.readerTheme.textColor,
                            fontFamily = interFont
                        )

                    },
                    actions = {
                        val hasPrevious = state.currentChapterIndex > 0
                        val hasNext = state.currentChapterIndex < state.chapters.size - 1
                        ReaderBarIcon(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(id = R.string.reader_previous_chapter),
                            enabled = hasPrevious,
                            state = state,
                            onClick = onPreviousChapter
                        )
                        ReaderBarIcon(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(id = R.string.reader_next_chapter),
                            enabled = hasNext,
                            state = state,
                            onClick = onNextChapter
                        )
                        ReaderBarIcon(
                            icon = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(id = R.string.reader_chapter_list_title),
                            state = state,
                            onClick = onChapterListClicked
                        )
                    }
                )
                Column(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = state.currentChapter.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = state.readerTheme.textColor,
                        fontFamily = interFont,
                        fontWeight = FontWeight.Medium
                    )
                    if (state.chapters.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                id = R.string.reader_chapter_progress,
                                state.currentChapterIndex + 1,
                                state.chapters.size,
                                (state.chapterScrollPercent.coerceIn(0f, 1f) * 100).roundToInt()
                            ),
                            modifier = Modifier.padding(top = 2.dp),
                            color = state.readerTheme.textColor.copy(alpha = 0.65f),
                            fontFamily = interFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderBarIcon(
    icon: ImageVector,
    contentDescription: String,
    state: ReaderScreenState,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = state.readerTheme.textColor.copy(alpha = if (enabled) 1f else 0.3f)
        )
    }
}
