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
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.scrollia.app.helpers.book.BookDownloader

class BookDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `same edition has a deterministic filename`() {
        val first = BookDownloader.createFileName("Crime and Punishment", "edition:2554")
        val second = BookDownloader.createFileName("Crime and Punishment", "edition:2554")

        assertThat(first).isEqualTo(second)
        assertThat(first).endsWith(".epub")
    }

    @Test
    fun `same title in different editions cannot collide`() {
        val english = BookDownloader.createFileName("Crime and Punishment", "edition:2554")
        val spanish = BookDownloader.createFileName("Crime and Punishment", "edition:9582")

        assertThat(english).isNotEqualTo(spanish)
    }

    @Test
    fun `imported copies of the same title cannot collide`() {
        val first = BookDownloader.createImportedFileName("Same title")
        val second = BookDownloader.createImportedFileName("Same title")

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `publishing renames a complete temporary file`() {
        val booksFolder = temporaryFolder.newFolder("ebooks")
        val tempFile = temporaryFolder.newFile("download.part")
        tempFile.writeText("complete epub")

        val published = BookDownloader.publishDownloadedFile(
            tempFile,
            booksFolder,
            "unique.epub"
        )

        assertThat(published.readText()).isEqualTo("complete epub")
        assertThat(tempFile.exists()).isFalse()
        assertThat(File(booksFolder, "unique.epub").exists()).isTrue()
    }

    @Test
    fun `download copy leaves no partial file after publication`() {
        val booksFolder = temporaryFolder.newFolder("ebooks")
        val download = temporaryFolder.newFile("system-download.epub")
        download.writeText("complete epub")

        val published = BookDownloader.copyAndPublishDownloadedFile(
            download,
            booksFolder,
            "unique.epub"
        )

        assertThat(published.readText()).isEqualTo("complete epub")
        assertThat(File(booksFolder, "unique.epub.part").exists()).isFalse()
        assertThat(download.exists()).isTrue()
    }

    @Test(expected = IllegalStateException::class)
    fun `publishing never overwrites an existing book`() {
        val booksFolder = temporaryFolder.newFolder("ebooks")
        val existing = File(booksFolder, "unique.epub").apply { writeText("original") }
        val tempFile = temporaryFolder.newFile("download.part").apply { writeText("replacement") }

        try {
            BookDownloader.publishDownloadedFile(tempFile, booksFolder, "unique.epub")
        } finally {
            assertThat(existing.readText()).isEqualTo("original")
            assertThat(tempFile.readText()).isEqualTo("replacement")
        }
    }
}
