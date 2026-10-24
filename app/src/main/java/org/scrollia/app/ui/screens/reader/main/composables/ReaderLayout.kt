package org.scrollia.app.ui.screens.reader.main.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Reader page geometry (docs/DESIGN.md §3.6 and §12). */
object ReaderLayout {
    /** Side margin of the text column on narrow screens. */
    val HorizontalMargin = 24.dp

    /** Target measure of the text column, in "ch" (width of the digit zero). */
    const val MEASURE_CH = 65

    /** Top spacing before every chapter title except the first one. */
    val ChapterTopSpacing = 56.dp

    /** Top spacing before the first chapter title. */
    val FirstChapterTopSpacing = 32.dp

    /** Maps the stored 50..200 font-size preference to sp. */
    fun fontSizeSp(fontSizePref: Int): Float = (fontSizePref / 5) * 0.9f
}

/** Width of [ReaderLayout.MEASURE_CH] zeros in the current reader font: the max text measure. */
@Composable
fun rememberReaderMeasure(fontSizeSp: Float, fontFamily: FontFamily): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(fontSizeSp, fontFamily, density) {
        val width = textMeasurer.measure(
            text = "0".repeat(ReaderLayout.MEASURE_CH),
            style = TextStyle(fontSize = fontSizeSp.sp, fontFamily = fontFamily),
            softWrap = false
        ).size.width
        with(density) { width.toDp() }
    }
}

/** Centered text column: side margins on phones, capped at [maxWidth] on large screens. */
fun Modifier.readerColumn(maxWidth: Dp): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = ReaderLayout.HorizontalMargin)
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = maxWidth)
    .fillMaxWidth()
