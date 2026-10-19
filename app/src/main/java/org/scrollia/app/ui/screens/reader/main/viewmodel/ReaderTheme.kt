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
import org.scrollia.app.ui.theme.ReaderNightBackground
import org.scrollia.app.ui.theme.ReaderNightText
import org.scrollia.app.ui.theme.ReaderPaperBackground
import org.scrollia.app.ui.theme.ReaderPaperText
import org.scrollia.app.ui.theme.ReaderSepiaBackground
import org.scrollia.app.ui.theme.ReaderSepiaText

/**
 * Reading themes (docs/DESIGN.md §3.6). Independent from the app theme:
 * the choice belongs to the reading session, not to the system dark mode.
 */
enum class ReaderTheme(val id: String, val backgroundColor: Color, val textColor: Color) {
    Paper("paper", ReaderPaperBackground, ReaderPaperText),
    Sepia("sepia", ReaderSepiaBackground, ReaderSepiaText),
    Night("night", ReaderNightBackground, ReaderNightText);

    companion object {
        fun getById(id: String?) = entries.find { it.id == id } ?: Paper
    }
}
