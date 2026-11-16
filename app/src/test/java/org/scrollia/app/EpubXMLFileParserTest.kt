/**
 * Copyright 2026-Present Scrollia contributors
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

package org.scrollia.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.jsoup.Jsoup
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.scrollia.app.epub.EpubXMLFileParser
import org.scrollia.app.epub.models.HtmlSpan
import org.scrollia.app.epub.models.ReaderItem
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class EpubXMLFileParserTest {

    @Test
    fun expandNonVoidSelfClosingTags_expandsAnchorAndKeepsBreak() {
        val input = """<p>One</p><a id="c1"/><p>Two</p><br/><img src="x.jpg"/>"""
        val out = EpubXMLFileParser.expandNonVoidSelfClosingTags(input)
        assertThat(out).contains("""<a id="c1"></a>""")
        assertThat(out).contains("<br/>")
        assertThat(out).contains("""<img src="x.jpg"/>""")
    }

    @Test
    fun selfClosingAnchorDoesNotMergeFollowingParagraphs() {
        val xhtml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <body>
                <h1>Chapter</h1>
                <a id="p1"/>
                <p>First paragraph.</p>
                <a id="p2"/>
                <p>Second paragraph.</p>
              </body>
            </html>
        """.trimIndent().toByteArray()

        val parser = EpubXMLFileParser(
            fileAbsolutePath = "chapter.xhtml",
            data = xhtml,
            zipFile = emptyMap(),
        )
        val paragraphs = parser.parseAsDocument().body
            .filterIsInstance<ReaderItem.Text>()
            .map { it.plainText() }
        // h1 may appear as its own text item; the regression is fused <p> blocks.
        assertThat(paragraphs.any { it.contains("First paragraph") }).isTrue()
        assertThat(paragraphs.any { it.contains("Second paragraph") }).isTrue()
        val firstIdx = paragraphs.indexOfFirst { it.contains("First paragraph") }
        val secondIdx = paragraphs.indexOfFirst { it.contains("Second paragraph") }
        assertThat(firstIdx).isLessThan(secondIdx)
        assertThat(paragraphs[firstIdx]).doesNotContain("Second paragraph")
    }

    @Test
    fun quijoteSample_selfClosingAnchorsDoNotSwallowChapter() {
        val bytes = javaClass.getResourceAsStream("/quijote-sample.xhtml")!!.readBytes()
        // Old HTML stream parse: an <a id> can hold tens of thousands of characters.
        val htmlDoc = Jsoup.parse(ByteArrayInputStream(bytes), "UTF-8", "")
        val htmlMax = htmlDoc.select("a").maxOfOrNull { it.text().length } ?: 0
        assertThat(htmlMax).isGreaterThan(5_000)

        val parsed = EpubXMLFileParser(
            fileAbsolutePath = "quijote-sample.xhtml",
            data = bytes,
            zipFile = emptyMap(),
        ).parseAsDocument()
        val texts = parsed.body.filterIsInstance<ReaderItem.Text>().map { it.plainText() }
        assertThat(texts.size).isGreaterThan(10)
        assertThat(texts.maxOf { it.length }).isLessThan(5_000)
        assertThat(
            texts.any {
                it.contains("Juan Gallo de Andrada") || it.contains("TASA") || it.length > 40
            }
        ).isTrue()
    }

    private fun ReaderItem.Text.plainText(): String =
        spans.joinToString("") { span ->
            when (span) {
                is HtmlSpan.Text -> span.text
                is HtmlSpan.Tag ->
                    span.children.joinToString("") { child ->
                        when (child) {
                            is HtmlSpan.Text -> child.text
                            else -> ""
                        }
                    }
            }
        }
}
