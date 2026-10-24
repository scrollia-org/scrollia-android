package org.scrollia.app.ui.screens.reader.main.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.scrollia.app.R
import org.scrollia.app.epub.models.ReaderItem
import org.scrollia.app.ui.screens.reader.main.viewmodel.PageBlock
import org.scrollia.app.ui.screens.reader.main.viewmodel.PageSpec
import org.scrollia.app.ui.screens.reader.main.viewmodel.PagedEntry
import org.scrollia.app.ui.screens.reader.main.viewmodel.PagedStyles
import org.scrollia.app.ui.screens.reader.main.viewmodel.PaginationEnvironment
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderPage
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderScreenState
import org.scrollia.app.ui.screens.reader.main.viewmodel.VisiblePosition
import org.scrollia.app.ui.theme.interFont
import kotlin.math.max
import kotlin.math.min

/** Space above the text of every page; the page indicator lives in it. */
private val PageTopMargin = 40.dp
private val PageBottomMargin = 32.dp

/**
 * Paged reading mode (docs/DESIGN.md §16): chapters laid out in viewport-sized pages, turned by
 * swiping or by tapping the outer thirds of the screen; the center third toggles the menu.
 */
@Composable
fun PagedChaptersContent(
    state: ReaderScreenState,
    onToggleReaderMenu: () -> Unit,
    onPaginationEnvironment: (PaginationEnvironment) -> Unit,
    onRequestPages: (Int) -> Unit,
    onRestoreComplete: () -> Unit,
    onPositionChanged: (VisiblePosition) -> Unit,
    onLoadImage: (String) -> Unit
) {
    val fontSizeSp = remember(state.fontSize) { ReaderLayout.fontSizeSp(state.fontSize) }
    val measure = rememberReaderMeasure(fontSizeSp, state.fontFamily.fontFamily)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fontFamilyResolver = LocalFontFamilyResolver.current

    val entries = remember(state.chapters.size, state.pages) {
        PagedEntry.build(state.chapters.size, state.pages)
    }
    val currentEntries by rememberUpdatedState(entries)
    val currentState by rememberUpdatedState(state)
    val pagerState = rememberPagerState { currentEntries.size }
    val coroutineScope = rememberCoroutineScope()
    val styles = remember(state.pageSpec) { state.pageSpec?.let { PagedStyles(it) } }

    // Restore once the target chapter is paginated (initial open, chapter jump, repagination).
    LaunchedEffect(state.pendingRestore, entries) {
        val target = state.pendingRestore ?: return@LaunchedEffect
        onRequestPages(target.chapterIndex)
        val index = PagedEntry.indexOf(entries, target) ?: return@LaunchedEffect
        pagerState.scrollToPage(index)
        onRestoreComplete()
    }

    LaunchedEffect(pagerState) {
        snapshotFlow {
            if (currentState.pendingRestore != null) null
            else currentEntries.getOrNull(pagerState.currentPage)
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { entry ->
                when (entry) {
                    is PagedEntry.Page -> onPositionChanged(
                        VisiblePosition(
                            anchor = entry.page.anchor,
                            lastVisible = entry.page.lastItem,
                            firstChapterIndex = entry.chapterIndex,
                            visibleChapters = setOf(entry.chapterIndex),
                            chapterFraction = (entry.pageIndex + 1f) / entry.pageCount
                        )
                    )

                    is PagedEntry.Placeholder -> onPositionChanged(
                        VisiblePosition(
                            anchor = null,
                            lastVisible = null,
                            firstChapterIndex = entry.chapterIndex,
                            visibleChapters = setOf(entry.chapterIndex),
                            chapterFraction = 0f
                        )
                    )
                }
            }
    }

    ReaderSelectionContainer { toolbarHidden ->
        val selectionToolbarHidden by rememberUpdatedState(toolbarHidden)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
        ) {
            val spec = with(density) {
                val margin = ReaderLayout.HorizontalMargin.roundToPx()
                PageSpec(
                    textWidthPx = min(constraints.maxWidth - 2 * margin, measure.roundToPx())
                        .coerceAtLeast(1),
                    pageHeightPx = (constraints.maxHeight - PageTopMargin.roundToPx() -
                            PageBottomMargin.roundToPx()).coerceAtLeast(1),
                    fontSizeSp = fontSizeSp,
                    lineHeight = state.lineHeight,
                    font = state.fontFamily,
                    justify = state.justifyText,
                    language = state.epubBook?.language,
                    density = density.density,
                    fontScale = density.fontScale,
                    layoutDirection = layoutDirection
                )
            }
            LaunchedEffect(spec) {
                onPaginationEnvironment(PaginationEnvironment(spec, fontFamilyResolver))
            }

            HorizontalPager(
                state = pagerState,
                key = { index -> currentEntries.getOrNull(index)?.key ?: "missing:$index" },
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(layoutDirection) {
                        // Same tap routing as the scroll reader: never consume, so the selection
                        // container still sees the tap that dismisses its toolbar.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val selectionActive = !selectionToolbarHidden
                            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                            if (up.id != down.id) return@awaitEachGesture
                            val pressMillis = up.uptimeMillis - down.uptimeMillis
                            if (pressMillis >= viewConfiguration.longPressTimeoutMillis) {
                                return@awaitEachGesture
                            }
                            if (selectionActive) return@awaitEachGesture
                            if (currentState.showReaderMenu) {
                                onToggleReaderMenu()
                                return@awaitEachGesture
                            }
                            val third = size.width / 3f
                            val forward = when {
                                up.position.x < third -> layoutDirection == LayoutDirection.Rtl
                                up.position.x > 2 * third -> layoutDirection == LayoutDirection.Ltr
                                else -> null
                            }
                            if (forward == null) {
                                onToggleReaderMenu()
                            } else {
                                val target = pagerState.currentPage + if (forward) 1 else -1
                                if (target in 0 until pagerState.pageCount) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(target) }
                                }
                            }
                        }
                    }
            ) { index ->
                val entry = currentEntries.getOrNull(index)
                if (entry is PagedEntry.Page && styles != null) {
                    PageView(page = entry.page, state = state, styles = styles, onLoadImage = onLoadImage)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = state.readerTheme.textColor.copy(alpha = 0.45f))
                    }
                }
            }

            val current = entries.getOrNull(pagerState.currentPage) as? PagedEntry.Page
            if (current != null && styles != null) {
                PageIndicator(
                    pageNumber = current.pageIndex + 1,
                    pageCount = current.pageCount,
                    widthPx = styles.spec.textWidthPx,
                    state = state
                )
            }
        }
    }
}

/** "Page x of y" in the reader's ink, aligned with the end of the text column. */
@Composable
private fun PageIndicator(pageNumber: Int, pageCount: Int, widthPx: Int, state: ReaderScreenState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = stringResource(id = R.string.reader_page_indicator, pageNumber, pageCount),
            modifier = Modifier.exactWidth(widthPx),
            textAlign = TextAlign.End,
            color = state.readerTheme.textColor.copy(alpha = 0.45f),
            fontFamily = interFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun PageView(
    page: ReaderPage,
    state: ReaderScreenState,
    styles: PagedStyles,
    onLoadImage: (String) -> Unit
) {
    val chapter = state.chapters.getOrNull(page.chapterIndex) ?: return
    val body = state.loadedChapters[chapter.chapterId]?.body ?: return
    val width = styles.spec.textWidthPx
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = PageTopMargin),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.exactWidth(width)) {
            page.blocks.forEach { block ->
                when (block) {
                    is PageBlock.Header -> Box(modifier = Modifier.exactSize(width, block.height)) {
                        BasicText(
                            text = chapter.title,
                            style = styles.headerStyle.copy(
                                color = state.readerTheme.textColor.copy(alpha = 0.88f)
                            ),
                            modifier = Modifier
                                .offset { IntOffset(0, styles.headerTop) }
                                .lineWindow(width, 0, max(0, block.height - styles.headerTop))
                        )
                    }

                    is PageBlock.Lines -> body.getOrNull(block.itemIndex)?.let { item ->
                        LinesBlock(item = item, block = block, styles = styles, state = state)
                    }

                    is PageBlock.Fixed -> {
                        val item = body.getOrNull(block.itemIndex) as? ReaderItem.Image
                        Box(
                            modifier = Modifier.exactSize(width, block.height),
                            contentAlignment = Alignment.Center
                        ) {
                            val bytes = item?.let { state.loadedImages[it.path] }
                            if (bytes != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(bytes)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.exactSize(block.contentWidth, block.contentHeight)
                                )
                            } else if (item != null) {
                                LaunchedEffect(item.path) { onLoadImage(item.path) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A slice of a text item: the full paragraph layout seen through a window of whole lines. */
@Composable
private fun LinesBlock(
    item: ReaderItem,
    block: PageBlock.Lines,
    styles: PagedStyles,
    state: ReaderScreenState
) {
    val text = remember(item) { PagedStyles.textOf(item) } ?: return
    val style = styles.styleFor(item) ?: return
    val ink = state.readerTheme.textColor
    val color = if (item is ReaderItem.Blockquote) ink.copy(alpha = 0.8f) else ink
    val inner = styles.innerPaddingFor(item)
    val outer = styles.outerPaddingFor(item)
    val width = styles.spec.textWidthPx
    Box(modifier = Modifier.exactSize(width, block.height)) {
        if (inner > 0) {
            val frameAlpha = if (item is ReaderItem.CodeBlock) 0.1f else 0.07f
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, outer) }
                    .exactSize(width, block.frameTop + block.clipHeight + block.frameBottom - 2 * outer)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ink.copy(alpha = frameAlpha))
            )
        }
        BasicText(
            text = text,
            style = style.copy(color = color),
            modifier = Modifier
                .offset { IntOffset(inner, block.frameTop) }
                .lineWindow(styles.textWidthFor(item), block.clipTop, block.clipHeight)
        )
    }
}

/** Lays the content out at exactly [widthPx] wide and shows rows [topPx, topPx + heightPx). */
private fun Modifier.lineWindow(widthPx: Int, topPx: Int, heightPx: Int) = this
    .clipToBounds()
    .layout { measurable, _ ->
        val placeable = measurable.measure(PagedStyles.fixedWidth(widthPx))
        layout(widthPx, heightPx) { placeable.place(0, -topPx) }
    }

private fun Modifier.exactWidth(widthPx: Int) = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = widthPx,
            maxWidth = widthPx,
            minHeight = constraints.minHeight,
            maxHeight = constraints.maxHeight
        )
    )
    layout(widthPx, placeable.height) { placeable.place(0, 0) }
}

private fun Modifier.exactSize(widthPx: Int, heightPx: Int) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(widthPx, max(0, heightPx)))
    layout(widthPx, max(0, heightPx)) { placeable.place(0, 0) }
}
