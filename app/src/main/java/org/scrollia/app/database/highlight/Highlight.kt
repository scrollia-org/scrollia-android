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

package org.scrollia.app.database.highlight

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A highlighted span inside one paragraph ([itemIndex]) of a chapter.
 * Offsets are into the plain text produced by [org.scrollia.app.ui.screens.reader.main.viewmodel.plainText].
 */
@Entity(
    tableName = "highlights",
    indices = [Index(value = ["library_item_id"])]
)
data class Highlight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "library_item_id")
    val libraryItemId: Int,
    @ColumnInfo(name = "chapter_index")
    val chapterIndex: Int,
    @ColumnInfo(name = "item_index")
    val itemIndex: Int,
    @ColumnInfo(name = "start_offset")
    val startOffset: Int,
    @ColumnInfo(name = "end_offset")
    val endOffset: Int,
    @ColumnInfo(name = "quote")
    val quote: String,
    /** ARGB; default is a translucent gold that fits Papel y Tinta. */
    @ColumnInfo(name = "color")
    val color: Int = DEFAULT_COLOR,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** #B08D3E gold at ~40% opacity. */
        const val DEFAULT_COLOR: Int = 0x66B08D3E
    }
}
