package org.scrollia.app.ui.screens.reader.main.viewmodel

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/** How the reader lays out a book: one continuous column, or viewport-sized pages. */
enum class ReadingMode(val id: String) {
    SCROLL("scroll"),
    PAGED("paged");

    companion object {
        fun getById(id: String?) = entries.find { it.id == id } ?: SCROLL
    }
}

/**
 * A reader item after measurement, in pixels. Text-like items expose their line boxes so they
 * can be split across pages; everything else is an unbreakable block.
 */
sealed class MeasuredItem {
    abstract val itemIndex: Int

    /**
     * @param lineTops / lineBottoms Line boxes of the layout.
     * @param frameTop / frameBottom Space drawn around every slice (block padding).
     * @param gapAfter Space after the item, only when it ends on the page.
     * @param lineStarts Offset in the item's text where each line starts (empty if unknown).
     * @param textEnd Offset in the item's text where the last line ends.
     * @param remeasureFrom The rest of the item from a line on, laid out on its own; this is
     *   what a continuation slice draws, so pagination measures exactly that. Null: slices
     *   reuse this layout's lines.
     */
    class Lines(
        override val itemIndex: Int,
        val lineTops: FloatArray,
        val lineBottoms: FloatArray,
        val frameTop: Int = 0,
        val frameBottom: Int = 0,
        val gapAfter: Int = 0,
        val lineStarts: IntArray = IntArray(0),
        val textEnd: Int = 0,
        val remeasureFrom: ((line: Int) -> Lines)? = null
    ) : MeasuredItem() {
        val lineCount get() = lineTops.size
    }

    /** An unbreakable block (image) whose content is [contentWidth]×[contentHeight]. */
    class Fixed(
        override val itemIndex: Int,
        val height: Int,
        val contentWidth: Int,
        val contentHeight: Int
    ) : MeasuredItem()
}

sealed class PageBlock {
    /** Reader item this block belongs to (the chapter title counts as item 0). */
    abstract val itemIndex: Int
    abstract val height: Int

    /** True if the item's beginning is on this page. */
    abstract val startsItem: Boolean

    data class Header(override val height: Int) : PageBlock() {
        override val itemIndex get() = 0
        override val startsItem get() = true
    }

    /**
     * Lines [fromLine, toLine) of a text item, i.e. its characters [startChar, endChar). The
     * item's text from [startChar] to its end is laid out (so justification and the hyphen at
     * the cut are those of the running paragraph) and only these lines are shown, through the
     * window [clipTop, clipTop + clipHeight) of that layout.
     */
    data class Lines(
        override val itemIndex: Int,
        val fromLine: Int,
        val toLine: Int,
        val clipTop: Int,
        val clipHeight: Int,
        val frameTop: Int,
        val frameBottom: Int,
        val gapAfter: Int,
        override val startsItem: Boolean,
        val endsItem: Boolean,
        val startChar: Int = 0,
        val endChar: Int = 0
    ) : PageBlock() {
        override val height get() = frameTop + clipHeight + frameBottom + gapAfter
        val lineCount get() = toLine - fromLine
    }

    data class Fixed(
        override val itemIndex: Int,
        override val height: Int,
        val contentWidth: Int,
        val contentHeight: Int
    ) : PageBlock() {
        override val startsItem get() = true
    }
}

data class ReaderPage(val chapterIndex: Int, val blocks: List<PageBlock>) {

    /**
     * Anchor stored for this page: the first item that *starts* on it, or the item continuing
     * from the previous page if none starts here. Restoring it lands on this same page.
     */
    val anchor: ReaderAnchor
        get() = ReaderAnchor(
            chapterIndex,
            (blocks.firstOrNull { it.startsItem } ?: blocks.firstOrNull())?.itemIndex ?: 0
        )

    val lastItem: ReaderAnchor
        get() = ReaderAnchor(chapterIndex, blocks.lastOrNull()?.itemIndex ?: 0)
}

/** Greedy, measurement-based pagination of one chapter. Pure: works on pixel metrics only. */
object Paginator {

    fun paginate(
        chapterIndex: Int,
        headerHeight: Int,
        items: List<MeasuredItem>,
        pageHeight: Int
    ): List<ReaderPage> {
        require(pageHeight > 0) { "pageHeight must be positive" }
        val pages = ArrayList<ReaderPage>()
        var blocks = ArrayList<PageBlock>()
        var used = 0

        fun newPage() {
            if (blocks.isEmpty()) return
            pages.add(ReaderPage(chapterIndex, blocks))
            blocks = ArrayList()
            used = 0
        }

        if (headerHeight > 0) {
            val height = min(headerHeight, pageHeight)
            blocks.add(PageBlock.Header(height))
            used = height
        }

        for (item in items) {
            when (item) {
                is MeasuredItem.Fixed -> {
                    val height = min(item.height, pageHeight)
                    if (used + height > pageHeight) newPage()
                    blocks.add(
                        PageBlock.Fixed(item.itemIndex, height, item.contentWidth, item.contentHeight)
                    )
                    used += height
                }

                is MeasuredItem.Lines -> {
                    var layout: MeasuredItem.Lines = item
                    var from = 0
                    while (from < layout.lineCount) {
                        val lineCount = layout.lineCount
                        val available = pageHeight - used - layout.frameTop - layout.frameBottom
                        val top = floor(layout.lineTops[from]).toInt()
                        var to = from
                        while (to < lineCount &&
                            ceil(layout.lineBottoms[to]).toInt() - top <= available
                        ) to++
                        if (to == from) {
                            if (blocks.isNotEmpty()) {
                                newPage()
                                continue
                            }
                            // A single line taller than the page: show it clipped rather than loop.
                            to = from + 1
                        }
                        val ends = to == lineCount
                        val chars = TextSlices.charRange(layout.lineStarts, layout.textEnd, from, to)
                        val block = PageBlock.Lines(
                            itemIndex = item.itemIndex,
                            fromLine = from,
                            toLine = to,
                            clipTop = top,
                            clipHeight = ceil(layout.lineBottoms[to - 1]).toInt() - top,
                            frameTop = layout.frameTop,
                            frameBottom = layout.frameBottom,
                            gapAfter = if (ends) layout.gapAfter else 0,
                            startsItem = layout === item && from == 0,
                            endsItem = ends,
                            startChar = chars.first,
                            endChar = chars.last + 1
                        )
                        blocks.add(block)
                        used += block.height
                        from = to
                        if (!ends) {
                            newPage()
                            layout.remeasureFrom?.let { remeasure ->
                                layout = remeasure(to)
                                from = 0
                            }
                        }
                    }
                }
            }
        }
        newPage()
        if (pages.isEmpty()) pages.add(ReaderPage(chapterIndex, emptyList()))
        return pages
    }

    /** Page of [pages] to open for [itemIndex]: where the item starts, clamped to the chapter. */
    fun pageIndexOf(pages: List<ReaderPage>, itemIndex: Int): Int {
        if (itemIndex <= 0 || pages.isEmpty()) return 0
        val starts = pages.indexOfFirst { page ->
            page.blocks.any { it !is PageBlock.Header && it.itemIndex == itemIndex && it.startsItem }
        }
        if (starts >= 0) return starts
        val after = pages.indexOfFirst { page ->
            page.blocks.any { it !is PageBlock.Header && it.itemIndex >= itemIndex }
        }
        return if (after >= 0) after else pages.lastIndex
    }
}

/**
 * One page of the reader's pager. Chapters that are not paginated yet contribute a single
 * [Placeholder]; the pager keeps its position by key when they expand into pages.
 */
sealed class PagedEntry {
    abstract val chapterIndex: Int
    abstract val key: String

    data class Placeholder(override val chapterIndex: Int) : PagedEntry() {
        override val key get() = "pp:$chapterIndex"
    }

    data class Page(
        override val chapterIndex: Int,
        val pageIndex: Int,
        val pageCount: Int,
        val page: ReaderPage
    ) : PagedEntry() {
        override val key get() = "pg:$chapterIndex:$pageIndex"
    }

    companion object {
        fun build(chapterCount: Int, pages: Map<Int, List<ReaderPage>>): List<PagedEntry> {
            val entries = ArrayList<PagedEntry>()
            for (chapterIndex in 0 until chapterCount) {
                val chapterPages = pages[chapterIndex]
                if (chapterPages == null) {
                    entries.add(Placeholder(chapterIndex))
                } else {
                    chapterPages.forEachIndexed { i, page ->
                        entries.add(Page(chapterIndex, i, chapterPages.size, page))
                    }
                }
            }
            return entries
        }

        /** Pager index for [anchor], or null if its chapter is not paginated yet. */
        fun indexOf(entries: List<PagedEntry>, anchor: ReaderAnchor): Int? {
            val start = entries.indexOfFirst { it.chapterIndex == anchor.chapterIndex }
            if (start < 0) return null
            val first = entries[start] as? Page ?: return null
            val pages = (start until start + first.pageCount).map { (entries[it] as Page).page }
            return start + Paginator.pageIndexOf(pages, anchor.itemIndex)
        }
    }
}
