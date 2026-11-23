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

package org.scrollia.app.ui.screens.reader.main.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.scrollia.app.database.highlight.Highlight
import org.scrollia.app.epub.models.ReaderItem

data class HighlightAnchor(
    val chapterIndex: Int,
    val itemIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val quote: String
)

/**
 * Find the first paragraph in [chapterIndex] that contains [quote] as a
 * contiguous substring of its plain text. Selection that spans paragraphs
 * is not supported yet.
 */
fun locateHighlight(
    chapterIndex: Int,
    body: List<ReaderItem>,
    quote: String
): HighlightAnchor? {
    val needle = quote.trim()
    if (needle.isEmpty()) return null
    for ((itemIndex, item) in body.withIndex()) {
        val text = item.plainText()
        val at = text.indexOf(needle)
        if (at >= 0) {
            return HighlightAnchor(
                chapterIndex = chapterIndex,
                itemIndex = itemIndex,
                startOffset = at,
                endOffset = at + needle.length,
                quote = needle
            )
        }
    }
    return null
}

fun AnnotatedString.withHighlights(highlights: List<Highlight>): AnnotatedString {
    if (highlights.isEmpty()) return this
    val builder = AnnotatedString.Builder(this)
    for (h in highlights) {
        val start = h.startOffset.coerceIn(0, length)
        val end = h.endOffset.coerceIn(start, length)
        if (start >= end) continue
        builder.addStyle(SpanStyle(background = Color(h.color)), start, end)
    }
    return builder.toAnnotatedString()
}
