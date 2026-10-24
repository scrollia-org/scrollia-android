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
     * @param lineTops / lineBottoms Line boxes of the full item layout.
     * @param frameTop / frameBottom Space drawn around every slice (block padding).
     * @param gapAfter Space after the item, only when it ends on the page.
     */
    class Lines(
        override val itemIndex: Int,
        val lineTops: FloatArray,
        val lineBottoms: FloatArray,
        val frameTop: Int = 0,
        val frameBottom: Int = 0,
        val gapAfter: Int = 0
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
     * Lines [fromLine, toLine) of a text item. The full item is laid out and shown through a
     * window [clipTop, clipTop + clipHeight), so line breaks, justification and hyphenation are
     * exactly those of the unsplit paragraph.
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
        val endsItem: Boolean
    ) : PageBlock() {
        override val height get() = frameTop + clipHeight + frameBottom + gapAfter
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
                    val lineCount = item.lineCount
                    var from = 0
                    while (from < lineCount) {
                        val available = pageHeight - used - item.frameTop - item.frameBottom
                        val top = floor(item.lineTops[from]).toInt()
                        var to = from
                        while (to < lineCount &&
                            ceil(item.lineBottoms[to]).toInt() - top <= available
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
                        val block = PageBlock.Lines(
                            itemIndex = item.itemIndex,
                            fromLine = from,
                            toLine = to,
                            clipTop = top,
                            clipHeight = ceil(item.lineBottoms[to - 1]).toInt() - top,
                            frameTop = item.frameTop,
                            frameBottom = item.frameBottom,
                            gapAfter = if (ends) item.gapAfter else 0,
                            startsItem = from == 0,
                            endsItem = ends
                        )
                        blocks.add(block)
                        used += block.height
                        from = to
                        if (!ends) newPage()
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
