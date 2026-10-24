package org.scrollia.app.ui.screens.reader.main.viewmodel

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.scrollia.app.epub.asAnnotatedString
import org.scrollia.app.epub.models.ReaderItem
import org.scrollia.app.ui.screens.reader.main.composables.ReaderLayout
import org.scrollia.app.ui.theme.frauncesFont
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Everything pagination depends on. Colors are deliberately absent: a theme change must not
 * repaginate. Two specs that are equal produce identical pages.
 */
data class PageSpec(
    val textWidthPx: Int,
    val pageHeightPx: Int,
    val fontSizeSp: Float,
    val lineHeight: Float,
    val font: ReaderFont,
    val justify: Boolean,
    val language: String?,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection
)

/** A [PageSpec] plus the font resolver needed to measure text with it. */
class PaginationEnvironment(
    val spec: PageSpec,
    val fontFamilyResolver: FontFamily.Resolver
)

/**
 * Text styles and block metrics of paged mode. Shared by measurement and rendering so that a
 * page is drawn with exactly the layout it was measured with.
 */
class PagedStyles(val spec: PageSpec) {
    val density = Density(spec.density, spec.fontScale)

    private fun Int.dpPx() = with(density) { this@dpPx.dp.roundToPx() }

    val paragraphGap = 8.dpPx()
    val headerTop = ReaderLayout.ChapterTopSpacing.value.roundToInt().dpPx()
    val headerBottom = 20.dpPx()
    val imagePadding = 12.dpPx()
    val blockOuterPadding = 8.dpPx()
    val codeInnerPadding = 12.dpPx()
    val quoteInnerPadding = 16.dpPx()

    val bodyStyle: TextStyle = ReaderLayout.bodyTextStyle(spec.language, spec.justify).copy(
        fontSize = spec.fontSizeSp.sp,
        lineHeight = spec.lineHeight.em,
        fontFamily = spec.font.fontFamily
    )

    val quoteStyle = TextStyle(
        fontSize = spec.fontSizeSp.sp,
        lineHeight = spec.lineHeight.em,
        fontStyle = FontStyle.Italic,
        fontFamily = spec.font.fontFamily,
        localeList = bodyStyle.localeList
    )

    val codeStyle = TextStyle(
        fontSize = (spec.fontSizeSp * 0.9f).sp,
        fontFamily = FontFamily.Monospace
    )

    val headerStyle = TextStyle(
        fontSize = (spec.fontSizeSp * 1.35f).sp,
        lineHeight = 1.3f.em,
        fontFamily = frauncesFont,
        fontWeight = FontWeight.Medium,
        localeList = bodyStyle.localeList
    )

    /** Text style of a text-like item, or null for images. */
    fun styleFor(item: ReaderItem): TextStyle? = when (item) {
        is ReaderItem.Text -> bodyStyle
        is ReaderItem.Blockquote -> quoteStyle
        is ReaderItem.CodeBlock -> codeStyle
        is ReaderItem.Image -> null
    }

    /** Inner padding of the frame drawn around a block item. */
    fun innerPaddingFor(item: ReaderItem): Int = when (item) {
        is ReaderItem.Blockquote -> quoteInnerPadding
        is ReaderItem.CodeBlock -> codeInnerPadding
        else -> 0
    }

    fun outerPaddingFor(item: ReaderItem): Int = when (item) {
        is ReaderItem.Blockquote, is ReaderItem.CodeBlock -> blockOuterPadding
        else -> 0
    }

    /** Width available to the text of [item], inside its frame. */
    fun textWidthFor(item: ReaderItem): Int =
        (spec.textWidthPx - 2 * innerPaddingFor(item)).coerceAtLeast(1)

    companion object {
        /** The exact string laid out for a text-like item (same as the scroll reader). */
        fun textOf(item: ReaderItem): AnnotatedString? = when (item) {
            is ReaderItem.Text -> item.spans.asAnnotatedString()
            is ReaderItem.Blockquote -> item.spans.asAnnotatedString(collapseWhitespace = false)
            is ReaderItem.CodeBlock -> item.code.asAnnotatedString(collapseWhitespace = false)
            is ReaderItem.Image -> null
        }

        fun fixedWidth(width: Int) = Constraints(minWidth = width, maxWidth = width)
    }
}

/** Measures a chapter with [TextMeasurer] and paginates it. Safe to run off the main thread. */
class ChapterPaginator(environment: PaginationEnvironment) {

    private val styles = PagedStyles(environment.spec)
    private val spec = environment.spec
    private val measurer = TextMeasurer(
        defaultFontFamilyResolver = environment.fontFamilyResolver,
        defaultDensity = styles.density,
        defaultLayoutDirection = spec.layoutDirection,
        cacheSize = 0
    )

    private fun measure(text: AnnotatedString, style: TextStyle, width: Int): TextLayoutResult =
        measurer.measure(text = text, style = style, constraints = PagedStyles.fixedWidth(width))

    suspend fun paginate(
        chapterIndex: Int,
        title: String,
        items: List<ReaderItem>,
        imageSize: suspend (String) -> IntSize?
    ): List<ReaderPage> {
        val headerHeight = if (title.isBlank()) 0 else {
            styles.headerTop + measure(AnnotatedString(title), styles.headerStyle, spec.textWidthPx)
                .size.height + styles.headerBottom
        }
        val measured = items.mapIndexedNotNull { index, item -> measureItem(index, item, imageSize) }
        return Paginator.paginate(chapterIndex, headerHeight, measured, spec.pageHeightPx)
    }

    private suspend fun measureItem(
        index: Int,
        item: ReaderItem,
        imageSize: suspend (String) -> IntSize?
    ): MeasuredItem? {
        if (item is ReaderItem.Image) {
            val size = imageSize(item.path) ?: return null
            if (size.width <= 0 || size.height <= 0) return null
            val maxHeight = (spec.pageHeightPx - 2 * styles.imagePadding).coerceAtLeast(1)
            // Image pixels are treated as dp so small ornaments are not blown up to full width.
            var width = min(spec.textWidthPx.toFloat(), size.width * spec.density)
            var height = width * size.height / size.width
            if (height > maxHeight) {
                width *= maxHeight / height
                height = maxHeight.toFloat()
            }
            val contentHeight = ceil(height).toInt()
            return MeasuredItem.Fixed(
                itemIndex = index,
                height = contentHeight + 2 * styles.imagePadding,
                contentWidth = ceil(width).toInt(),
                contentHeight = contentHeight
            )
        }
        val text = PagedStyles.textOf(item) ?: return null
        return measureLines(index, item, text, start = 0)
    }

    /** Lines of [text] from offset [start] to its end, laid out on their own. */
    private fun measureLines(
        index: Int,
        item: ReaderItem,
        text: AnnotatedString,
        start: Int
    ): MeasuredItem.Lines {
        val layout = measure(
            TextSlices.layoutText(text, start), styles.styleFor(item)!!, styles.textWidthFor(item)
        )
        val frame = styles.outerPaddingFor(item) + styles.innerPaddingFor(item)
        val lineStarts = IntArray(layout.lineCount) { start + layout.getLineStart(it) }
        return MeasuredItem.Lines(
            itemIndex = index,
            lineTops = FloatArray(layout.lineCount) { layout.getLineTop(it) },
            lineBottoms = FloatArray(layout.lineCount) { layout.getLineBottom(it) },
            frameTop = frame,
            frameBottom = frame,
            gapAfter = if (item is ReaderItem.Text) styles.paragraphGap else 0,
            lineStarts = lineStarts,
            textEnd = text.length,
            remeasureFrom = { line -> measureLines(index, item, text, lineStarts[line]) }
        )
    }
}
