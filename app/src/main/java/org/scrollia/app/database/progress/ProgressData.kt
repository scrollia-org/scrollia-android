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


package org.scrollia.app.database.progress

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * Data class for storing the reading progress of a library item (book).
 *
 * @param libraryItemId The ID of the library item.
 * @param lastChapterIndex The index of the last chapter read.
 * @param lastChapterOffset Legacy pixel offset inside the chapter (schema <= 5). No longer
 *   read: pixel offsets break on typography changes. New rows store 0.
 * @param lastReadTime The time when the last chapter was read.
 * @param lastChapterItemIndex Index of the first visible reader item (paragraph, image,
 *   block) inside the chapter: the semantic reading anchor.
 * @param progress Whole-book progress estimate in 0..1, stored so lists can show it without
 *   parsing the EPUB.
 * @param finished Whether the reader reached the end of the last chapter.
 */
@Entity(tableName = "reader_table")
data class ProgressData(
    @ColumnInfo(name = "library_item_id") val libraryItemId: Int,
    @ColumnInfo(name = "last_chapter_index") val lastChapterIndex: Int,
    @ColumnInfo(name = "last_chapter_offset") val lastChapterOffset: Int = 0,
    // Added in database schema version 5
    @ColumnInfo(
        name = "last_read_time",
        defaultValue = "0"
    ) val lastReadTime: Long = System.currentTimeMillis(),
    // Added in database schema version 6
    @ColumnInfo(
        name = "last_chapter_item_index",
        defaultValue = "0"
    ) val lastChapterItemIndex: Int = 0,
    @ColumnInfo(name = "progress", defaultValue = "0") val progress: Float = 0f,
    @ColumnInfo(name = "finished", defaultValue = "0") val finished: Boolean = false,
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    /** Whole-book progress as a whole percentage (0..100); 100 once finished. */
    fun progressPercent(): Int = if (finished) 100 else (progress * 100f).toInt().coerceIn(0, 99)

    fun getProgressPercent(): String =
        String.format(locale = Locale.US, format = "%d", progressPercent())
}