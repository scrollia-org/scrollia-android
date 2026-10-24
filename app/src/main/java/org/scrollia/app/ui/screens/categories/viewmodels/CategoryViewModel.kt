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

package org.scrollia.app.ui.screens.categories.viewmodels


import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.scrollia.app.api.BookAPI
import org.scrollia.app.api.models.Book
import org.scrollia.app.helpers.PageResult
import org.scrollia.app.helpers.PagedState
import org.scrollia.app.helpers.Paginator
import org.scrollia.app.helpers.PreferenceUtil
import org.scrollia.app.helpers.book.BookLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val booksApi: BookAPI,
    private val preferenceUtil: PreferenceUtil
) : ViewModel() {

    private var category: String? = null

    private val _language: MutableState<BookLanguage> = mutableStateOf(getPreferredLanguage())
    val language: State<BookLanguage> = _language

    private val pagination = Paginator(scope = viewModelScope, idOf = Book::id) { page ->
        val category = category ?: return@Paginator Result.success(PageResult(emptyList(), false))
        // A cached first page would replace the shimmer mid-way through the navigation
        // animation; a short delay avoids that flicker.
        if (page == 1L) delay(400)
        booksApi.getBooksByCategory(category, page, language.value).map { bookSet ->
            PageResult(
                items = bookSet.books.filter { it.formats.applicationepubzip != null },
                hasNext = bookSet.next != null
            )
        }
    }

    val state: StateFlow<PagedState<Book>> = pagination.state

    /** Show [category]. Idempotent: coming back to the screen keeps loaded pages and position. */
    fun loadBookByCategory(category: String) {
        if (this.category == category) return
        this.category = category
        pagination.reset()
        pagination.loadNext()
    }

    fun loadNextItems() = pagination.loadNext()

    fun retry() = pagination.retry()

    fun retryAfterReconnect() = pagination.retryAfterReconnect()

    fun changeLanguage(language: BookLanguage) {
        if (language == _language.value) return
        _language.value = language
        preferenceUtil.putString(PreferenceUtil.PREFERRED_BOOK_LANG_STR, language.isoCode)
        pagination.reset()
        pagination.loadNext()
    }

    private fun getPreferredLanguage(): BookLanguage {
        val isoCode = preferenceUtil.getString(
            PreferenceUtil.PREFERRED_BOOK_LANG_STR,
            BookLanguage.AllBooks.isoCode
        )
        return BookLanguage.getAllLanguages().find { it.isoCode == isoCode }
            ?: BookLanguage.AllBooks
    }
}
