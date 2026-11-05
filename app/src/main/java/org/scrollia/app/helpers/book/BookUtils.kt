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

package org.scrollia.app.helpers.book

import androidx.navigation.NavController
import org.scrollia.app.api.models.Author
import org.scrollia.app.database.library.LibraryItem
import org.scrollia.app.ui.navigation.Screens
import java.util.Locale

object BookUtils {

    /**
     * Converts the list of authors into a single string.
     *
     * @param authors List of authors.
     * @return String representation of the authors.
     */
    fun getAuthorsAsString(authors: List<Author>): String {
        return if (authors.isEmpty()) {
            "Unknown Author"
        } else {
            authors.joinToString(", ") { fixAuthorName(it.name) }
        }
    }

    /**
     * For some weird reasons, gutenberg gives name of authors in
     * reversed, where first name and last are separated by a comma
     * Eg: "Fyodor Dostoyevsky" becomes "Dostoyevsky, Fyodor", This
     * function fixes that and returns name in correct format.
     *
     * @param name Name of the author.
     * @return Name of the author in correct format.
     */
    private fun fixAuthorName(name: String): String {
        return name.split(",").reversed().joinToString(" ") { it.trim() }
    }

    /**
     * Converts the list of languages into a single string.
     *
     * @param languages List of languages.
     * @return String representation of the languages.
     */
    fun getLanguagesAsString(languages: List<String>): String {
        return languages.joinToString(", ") { Locale.forLanguageTag(it).displayLanguage }
    }

    /** Opens the book with Scrollia's reader. */
    fun openBookFile(
        libraryItem: LibraryItem,
        navController: NavController
    ) {
        navController.navigate(Screens.ReaderDetailScreen.withLibraryItemId(libraryItem.id.toString()))
    }
}