package org.scrollia.app.ui.screens.reader.main.viewmodel

/**
 * Semantic reading position: a chapter and the index of a [org.scrollia.app.epub.models.ReaderItem]
 * (paragraph, image, block) inside it. Unlike a pixel offset it survives font size, line height,
 * font family and theme changes.
 */
data class ReaderAnchor(val chapterIndex: Int, val itemIndex: Int)

/**
 * One row of the reader's flattened LazyColumn. Every chapter contributes either a
 * [Placeholder] (body not parsed yet) or a [ChapterHeader] followed by one [Item] per
 * reader item.
 */
sealed class ReaderListEntry {
    abstract val chapterIndex: Int
    abstract val key: String

    data class Placeholder(override val chapterIndex: Int) : ReaderListEntry() {
        override val key get() = "p:$chapterIndex"
    }

    data class ChapterHeader(override val chapterIndex: Int) : ReaderListEntry() {
        override val key get() = "h:$chapterIndex"
    }

    data class Item(override val chapterIndex: Int, val itemIndex: Int) : ReaderListEntry() {
        override val key get() = "i:$chapterIndex:$itemIndex"
    }
}

/**
 * What is on screen right now, in semantic terms (both reading modes report it).
 *
 * @param anchor First visible item, or null if it is an unloaded chapter placeholder.
 * @param lastVisible Last visible item (null for placeholders).
 * @param firstChapterIndex Chapter of the first visible row or page.
 * @param visibleChapters Chapters with at least one visible row.
 * @param chapterFraction How far (0..1) the reader is into the first visible chapter.
 */
data class VisiblePosition(
    val anchor: ReaderAnchor?,
    val lastVisible: ReaderAnchor?,
    val firstChapterIndex: Int,
    val visibleChapters: Set<Int>,
    val chapterFraction: Float
)

object ReaderPosition {

    /** Fraction of the last chapter that must be reached for a book to count as finished. */
    const val FINISHED_THRESHOLD = 0.95f

    /**
     * Builds the flattened list. [chapterItemCounts] has one entry per chapter: the number of
     * reader items if the chapter body is loaded, or null if it is not.
     */
    fun buildEntries(chapterItemCounts: List<Int?>): List<ReaderListEntry> {
        val entries = ArrayList<ReaderListEntry>()
        chapterItemCounts.forEachIndexed { chapterIndex, count ->
            if (count == null) {
                entries.add(ReaderListEntry.Placeholder(chapterIndex))
            } else {
                entries.add(ReaderListEntry.ChapterHeader(chapterIndex))
                for (i in 0 until count) entries.add(ReaderListEntry.Item(chapterIndex, i))
            }
        }
        return entries
    }

    /** Index of the first entry belonging to [chapterIndex] (its header or placeholder). */
    fun chapterStartIndex(entries: List<ReaderListEntry>, chapterIndex: Int): Int =
        entries.indexOfFirst { it.chapterIndex == chapterIndex }

    /**
     * List index to scroll to for [anchor], or null if the anchor's chapter is not loaded yet.
     * Item 0 maps to the chapter header so that the title stays visible; out-of-range items are
     * clamped to the last item of the chapter.
     */
    fun indexOf(entries: List<ReaderListEntry>, anchor: ReaderAnchor): Int? {
        val start = chapterStartIndex(entries, anchor.chapterIndex)
        if (start < 0 || entries[start] is ReaderListEntry.Placeholder) return null
        if (anchor.itemIndex <= 0) return start
        var last = start
        for (i in start + 1 until entries.size) {
            val entry = entries[i]
            if (entry !is ReaderListEntry.Item || entry.chapterIndex != anchor.chapterIndex) break
            last = i
            if (entry.itemIndex == anchor.itemIndex) return i
        }
        return last
    }

    /** Anchor for a list entry, or null for placeholders (never persist an unloaded position). */
    fun anchorOf(entry: ReaderListEntry?): ReaderAnchor? = when (entry) {
        is ReaderListEntry.ChapterHeader -> ReaderAnchor(entry.chapterIndex, 0)
        is ReaderListEntry.Item -> ReaderAnchor(entry.chapterIndex, entry.itemIndex)
        else -> null
    }

    /**
     * Fraction (0..1) of a chapter read when the reader is at [itemIndex] of [itemCount], with
     * [withinItem] being how much of that item has scrolled past (0..1).
     */
    fun chapterFraction(itemIndex: Int, itemCount: Int, withinItem: Float = 0f): Float {
        if (itemCount <= 0) return 0f
        return ((itemIndex + withinItem.coerceIn(0f, 1f)) / itemCount).coerceIn(0f, 1f)
    }

    /** Whole-book progress estimate (0..1), weighting every chapter equally. */
    fun bookFraction(chapterIndex: Int, chapterFraction: Float, totalChapters: Int): Float {
        if (totalChapters <= 0) return 0f
        return ((chapterIndex + chapterFraction.coerceIn(0f, 1f)) / totalChapters).coerceIn(0f, 1f)
    }

    /**
     * A book is finished when the *last visible* item is in the last chapter and at least
     * [FINISHED_THRESHOLD] of that chapter has been reached.
     */
    fun isFinished(
        lastVisibleChapter: Int,
        lastVisibleItemIndex: Int,
        totalChapters: Int,
        lastChapterItemCount: Int
    ): Boolean {
        if (totalChapters <= 0 || lastVisibleChapter != totalChapters - 1) return false
        if (lastChapterItemCount <= 0) return true
        return (lastVisibleItemIndex + 1).toFloat() / lastChapterItemCount >= FINISHED_THRESHOLD
    }
}
