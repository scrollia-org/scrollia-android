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


package org.scrollia.app.ui.screens.reader.detail

import android.content.Context
import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.scrollia.app.R
import org.scrollia.app.database.progress.ProgressData
import org.scrollia.app.helpers.NetworkObserver
import org.scrollia.app.helpers.weakHapticFeedback
import org.scrollia.app.ui.common.CustomTopAppBar
import org.scrollia.app.ui.common.ProgressDots
import org.scrollia.app.ui.common.RecoverableError
import org.scrollia.app.ui.common.simpleVerticalScrollbar
import org.scrollia.app.ui.screens.reader.main.activities.ReaderActivity
import org.scrollia.app.ui.screens.reader.main.activities.ReaderConstants
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont
import org.scrollia.app.ui.theme.literataFont

/** Text measure of the detail column: about 65 characters of Literata 16 sp. */
private val DetailMaxWidth = 640.dp

/** Lines of synopsis shown before "Read more". */
private const val SYNOPSIS_FOLDED_LINES = 6

private fun Modifier.detailColumn(): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = 20.dp)
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = DetailMaxWidth)
    .fillMaxWidth()

@Composable
fun ReaderDetailScreen(
    libraryItemId: String,
    navController: NavController,
    networkStatus: NetworkObserver.Status,
    viewModel: ReaderDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val readerData by (viewModel.progressData?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf(null) })

    LaunchedEffect(key1 = true) { viewModel.loadEbookData(libraryItemId, networkStatus) }

    ReaderDetailScreen(
        libraryItemId = libraryItemId,
        navController = navController,
        state = state,
        progressData = readerData,
        onRetry = { viewModel.loadEbookData(libraryItemId, networkStatus) }
    )
}

@Composable
fun ReaderDetailScreen(
    libraryItemId: String,
    navController: NavController,
    state: ReaderDetailScreenState,
    progressData: ProgressData?,
    onRetry: () -> Unit
) {
    Crossfade(targetState = state.isLoading, label = "ReaderDetailLoadingCrossFade") { isLoading ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 65.dp),
                contentAlignment = Alignment.Center
            ) {
                ProgressDots()
            }
        } else {
            if (state.error != null) {
                RecoverableError(
                    onRetry = onRetry,
                    onBack = { navController.navigateUp() }
                )
            } else {
                ReaderDetailScaffold(
                    libraryItemId = libraryItemId,
                    progressData = progressData,
                    state = state,
                    navController = navController
                )
            }
        }
    }
}

private fun openReader(context: Context, libraryItemId: String, chapterIndex: Int? = null) {
    val intent = Intent(context, ReaderActivity::class.java)
    intent.putExtra(ReaderConstants.EXTRA_LIBRARY_ITEM_ID, libraryItemId.toInt())
    chapterIndex?.let { intent.putExtra(ReaderConstants.EXTRA_CHAPTER_IDX, it) }
    context.startActivity(intent)
}

@Composable
private fun ReaderDetailScaffold(
    libraryItemId: String,
    progressData: ProgressData?,
    state: ReaderDetailScreenState,
    navController: NavController
) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    val started = progressData != null &&
            (progressData.finished || progressData.progressPercent() > 0)
    val currentChapter = progressData?.lastChapterIndex
        ?.takeIf { started && !progressData.finished }
        ?.let { state.chapters.getOrNull(it) }

    Scaffold(topBar = {
        CustomTopAppBar(headerText = stringResource(id = R.string.book_detail_header)) {
            navController.navigateUp()
        }
    }) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 40.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .simpleVerticalScrollbar(
                    lazyListState, color = MaterialTheme.colorScheme.primary
                )
        ) {
            item(key = "header") {
                DetailHeader(
                    title = state.title,
                    authors = state.authors,
                    coverImage = state.coverImage,
                    chapterCount = state.chapters.size,
                    progressData = progressData.takeIf { started },
                    currentChapterTitle = currentChapter?.title,
                    onReadClick = {
                        // A finished book starts over; otherwise resume the saved paragraph.
                        openReader(
                            context, libraryItemId,
                            chapterIndex = if (progressData?.finished == true) 0 else null
                        )
                    },
                    modifier = Modifier
                        .detailColumn()
                        .padding(top = 24.dp)
                )
            }
            item(key = "synopsis_heading") {
                SectionHeading(
                    text = stringResource(id = R.string.book_synopsis),
                    modifier = Modifier
                        .detailColumn()
                        .padding(top = 36.dp, bottom = 10.dp)
                )
            }
            item(key = "synopsis") {
                Synopsis(
                    text = state.description,
                    note = state.descriptionNote,
                    modifier = Modifier.detailColumn()
                )
            }
            item(key = "chapters_heading") {
                SectionHeading(
                    text = stringResource(id = R.string.reader_chapter_list_title),
                    modifier = Modifier
                        .detailColumn()
                        .padding(top = 36.dp, bottom = 6.dp)
                )
            }
            items(state.chapters.size) { idx ->
                val chapter = state.chapters[idx]
                val isCurrent = progressData != null && started && !progressData.finished &&
                        idx == progressData.lastChapterIndex
                ChapterItem(
                    chapterTitle = chapter.title,
                    isRead = progressData?.finished == true ||
                            idx < (progressData?.lastChapterIndex ?: 0),
                    isCurrent = isCurrent,
                    onClick = {
                        // Tapping the chapter being read resumes the saved paragraph
                        // instead of jumping to the chapter start.
                        openReader(
                            context, libraryItemId,
                            chapterIndex = idx.takeIf { idx != progressData?.lastChapterIndex }
                        )
                    },
                    modifier = Modifier.detailColumn()
                )
                if (idx < state.chapters.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.detailColumn(),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    title: String,
    authors: String,
    coverImage: Any?,
    chapterCount: Int,
    progressData: ProgressData?,
    currentChapterTitle: String?,
    onReadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        // Natural aspect ratio at a fixed width (§9): no crop, no letterbox bands.
        Box(
            modifier = Modifier
                .width(if (wide) 168.dp else 116.dp)
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverImage)
                    .crossfade(true).build(),
                placeholder = painterResource(id = R.drawable.placeholder_book),
                error = painterResource(id = R.drawable.placeholder_book),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (wide) 28.dp else 18.dp)
        ) {
            Text(
                text = title,
                fontFamily = frauncesFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (wide) 28.sp else 22.sp,
                lineHeight = if (wide) 34.sp else 27.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = authors,
                fontFamily = interFont,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (chapterCount > 0) {
                CaptionText(
                    text = pluralStringResource(
                        id = R.plurals.detail_chapter_count, count = chapterCount, chapterCount
                    ),
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            if (progressData != null) {
                DetailProgress(
                    progressData = progressData,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .widthIn(max = 240.dp)
                        .fillMaxWidth()
                )
            }
            if (currentChapterTitle != null) {
                Text(
                    text = currentChapterTitle,
                    fontFamily = interFont,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Button(
                onClick = {
                    view.weakHapticFeedback()
                    onReadClick()
                },
                modifier = Modifier
                    .padding(top = 20.dp)
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_read_book),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        id = when {
                            progressData == null -> R.string.detail_start_reading
                            progressData.finished -> R.string.detail_read_again
                            else -> R.string.detail_continue_reading
                        }
                    ),
                    fontFamily = interFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/** Same progress grammar as the Library and "Continue reading": thin line + %, or Finished. */
@Composable
private fun DetailProgress(progressData: ProgressData, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (progressData.finished) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(id = R.string.library_finished),
                fontFamily = interFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LinearProgressIndicator(
                progress = { progressData.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    id = R.string.progress_percent_read, progressData.progressPercent()
                ),
                fontFamily = interFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Editorial "seal": Inter 500, 12 sp, uppercase, wide tracking (§3). */
@Composable
private fun CaptionText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text.uppercase(LocalConfiguration.current.locales[0]),
        fontFamily = interFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        CaptionText(text = text)
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun Synopsis(text: String, note: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        if (text.isEmpty()) {
            Text(
                text = stringResource(id = R.string.book_synopsis_not_found),
                fontFamily = literataFont,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        Text(
            text = text,
            fontFamily = literataFont,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            maxLines = if (expanded) Int.MAX_VALUE else SYNOPSIS_FOLDED_LINES,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
        )
        if (overflows || expanded) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(
                    text = stringResource(
                        id = if (expanded) R.string.detail_synopsis_less else R.string.detail_synopsis_more
                    ),
                    fontFamily = interFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
        if (note.isNotEmpty()) {
            Text(
                text = note,
                fontFamily = interFont,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = if (overflows || expanded) 0.dp else 12.dp)
            )
        }
    }
}

@Composable
private fun ChapterItem(
    chapterTitle: String,
    isRead: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val contentColor = when {
        isCurrent -> MaterialTheme.colorScheme.onSecondaryContainer
        isRead -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val readDescription = stringResource(id = R.string.reader_chapter_read)
    val currentDescription = stringResource(id = R.string.reader_current_chapter)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .clickable {
                view.weakHapticFeedback()
                onClick()
            }
            .semantics {
                when {
                    isCurrent -> stateDescription = currentDescription
                    isRead -> stateDescription = readDescription
                }
            }
            .padding(vertical = 14.dp, horizontal = 12.dp)
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = chapterTitle,
            fontFamily = frauncesFont,
            color = contentColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (isCurrent) {
            CaptionText(
                text = stringResource(id = R.string.reader_chapter_current_label),
                color = contentColor
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun EpubDetailScreenPV() {
    ReaderDetailScreen(
        "",
        rememberNavController(),
        state = ReaderDetailScreenState(),
        progressData = ProgressData(
            libraryItemId = 0,
            lastChapterIndex = 0,
            lastReadTime = 0,
        ),
        onRetry = {}
    )
}
