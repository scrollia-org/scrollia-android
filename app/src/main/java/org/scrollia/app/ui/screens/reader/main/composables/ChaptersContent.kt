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

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.scrollia.app.R
import org.scrollia.app.epub.asAnnotatedString
import org.scrollia.app.epub.models.ReaderItem
import org.scrollia.app.helpers.toToast
import org.scrollia.app.ui.common.ScrolliaSelectionContainer
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderListEntry
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderScreenState
import org.scrollia.app.ui.theme.frauncesFont
import androidx.compose.runtime.getValue


@Composable
fun ChaptersContent(
    state: ReaderScreenState,
    entries: List<ReaderListEntry>,
    lazyListState: LazyListState,
    onToggleReaderMenu: () -> Unit,
    onLoadImage: (String) -> Unit
) {
    val targetFontSize = remember(state.fontSize) { ReaderLayout.fontSizeSp(state.fontSize) }
    val maxTextWidth = rememberReaderMeasure(targetFontSize, state.fontFamily.fontFamily)
    val bodyStyle = remember(state.epubBook?.language, state.justifyText) {
        ReaderLayout.bodyTextStyle(state.epubBook?.language, state.justifyText)
    }
    val fontSize by animateFloatAsState(
        targetValue = targetFontSize,
        animationSpec = tween(durationMillis = 300),
        label = "fontSize"
    )
    val lineHeight by animateFloatAsState(
        targetValue = state.lineHeight,
        animationSpec = tween(durationMillis = 300),
        label = "lineHeight"
    )

    ReaderSelectionContainer {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // We're using awaitEachGesture instead of Modifier.clickable or
                    // Modifier.detectTapGestures so that we can propagate the click
                    // event to the parent, which in this case is SelectionContainer.
                    // Without this, the click event would be consumed before reaching
                    // SelectionContainer, preventing the copy/paste popup from being
                    // dismissed until the user copies the selected text.
                    awaitEachGesture {
                        val down: PointerInputChange = awaitFirstDown()
                        val up: PointerInputChange? = waitForUpOrCancellation()
                        // only trigger the click if the pointer hasn't moved up or down
                        // i.e. only on tap gesture
                        if (up != null && down.id == up.id) {
                            onToggleReaderMenu()
                        }
                    }
                },
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 64.dp)
        ) {
            items(
                count = entries.size,
                key = { index -> entries[index].key },
                contentType = { index -> entries[index]::class }
            ) { index ->
                when (val entry = entries[index]) {
                    is ReaderListEntry.Placeholder -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is ReaderListEntry.ChapterHeader -> {
                        val chapter = state.chapters[entry.chapterIndex]
                        ChapterHeader(
                            title = chapter.title,
                            isFirstChapter = entry.chapterIndex == 0,
                            fontSize = fontSize,
                            maxTextWidth = maxTextWidth,
                            state = state
                        )
                    }

                    is ReaderListEntry.Item -> {
                        val chapter = state.chapters[entry.chapterIndex]
                        val item = state.loadedChapters[chapter.chapterId]?.body
                            ?.getOrNull(entry.itemIndex)
                        if (item != null) {
                            ReaderItemContent(
                                item = item,
                                state = state,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                maxTextWidth = maxTextWidth,
                                bodyStyle = bodyStyle,
                                onLoadImage = onLoadImage
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Selection with copy/share/translate; web search and dictionary are temporarily disabled.
 * [content] learns if the toolbar is hidden. With [selectable] false only the toolbar is provided
 * (see ScrolliaSelectionContainer).
 */
@Composable
internal fun ReaderSelectionContainer(
    selectable: Boolean = true,
    content: @Composable (toolbarHidden: Boolean) -> Unit
) {
    val context = LocalContext.current
    ScrolliaSelectionContainer(
        onCopyRequested = {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                context.getString(R.string.copied).toToast(context)
            }
        },
        onShareRequested = {
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, it)
                type = "text/plain"
            }
            try {
                context.startActivity(Intent.createChooser(intent, null))
            } catch (_: ActivityNotFoundException) {
                context.getString(R.string.no_app_to_handle_content).toToast(context)
            }
        },
        onWebSearchRequested = {
            val intent = Intent().apply {
                action = Intent.ACTION_WEB_SEARCH
                putExtra(SearchManager.QUERY, it)
            }
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                context.getString(R.string.no_app_to_handle_content).toToast(context)
            }
        },
        onTranslateRequested = {
            val intent = Intent(
                Intent.ACTION_VIEW,
                "https://translate.google.com/?sl=auto&tl=en&text=$it".toUri()
            )
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                context.getString(R.string.no_app_to_handle_content).toToast(context)
            }
        },
        onDictionaryRequested = {
            val dictionaryIntent = Intent()
            val browserIntent = Intent()

            dictionaryIntent.type = "text/plain"
            dictionaryIntent.action = Intent.ACTION_PROCESS_TEXT
            dictionaryIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, it.trim())
            dictionaryIntent.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

            browserIntent.action = Intent.ACTION_VIEW
            val text = it.trim().replace(" ", "+")
            browserIntent.data = "https://www.onelook.com/?w=$text".toUri()

            var dictionaryFailure = false
            try {
                context.startActivity(Intent.createChooser(dictionaryIntent, null))
            } catch (_: ActivityNotFoundException) {
                dictionaryFailure = true
            }

            if (dictionaryFailure) {
                try {
                    context.startActivity(Intent.createChooser(browserIntent, null))
                } catch (_: ActivityNotFoundException) {
                    context.getString(R.string.no_app_to_handle_content).toToast(context)
                }
            }
        },
        selectable = selectable
    ) { toolbarHidden -> content(toolbarHidden) }
}

@Composable
private fun ChapterHeader(
    title: String,
    isFirstChapter: Boolean,
    fontSize: Float,
    maxTextWidth: Dp,
    state: ReaderScreenState
) {
    Column(modifier = Modifier.readerColumn(maxTextWidth)) {
        Spacer(
            modifier = Modifier.height(
                if (isFirstChapter) ReaderLayout.FirstChapterTopSpacing
                else ReaderLayout.ChapterTopSpacing
            )
        )
        Text(
            text = title,
            fontSize = (fontSize * 1.35f).sp,
            lineHeight = 1.3f.em,
            fontFamily = frauncesFont,
            fontWeight = FontWeight.Medium,
            color = state.readerTheme.textColor.copy(alpha = 0.88f)
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ReaderItemContent(
    item: ReaderItem,
    state: ReaderScreenState,
    fontSize: Float,
    lineHeight: Float,
    maxTextWidth: Dp,
    bodyStyle: TextStyle,
    onLoadImage: (String) -> Unit
) {
    when (item) {
        is ReaderItem.Text -> {
            val annotatedString = remember(item.spans) { item.spans.asAnnotatedString() }
            Text(
                text = annotatedString,
                fontSize = fontSize.sp,
                lineHeight = lineHeight.em,
                fontFamily = state.fontFamily.fontFamily,
                color = state.readerTheme.textColor,
                style = bodyStyle,
                modifier = Modifier
                    .readerColumn(maxTextWidth)
                    .padding(bottom = 8.dp),
            )
        }

        is ReaderItem.Image -> {
            val imageBytes = state.loadedImages[item.path]
            if (imageBytes != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageBytes)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .readerColumn(maxTextWidth)
                        .padding(vertical = 12.dp)
                )
            } else {
                LaunchedEffect(item.path) {
                    onLoadImage(item.path)
                }
                Box(
                    modifier = Modifier
                        .readerColumn(maxTextWidth)
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        is ReaderItem.CodeBlock -> {
            Box(
                modifier = Modifier
                    .readerColumn(maxTextWidth)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(state.readerTheme.textColor.copy(alpha = 0.1f))
                    .padding(12.dp)
            ) {
                val annotatedString = remember(item.code) {
                    item.code.asAnnotatedString(collapseWhitespace = false)
                }
                Text(
                    text = annotatedString,
                    fontSize = (fontSize * 0.9f).sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = state.readerTheme.textColor
                )
            }
        }

        is ReaderItem.Blockquote -> {
            Box(
                modifier = Modifier
                    .readerColumn(maxTextWidth)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(state.readerTheme.textColor.copy(alpha = 0.07f))
                    .padding(16.dp)
            ) {
                val annotatedString = remember(item.spans) {
                    item.spans.asAnnotatedString(collapseWhitespace = false)
                }
                Text(
                    text = annotatedString,
                    fontSize = fontSize.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    lineHeight = lineHeight.em,
                    fontFamily = state.fontFamily.fontFamily,
                    color = state.readerTheme.textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}
