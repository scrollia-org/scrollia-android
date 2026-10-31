package org.scrollia.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.scrollia.app.helpers.book.BookImporter
import org.scrollia.app.helpers.book.BookImporter.ImportException
import org.scrollia.app.helpers.book.BookImporter.ParsedBook
import org.scrollia.app.helpers.book.BookImporter.Reason

class BookImporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val bytes = Random(7).nextBytes(200_000)
    private val book = ParsedBook("Moby-Dick: or, The Whale", "Herman Melville")

    /** A provider stream that is not a FileInputStream and returns short reads. */
    private class ProviderStream(
        private val data: ByteArray,
        private val failAfter: Int? = null,
        private val onRead: (Int) -> Unit = {}
    ) : InputStream() {
        private var position = 0

        override fun read(): Int = if (position < data.size) data[position++].toInt() and 0xff else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (failAfter != null && position >= failAfter) throw IOException("provider revoked")
            if (position >= data.size) return -1
            val count = minOf(length, 7_000, data.size - position)
            System.arraycopy(data, position, buffer, offset, count)
            position += count
            onRead(position)
            return count
        }
    }

    private class Registry {
        val rows = mutableMapOf<Int, Pair<ParsedBook, File>>()
        private var nextId = 41

        fun register(book: ParsedBook, file: File): Int {
            val id = ++nextId
            rows[id] = book to file
            return id
        }

        fun unregister(id: Int) {
            rows.remove(id)
        }
    }

    private fun booksFolder() = File(temporaryFolder.root, "ebooks")

    private suspend fun import(
        input: InputStream = ProviderStream(bytes),
        registry: Registry = Registry(),
        totalBytes: Long? = bytes.size.toLong(),
        parse: suspend (File) -> ParsedBook = { book },
        register: (ParsedBook, File) -> Int = registry::register,
        publish: ((File, File, String) -> File)? = null,
        onProgress: (Float?) -> Unit = {}
    ): Int = if (publish == null) {
        BookImporter.importStream(
            input, totalBytes, booksFolder(), parse, register, registry::unregister, onProgress
        )
    } else {
        BookImporter.importStream(
            input, totalBytes, booksFolder(), parse, register, registry::unregister, onProgress,
            publish
        )
    }

    private suspend fun expectFailure(reason: Reason, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $reason")
        } catch (exc: ImportException) {
            assertThat(exc.reason).isEqualTo(reason)
        }
    }

    private fun folderContents(): List<String> = booksFolder().list()?.sorted().orEmpty()

    @Test
    fun `imports a non-file provider stream and registers the published copy`() = runTest {
        val registry = Registry()
        var parsedFrom: File? = null

        val id = import(registry = registry, parse = { file ->
            parsedFrom = file
            assertThat(file.readBytes()).isEqualTo(bytes)
            book
        })

        val (registered, file) = registry.rows.getValue(id)
        assertThat(registered).isEqualTo(book)
        assertThat(file.parentFile).isEqualTo(booksFolder())
        assertThat(file.readBytes()).isEqualTo(bytes)
        assertThat(file.name).endsWith(".epub")
        assertThat(file.name).doesNotContain(":")
        assertThat(parsedFrom!!.name).endsWith(".part")
        assertThat(folderContents()).containsExactly(file.name)
    }

    @Test
    fun `importing the same book twice never overwrites the first copy`() = runTest {
        val registry = Registry()
        val firstId = import(registry = registry)
        val secondBytes = bytes.reversedArray()
        val secondId = import(input = ProviderStream(secondBytes), registry = registry)

        val first = registry.rows.getValue(firstId).second
        val second = registry.rows.getValue(secondId).second
        assertThat(first.name).isNotEqualTo(second.name)
        assertThat(first.readBytes()).isEqualTo(bytes)
        assertThat(second.readBytes()).isEqualTo(secondBytes)
        assertThat(folderContents()).hasSize(2)
    }

    @Test
    fun `read failure during copy leaves nothing behind`() = runTest {
        val registry = Registry()
        expectFailure(Reason.UNREADABLE) {
            import(input = ProviderStream(bytes, failAfter = 50_000), registry = registry)
        }
        assertThat(folderContents()).isEmpty()
        assertThat(registry.rows).isEmpty()
    }

    @Test
    fun `bytes that do not parse as an EPUB are rejected`() = runTest {
        val registry = Registry()
        expectFailure(Reason.NOT_EPUB) {
            import(registry = registry, parse = { throw IllegalStateException("no container.xml") })
        }
        assertThat(folderContents()).isEmpty()
        assertThat(registry.rows).isEmpty()
    }

    @Test
    fun `an empty document is not an EPUB`() = runTest {
        expectFailure(Reason.NOT_EPUB) { import(input = ProviderStream(ByteArray(0))) }
        assertThat(folderContents()).isEmpty()
    }

    @Test
    fun `publication failure removes the staging copy`() = runTest {
        val registry = Registry()
        expectFailure(Reason.STORAGE) {
            import(registry = registry, publish = { _, _, _ -> throw IllegalStateException("rename") })
        }
        assertThat(folderContents()).isEmpty()
        assertThat(registry.rows).isEmpty()
    }

    @Test
    fun `registration failure removes the published file but keeps existing books`() = runTest {
        booksFolder().mkdirs()
        val existing = File(booksFolder(), "Existing_Book_1234.epub").apply { writeText("keep") }

        expectFailure(Reason.DATABASE) {
            import(register = { _, _ -> throw IllegalStateException("disk I/O error") })
        }

        assertThat(folderContents()).containsExactly(existing.name)
        assertThat(existing.readText()).isEqualTo("keep")
    }

    @Test
    fun `cancelling during the copy cleans up`() = runTest {
        val registry = Registry()
        val job = CompletableDeferred<Deferred<Int>>()
        val import = async {
            import(
                input = ProviderStream(bytes) { position ->
                    if (position > 20_000) job.getCompleted().cancel()
                },
                registry = registry
            )
        }
        job.complete(import)
        import.join()

        assertThat(import.isCancelled).isTrue()
        assertThat(folderContents()).isEmpty()
        assertThat(registry.rows).isEmpty()
    }

    @Test
    fun `cancelling while the row is written undoes the row and the file`() = runTest {
        val registry = Registry()
        val job = CompletableDeferred<Deferred<Int>>()
        val import = async {
            import(registry = registry, register = { parsed, file ->
                job.getCompleted().cancel()
                registry.register(parsed, file)
            })
        }
        job.complete(import)
        import.join()

        assertThat(import.isCancelled).isTrue()
        assertThat(folderContents()).isEmpty()
        assertThat(registry.rows).isEmpty()
    }

    @Test
    fun `progress is determinate only when the size is known`() = runTest {
        val known = mutableListOf<Float?>()
        import(onProgress = { known += it })
        assertThat(known.first()).isEqualTo(0f)
        assertThat(known.last()).isEqualTo(1f)
        assertThat(known.filterNotNull()).isInOrder()
        assertThat(known).doesNotContain(null)

        val unknown = mutableListOf<Float?>()
        import(totalBytes = null, onProgress = { unknown += it })
        assertThat(unknown).containsExactly(null)
    }
}
