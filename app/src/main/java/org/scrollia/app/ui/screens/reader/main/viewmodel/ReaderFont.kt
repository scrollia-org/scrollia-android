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

import androidx.annotation.Keep
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import org.scrollia.app.R
import org.scrollia.app.ui.theme.interFont
import org.scrollia.app.ui.theme.literataFont

@Keep
sealed class ReaderFont(val id: String, val name: String, val fontFamily: FontFamily) {

    companion object {
        private val fontMap by lazy {
            ReaderFont::class.sealedSubclasses
                .mapNotNull { it.objectInstance }
                .associateBy { it.id }
        }

        fun getAllFonts() = fontMap.values.toList()

        // Fallback to the default font instead of crashing when a stored
        // preference id no longer exists (e.g. removed fonts after an update).
        fun getFontById(id: String) = fontMap[id] ?: Literata
        fun getFontByName(name: String) = getAllFonts().find { it.name == name } ?: Literata
    }

    @Keep
    data object Literata : ReaderFont("literata", "Literata", literataFont)

    @Keep
    data object Inter : ReaderFont("inter", "Inter", interFont)

    @Keep
    data object Dyslexic : ReaderFont(
        "dyslexic", "OpenDyslexic", FontFamily(
            Font(R.font.reader_opendyslexic_font)
        )
    )

    @Keep
    data object System : ReaderFont("system", "System Default", FontFamily.Default)

    @Keep
    data object Serif : ReaderFont("serif", "Serif", FontFamily.Serif)
}
