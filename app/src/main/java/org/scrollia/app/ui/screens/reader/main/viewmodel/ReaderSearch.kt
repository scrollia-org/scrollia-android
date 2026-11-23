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

import org.scrollia.app.epub.asAnnotatedString
import org.scrollia.app.epub.models.ReaderItem

/** One match from an in-book search, ready to jump to with [ReaderAnchor]. */
data class ReaderSearchHit(
    val chapterIndex: Int,
    val chapterTitle: String,
    val itemIndex: Int,
    val snippet: String
)

internal fun ReaderItem.plainText(): String = when (this) {
    is ReaderItem.Text -> spans.asAnnotatedString().text
    is ReaderItem.Blockquote -> spans.asAnnotatedString(collapseWhitespace = false).text
    is ReaderItem.CodeBlock -> code
    is ReaderItem.Image -> ""
}

/**
 * Find up to [maxHits] case-insensitive matches of [query] in [body].
 * One hit per item (first match in that paragraph).
 */
internal fun findSearchHits(
    chapterIndex: Int,
    chapterTitle: String,
    body: List<ReaderItem>,
    query: String,
    maxHits: Int,
    snippetRadius: Int = 48
): List<ReaderSearchHit> {
    if (query.isBlank() || maxHits <= 0) return emptyList()
    val needle = query.trim()
    val hits = mutableListOf<ReaderSearchHit>()
    for ((itemIndex, item) in body.withIndex()) {
        if (hits.size >= maxHits) break
        val text = item.plainText()
        if (text.isEmpty()) continue
        val at = text.indexOf(needle, ignoreCase = true)
        if (at < 0) continue
        val start = (at - snippetRadius).coerceAtLeast(0)
        val end = (at + needle.length + snippetRadius).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        hits += ReaderSearchHit(
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            itemIndex = itemIndex,
            snippet = prefix + text.substring(start, end).replace('\n', ' ') + suffix
        )
    }
    return hits
}
