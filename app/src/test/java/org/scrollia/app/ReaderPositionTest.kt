package org.scrollia.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.scrollia.app.database.progress.ProgressData
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderAnchor
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderListEntry
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderPosition

class ReaderPositionTest {

    // Chapter 0 loaded (3 items), chapter 1 not loaded, chapter 2 loaded (2 items).
    private val entries = ReaderPosition.buildEntries(listOf(3, null, 2))

    @Test
    fun `buildEntries flattens loaded chapters and keeps placeholders`() {
        assertThat(entries).containsExactly(
            ReaderListEntry.ChapterHeader(0),
            ReaderListEntry.Item(0, 0),
            ReaderListEntry.Item(0, 1),
            ReaderListEntry.Item(0, 2),
            ReaderListEntry.Placeholder(1),
            ReaderListEntry.ChapterHeader(2),
            ReaderListEntry.Item(2, 0),
            ReaderListEntry.Item(2, 1),
        ).inOrder()
        assertThat(entries.map { it.key }.toSet()).hasSize(entries.size)
    }

    @Test
    fun `indexOf waits for unloaded chapters`() {
        assertThat(ReaderPosition.indexOf(entries, ReaderAnchor(1, 5))).isNull()
        assertThat(ReaderPosition.indexOf(entries, ReaderAnchor(7, 0))).isNull()
    }

    @Test
    fun `indexOf maps item zero to the header and clamps overflow`() {
        assertThat(ReaderPosition.indexOf(entries, ReaderAnchor(2, 0))).isEqualTo(5)
        assertThat(ReaderPosition.indexOf(entries, ReaderAnchor(2, 1))).isEqualTo(7)
        assertThat(ReaderPosition.indexOf(entries, ReaderAnchor(0, 2))).isEqualTo(3)
        assertThat(ReaderPosition.indexOf(entries, ReaderAnchor(0, 99))).isEqualTo(3)
    }

    @Test
    fun `anchor survives a round trip through the list`() {
        val anchor = ReaderAnchor(0, 2)
        val index = ReaderPosition.indexOf(entries, anchor)!!
        assertThat(ReaderPosition.anchorOf(entries[index])).isEqualTo(anchor)
    }

    @Test
    fun `placeholders never produce an anchor`() {
        assertThat(ReaderPosition.anchorOf(ReaderListEntry.Placeholder(1))).isNull()
        assertThat(ReaderPosition.anchorOf(ReaderListEntry.ChapterHeader(1)))
            .isEqualTo(ReaderAnchor(1, 0))
    }

    @Test
    fun `chapterStartIndex finds headers and placeholders`() {
        assertThat(ReaderPosition.chapterStartIndex(entries, 1)).isEqualTo(4)
        assertThat(ReaderPosition.chapterStartIndex(entries, 2)).isEqualTo(5)
        assertThat(ReaderPosition.chapterStartIndex(entries, 3)).isEqualTo(-1)
    }

    @Test
    fun `fractions are bounded`() {
        assertThat(ReaderPosition.chapterFraction(5, 10, 0.5f)).isWithin(1e-6f).of(0.55f)
        assertThat(ReaderPosition.chapterFraction(0, 0)).isEqualTo(0f)
        assertThat(ReaderPosition.bookFraction(1, 0.5f, 4)).isWithin(1e-6f).of(0.375f)
        assertThat(ReaderPosition.bookFraction(9, 2f, 4)).isEqualTo(1f)
    }

    @Test
    fun `finished only near the end of the last chapter`() {
        assertThat(ReaderPosition.isFinished(2, 99, 4, 100)).isFalse()
        assertThat(ReaderPosition.isFinished(3, 50, 4, 100)).isFalse()
        assertThat(ReaderPosition.isFinished(3, 94, 4, 100)).isTrue()
        assertThat(ReaderPosition.isFinished(3, 99, 4, 100)).isTrue()
    }

    @Test
    fun `single chapter books are not finished at the start`() {
        assertThat(ReaderPosition.isFinished(0, 0, 1, 300)).isFalse()
        assertThat(ReaderPosition.isFinished(0, 299, 1, 300)).isTrue()
    }

    @Test
    fun `finished is retained when revisiting earlier pages`() {
        assertThat(ReaderPosition.retainFinished(wasFinished = true, isFinished = false)).isTrue()
        assertThat(ReaderPosition.retainFinished(wasFinished = false, isFinished = true)).isTrue()
        assertThat(ReaderPosition.retainFinished(wasFinished = false, isFinished = false)).isFalse()
    }

    @Test
    fun `progress percent caps below 100 until finished`() {
        val reading = ProgressData(libraryItemId = 1, lastChapterIndex = 0, progress = 0.999f)
        assertThat(reading.progressPercent()).isEqualTo(99)
        assertThat(reading.copy(finished = true).progressPercent()).isEqualTo(100)
    }
}
