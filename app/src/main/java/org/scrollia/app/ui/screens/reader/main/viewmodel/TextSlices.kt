package org.scrollia.app.ui.screens.reader.main.viewmodel

import androidx.compose.ui.text.AnnotatedString

/**
 * Character ranges of page slices. A slice of a paragraph is a run of whole lines of a measured
 * layout; these helpers turn line indices into text offsets and cut the styled text, keeping
 * every span and annotation clipped to the slice.
 */
object TextSlices {

    /**
     * Characters [first, last] covered by lines [fromLine, toLine), given where each line
     * starts and where the last one ends. Empty range (0 until 0) when offsets are unknown.
     */
    fun charRange(lineStarts: IntArray, textEnd: Int, fromLine: Int, toLine: Int): IntRange {
        if (lineStarts.isEmpty() || fromLine >= toLine) return 0 until 0
        val start = lineStarts[fromLine.coerceIn(0, lineStarts.lastIndex)]
        val end = if (toLine < lineStarts.size) lineStarts[toLine] else textEnd
        return start until end.coerceAtLeast(start)
    }

    /** What a slice lays out: the text from [start] to the end of the paragraph. */
    fun layoutText(text: AnnotatedString, start: Int): AnnotatedString =
        text.subSequence(start.coerceIn(0, text.length), text.length)

    /** The characters a slice shows, styled. */
    fun visibleText(text: AnnotatedString, start: Int, end: Int): AnnotatedString {
        val from = start.coerceIn(0, text.length)
        return text.subSequence(from, end.coerceIn(from, text.length))
    }
}
