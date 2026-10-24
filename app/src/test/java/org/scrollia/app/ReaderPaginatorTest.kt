package org.scrollia.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.scrollia.app.ui.screens.reader.main.viewmodel.MeasuredItem
import org.scrollia.app.ui.screens.reader.main.viewmodel.PageBlock
import org.scrollia.app.ui.screens.reader.main.viewmodel.PagedEntry
import org.scrollia.app.ui.screens.reader.main.viewmodel.Paginator
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderAnchor
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReadingMode

class ReaderPaginatorTest {

    /** A paragraph of [lines] lines of [lineHeight] px each. */
    private fun paragraph(index: Int, lines: Int, lineHeight: Int = 10, gap: Int = 0) =
        MeasuredItem.Lines(
            itemIndex = index,
            lineTops = FloatArray(lines) { (it * lineHeight).toFloat() },
            lineBottoms = FloatArray(lines) { ((it + 1) * lineHeight).toFloat() },
            gapAfter = gap
        )

    @Test
    fun `small items share a page`() {
        val pages = Paginator.paginate(0, 0, listOf(paragraph(0, 3), paragraph(1, 3)), 100)
        assertThat(pages).hasSize(1)
        assertThat(pages[0].blocks.map { it.itemIndex }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `paragraph is split on whole lines and never overflows a page`() {
        val pages = Paginator.paginate(0, 0, listOf(paragraph(0, 25)), 95)
        assertThat(pages).hasSize(3)
        val slices = pages.map { it.blocks.single() as PageBlock.Lines }
        assertThat(slices.map { it.toLine - it.fromLine }).containsExactly(9, 9, 7).inOrder()
        assertThat(slices[1].clipTop).isEqualTo(90)
        assertThat(slices[1].fromLine).isEqualTo(slices[0].toLine)
        pages.forEach { page -> assertThat(page.blocks.sumOf { it.height }).isAtMost(95) }
    }

    @Test
    fun `header takes the top of the first page`() {
        val pages = Paginator.paginate(2, 50, listOf(paragraph(0, 10)), 100)
        assertThat(pages[0].blocks.first()).isInstanceOf(PageBlock.Header::class.java)
        assertThat((pages[0].blocks[1] as PageBlock.Lines).toLine).isEqualTo(5)
        assertThat(pages[0].anchor).isEqualTo(ReaderAnchor(2, 0))
    }

    @Test
    fun `image that does not fit moves to the next page`() {
        val image = MeasuredItem.Fixed(1, height = 60, contentWidth = 40, contentHeight = 50)
        val pages = Paginator.paginate(0, 0, listOf(paragraph(0, 5), image), 100)
        assertThat(pages).hasSize(2)
        assertThat(pages[1].blocks.single().itemIndex).isEqualTo(1)
    }

    @Test
    fun `page anchor is the first item starting on it`() {
        val pages = Paginator.paginate(0, 0, listOf(paragraph(0, 15), paragraph(1, 3)), 100)
        // Page 2 holds the tail of item 0 and the start of item 1.
        assertThat(pages[1].anchor).isEqualTo(ReaderAnchor(0, 1))
        assertThat(Paginator.pageIndexOf(pages, 1)).isEqualTo(1)
        assertThat(pages[1].lastItem).isEqualTo(ReaderAnchor(0, 1))
    }

    @Test
    fun `page fully inside a long paragraph anchors to it and restores to its first page`() {
        val pages = Paginator.paginate(0, 0, listOf(paragraph(0, 2), paragraph(1, 40)), 100)
        assertThat(pages[2].anchor).isEqualTo(ReaderAnchor(0, 1))
        assertThat(Paginator.pageIndexOf(pages, 1)).isEqualTo(0)
    }

    @Test
    fun `missing items restore to the next page, out of range to the last`() {
        val image = MeasuredItem.Fixed(2, height = 90, contentWidth = 10, contentHeight = 10)
        val pages = Paginator.paginate(0, 0, listOf(paragraph(0, 9), image), 100)
        // Item 1 was dropped (e.g. an undecodable image).
        assertThat(Paginator.pageIndexOf(pages, 1)).isEqualTo(1)
        assertThat(Paginator.pageIndexOf(pages, 99)).isEqualTo(pages.lastIndex)
    }

    @Test
    fun `line taller than the page is shown alone instead of looping`() {
        val pages = Paginator.paginate(0, 0, listOf(paragraph(0, 2, lineHeight = 150)), 100)
        assertThat(pages).hasSize(2)
    }

    @Test
    fun `entries keep placeholders for unpaginated chapters and map anchors`() {
        val chapter1 = Paginator.paginate(1, 0, listOf(paragraph(0, 15), paragraph(1, 3)), 100)
        val entries = PagedEntry.build(3, mapOf(1 to chapter1))
        assertThat(entries.map { it.key }).containsExactly("pp:0", "pg:1:0", "pg:1:1", "pp:2").inOrder()
        assertThat(PagedEntry.indexOf(entries, ReaderAnchor(1, 1))).isEqualTo(2)
        assertThat(PagedEntry.indexOf(entries, ReaderAnchor(0, 0))).isNull()
    }

    @Test
    fun `unknown reading mode falls back to scroll`() {
        assertThat(ReadingMode.getById(null)).isEqualTo(ReadingMode.SCROLL)
        assertThat(ReadingMode.getById("paged")).isEqualTo(ReadingMode.PAGED)
    }
}
