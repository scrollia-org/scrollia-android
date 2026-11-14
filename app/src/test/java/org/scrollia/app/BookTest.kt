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

package org.scrollia.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.scrollia.app.api.models.Book
import org.scrollia.app.api.models.Edition
import org.scrollia.app.api.models.Formats
import org.scrollia.app.api.models.withEdition

class BookTest {
    private val english = Edition(
        id = 11,
        title = "Alice's Adventures in Wonderland",
        languages = listOf("en"),
        downloadCount = 100,
        formats = Formats(imagejpeg = "en.jpg")
    )
    private val spanish = Edition(
        id = 1000001,
        title = "Las aventuras de Alicia en el País de las Maravillas",
        languages = listOf("es"),
        downloadCount = 0,
        formats = Formats(imagejpeg = "es.jpg")
    )
    private val book = Book(
        authors = emptyList(),
        bookshelves = emptyList(),
        copyright = false,
        downloadCount = 500,
        editions = listOf(english, spanish),
        formats = Formats(imagejpeg = "work.jpg"),
        id = spanish.id,
        languages = listOf("en", "es"),
        mediaType = "Text",
        subjects = emptyList(),
        title = spanish.title,
        translators = emptyList()
    )

    @Test
    fun `withEdition applies selected edition when its id matches the book id`() {
        val selected = book.withEdition(spanish.id)

        assertThat(selected.id).isEqualTo(spanish.id)
        assertThat(selected.title).isEqualTo(spanish.title)
        assertThat(selected.languages).containsExactly("es")
        assertThat(selected.downloadCount).isEqualTo(0)
        assertThat(selected.formats.imagejpeg).isEqualTo("es.jpg")
    }

    @Test
    fun `withEdition applies a different selected edition`() {
        val selected = book.withEdition(english.id)

        assertThat(selected.id).isEqualTo(english.id)
        assertThat(selected.title).isEqualTo(english.title)
        assertThat(selected.languages).containsExactly("en")
        assertThat(selected.downloadCount).isEqualTo(100)
        assertThat(selected.formats.imagejpeg).isEqualTo("en.jpg")
    }
}
