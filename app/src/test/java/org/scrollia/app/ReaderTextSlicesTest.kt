package org.scrollia.app

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.scrollia.app.ui.screens.reader.main.viewmodel.MeasuredItem
import org.scrollia.app.ui.screens.reader.main.viewmodel.PageBlock
import org.scrollia.app.ui.screens.reader.main.viewmodel.Paginator
import org.scrollia.app.ui.screens.reader.main.viewmodel.TextSlices

class ReaderTextSlicesTest {

    // "It was the " + bold "best of times" + ", it was the " + italic "worst" + " of times."
    private val styled = buildAnnotatedString {
        append("It was the ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("best of times") }
        append(", it was the ")
        pushStringAnnotation(tag = "URL", annotation = "https://example.org")
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("worst") }
        pop()
        append(" of times.")
    }

    @Test
    fun `char range covers whole lines`() {
        val starts = intArrayOf(0, 11, 24, 37)
        assertThat(TextSlices.charRange(starts, 50, 0, 1)).isEqualTo(0 until 11)
        assertThat(TextSlices.charRange(starts, 50, 1, 3)).isEqualTo(11 until 37)
        assertThat(TextSlices.charRange(starts, 50, 3, 4)).isEqualTo(37 until 50)
    }

    @Test
    fun `char range of unknown or empty input is empty`() {
        assertThat(TextSlices.charRange(IntArray(0), 0, 0, 1).isEmpty()).isTrue()
        assertThat(TextSlices.charRange(intArrayOf(0, 5), 9, 1, 1).isEmpty()).isTrue()
    }

    @Test
    fun `one line paragraph is a single slice of the whole text`() {
        assertThat(TextSlices.charRange(intArrayOf(0), 12, 0, 1)).isEqualTo(0 until 12)
    }

    @Test
    fun `bold span crossing the start of a slice is clipped, not dropped`() {
        // Slice starts inside "best of times" (bold covers 11..24).
        val slice = TextSlices.visibleText(styled, 16, 30)
        assertThat(slice.text).isEqualTo(styled.text.substring(16, 30))
        val bold = slice.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertThat(bold.start).isEqualTo(0)
        assertThat(bold.end).isEqualTo(24 - 16)
    }

    @Test
    fun `leading and trailing slices split styles and annotations exactly`() {
        val cut = styled.text.indexOf("worst") + 2 // inside the italic, annotated word
        val leading = TextSlices.visibleText(styled, 0, cut)
        val trailing = TextSlices.visibleText(styled, cut, styled.length)

        assertThat(leading.text + trailing.text).isEqualTo(styled.text)
        val leadingItalic = leading.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
        val trailingItalic = trailing.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
        assertThat(leading.text.substring(leadingItalic.start, leadingItalic.end)).isEqualTo("wo")
        assertThat(trailing.text.substring(trailingItalic.start, trailingItalic.end)).isEqualTo("rst")
        assertThat(leading.getStringAnnotations("URL", 0, leading.length).single().item)
            .isEqualTo("https://example.org")
        assertThat(trailing.getStringAnnotations("URL", 0, trailing.length).single().end)
            .isEqualTo(3)
    }

    @Test
    fun `whitespace inside a slice is preserved`() {
        val text = AnnotatedString("  two  spaces \n kept ")
        assertThat(TextSlices.visibleText(text, 1, text.length).text).isEqualTo(" two  spaces \n kept ")
    }

    @Test
    fun `empty input and out of range offsets are safe`() {
        assertThat(TextSlices.visibleText(AnnotatedString(""), 0, 0).text).isEmpty()
        assertThat(TextSlices.visibleText(styled, 40, 10).text).isEmpty()
        assertThat(TextSlices.layoutText(styled, 999).text).isEmpty()
    }

    @Test
    fun `layout text keeps the rest of the paragraph and its styles`() {
        val tail = TextSlices.layoutText(styled, 11)
        assertThat(tail.text).isEqualTo(styled.text.substring(11))
        assertThat(tail.spanStyles.first { it.item.fontWeight == FontWeight.Bold }.start)
            .isEqualTo(0)
    }

    /** A paragraph of [lines] lines of 10 px; each line is [chars] characters long. */
    private fun paragraph(lines: Int, chars: Int, firstLine: Int = 0, trimFirstTop: Int = 0):
            MeasuredItem.Lines {
        val count = lines - firstLine
        return MeasuredItem.Lines(
            itemIndex = 0,
            lineTops = FloatArray(count) { if (it == 0) 0f else (it * 10 - trimFirstTop).toFloat() },
            lineBottoms = FloatArray(count) { ((it + 1) * 10 - trimFirstTop).toFloat() },
            lineStarts = IntArray(count) { (firstLine + it) * chars },
            textEnd = lines * chars,
            // A continuation is laid out on its own; its first line loses its top leading.
            remeasureFrom = { line -> paragraph(lines, chars, firstLine + line, trimFirstTop = 2) }
        )
    }

    @Test
    fun `continuation slices are measured on their own and cover every character once`() {
        val pages = Paginator.paginate(0, 0, listOf(paragraph(lines = 25, chars = 40)), 95)
        val slices = pages.map { it.blocks.single() as PageBlock.Lines }

        assertThat(slices.first().startChar).isEqualTo(0)
        assertThat(slices.last().endChar).isEqualTo(25 * 40)
        slices.zipWithNext { a, b -> assertThat(b.startChar).isEqualTo(a.endChar) }
        assertThat(slices.map { it.startsItem }).containsExactly(true, false, false).inOrder()
        assertThat(slices.map { it.endsItem }).containsExactly(false, false, true).inOrder()
        // Every slice is drawn from the start of its own layout.
        slices.forEach { slice ->
            assertThat(slice.fromLine).isEqualTo(0)
            assertThat(slice.clipTop).isEqualTo(0)
        }
        assertThat(slices.map { it.lineCount }).containsExactly(9, 9, 7).inOrder()
        assertThat(slices[1].clipHeight).isEqualTo(88)
        pages.forEach { page -> assertThat(page.blocks.sumOf { it.height }).isAtMost(95) }
    }

    @Test
    fun `without remeasuring, slices reuse the layout and stay contiguous`() {
        val item = MeasuredItem.Lines(
            itemIndex = 0,
            lineTops = FloatArray(12) { (it * 10).toFloat() },
            lineBottoms = FloatArray(12) { ((it + 1) * 10).toFloat() },
            lineStarts = IntArray(12) { it * 7 },
            textEnd = 84
        )
        val slices = Paginator.paginate(0, 0, listOf(item), 50)
            .map { it.blocks.single() as PageBlock.Lines }
        assertThat(slices.map { it.startChar to it.endChar })
            .containsExactly(0 to 35, 35 to 70, 70 to 84).inOrder()
        assertThat(slices[1].fromLine).isEqualTo(5)
    }
}
