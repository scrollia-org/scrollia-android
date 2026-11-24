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

package org.scrollia.app.helpers

import android.content.Context
import org.scrollia.app.BuildConfig
import org.scrollia.app.R
import java.util.Locale

/**
 * Opens the system mail client with a prefilled message to
 * [Constants.DEV_EMAIL]. No data leaves the device until the user sends.
 * Field labels stay in English so reports stay easy to triage; the subject
 * and UI chrome follow the app language.
 */
object FeedbackMail {

    private const val MAX_QUOTE_CHARS = 400

    fun openGeneral(context: Context) {
        Utils.openEmail(
            context = context,
            email = Constants.DEV_EMAIL,
            subject = context.getString(
                R.string.feedback_subject_general,
                BuildConfig.VERSION_NAME
            ),
            body = buildBody(context, quote = null, bookLines = emptyList())
        )
    }

    fun openFromReader(
        context: Context,
        title: String?,
        author: String?,
        language: String?,
        chapterTitle: String?,
        chapterIndex: Int?,
        chapterCount: Int?,
        quote: String? = null
    ) {
        val bookLines = buildList {
            title?.takeIf { it.isNotBlank() }?.let { add("Title: $it") }
            author?.takeIf { it.isNotBlank() }?.let { add("Author: $it") }
            language?.takeIf { it.isNotBlank() }?.let { add("Book language: $it") }
            val chapter = formatChapter(chapterTitle, chapterIndex, chapterCount)
            if (chapter != null) add("Chapter: $chapter")
        }
        Utils.openEmail(
            context = context,
            email = Constants.DEV_EMAIL,
            subject = context.getString(
                R.string.feedback_subject_reader,
                BuildConfig.VERSION_NAME
            ),
            body = buildBody(context, quote = quote, bookLines = bookLines)
        )
    }

    private fun formatChapter(
        title: String?,
        index: Int?,
        count: Int?
    ): String? {
        val position = when {
            index != null && count != null && count > 0 ->
                "${index + 1}/$count"
            index != null -> "${index + 1}"
            else -> null
        }
        val name = title?.takeIf { it.isNotBlank() }
        return when {
            position != null && name != null -> "$position — $name"
            position != null -> position
            name != null -> name
            else -> null
        }
    }

    private fun buildBody(
        context: Context,
        quote: String?,
        bookLines: List<String>
    ): String {
        val uiLang = Locale.getDefault().toLanguageTag()
        val lines = mutableListOf<String>()
        lines += "Scrollia ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        lines += "UI language: $uiLang"
        if (bookLines.isNotEmpty()) {
            lines += ""
            lines.addAll(bookLines)
        }
        val clipped = quote?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            if (text.length <= MAX_QUOTE_CHARS) text
            else text.take(MAX_QUOTE_CHARS).trimEnd() + "…"
        }
        if (clipped != null) {
            lines += ""
            lines += "Selected text:"
            lines += "\"$clipped\""
        }
        lines += ""
        lines += context.getString(R.string.feedback_body_prompt)
        lines += ""
        return lines.joinToString("\n")
    }
}
