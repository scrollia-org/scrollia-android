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

package org.scrollia.app.ui.screens.home.viewmodels

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.scrollia.app.api.BookAPI
import org.scrollia.app.api.models.Book
import org.scrollia.app.api.models.CategoryInfo
import org.scrollia.app.helpers.PageResult
import org.scrollia.app.helpers.PagedState
import org.scrollia.app.helpers.Paginator
import org.scrollia.app.helpers.PreferenceUtil
import org.scrollia.app.helpers.SearchController
import org.scrollia.app.helpers.SearchState
import org.scrollia.app.helpers.book.BookCoverCache
import org.scrollia.app.helpers.book.BookLanguage
import org.scrollia.app.database.library.LibraryDao
import org.scrollia.app.database.progress.ProgressDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

sealed class UserAction {
    data object SearchIconClicked : UserAction()
    data object CloseIconClicked : UserAction()
    data class TextFieldInput(val text: String) : UserAction()

    data class ApplyFilters(
        val language: BookLanguage,
        val genre: String?,
        val sortOption: SortOption
    ) : UserAction()
}

enum class SortOption {
    POPULARITY, TITLE, AUTHOR
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookAPI: BookAPI,
    private val preferenceUtil: PreferenceUtil,
    libraryDao: LibraryDao,
    progressDao: ProgressDao,
    private val bookCoverCache: BookCoverCache,
) : ViewModel() {
    private val _language: MutableState<BookLanguage> = mutableStateOf(getPreferredLanguage())
    val language: State<BookLanguage> = _language

    var genre by mutableStateOf<String?>(null)
        private set
    var sortOption by mutableStateOf(SortOption.POPULARITY)
        private set

    // Non-empty categories from the backend; null when the endpoint is
    // unavailable, in which case the genre filter section is hidden.
    var genreOptions by mutableStateOf<List<CategoryInfo>?>(null)
        private set

    val filtersActive: Boolean
        get() = language.value != BookLanguage.AllBooks ||
                genre != null || sortOption != SortOption.POPULARITY

    // Books in progress for the "Continue reading" rail; empty hides the rail.
    var continueReading by mutableStateOf<List<ContinueReadingItem>>(emptyList())
        private set

    private val catalog = Paginator(
        scope = viewModelScope,
        idOf = Book::id,
        // Sorted client-side over all loaded items (the backend has no sort params).
        arrange = { books ->
            when (sortOption) {
                SortOption.TITLE -> books.sortedBy { it.title.lowercase() }
                SortOption.AUTHOR -> books.sortedBy {
                    it.authors.firstOrNull()?.name?.lowercase() ?: ""
                }
                SortOption.POPULARITY -> books
            }
        }
    ) { page ->
        // Only delay the first page, to show the shimmer instead of a flicker when Home
        // opens straight after the welcome screen.
        if (page == 1L) delay(400L)
        bookAPI.getAllBooks(page, language.value, genre).map { bookSet ->
            PageResult(
                items = bookSet.books.filter {
                    it.formats.applicationepubzip != null && it.id !in HIDDEN_BOOK_IDS
                },
                hasNext = bookSet.next != null
            )
        }
    }

    val catalogState: StateFlow<PagedState<Book>> = catalog.state

    private val search = SearchController(scope = viewModelScope) { query ->
        bookAPI.searchBooks(query).map { bookSet ->
            bookSet.books.filter { it.formats.applicationepubzip != null }
        }
    }

    val searchState: StateFlow<SearchState<Book>> = search.state

    private var genresRequested = false

    init {
        catalog.loadNext()
        loadGenres()
        // Room flows re-emit on every progress write, so the rail is current when the user
        // comes back from the reader.
        viewModelScope.launch {
            combine(
                libraryDao.getAllItemsAsFlow(),
                progressDao.getAllReaderItemsAsFlow()
            ) { library, progress -> ContinueReading.inProgress(library, progress) }
                .map { rows ->
                    rows.filter { (item, _) -> item.fileExist() }.map { (item, p) ->
                        ContinueReadingItem(
                            libraryItemId = item.id,
                            title = item.title,
                            authors = item.authors,
                            coverFile = bookCoverCache.coverFile(item),
                            progressPercent = p.progressPercent(),
                            progress = p.progress
                        )
                    }
                }
                .flowOn(Dispatchers.IO)
                .collect { continueReading = it }
        }
    }

    fun loadNextItems() = catalog.loadNext()

    fun retryCatalog() = catalog.retry()

    fun retrySearch() = search.retry()

    /**
     * Connectivity is back: retry only what failed for lack of it (or a failed later page).
     * Loaded items, query, filters and position are untouched.
     */
    fun onNetworkAvailable() {
        catalog.retryAfterReconnect()
        search.retryAfterReconnect()
        if (genreOptions == null && !genresRequested) loadGenres()
    }

    fun onAction(userAction: UserAction) {
        when (userAction) {
            UserAction.CloseIconClicked -> search.close()
            UserAction.SearchIconClicked -> search.open()
            is UserAction.TextFieldInput -> search.onTextChange(userAction.text)
            is UserAction.ApplyFilters -> {
                val changed = userAction.language != language.value ||
                        userAction.genre != genre || userAction.sortOption != sortOption
                if (!changed) return
                _language.value = userAction.language
                preferenceUtil.putString(
                    PreferenceUtil.PREFERRED_BOOK_LANG_STR, userAction.language.isoCode
                )
                genre = userAction.genre
                sortOption = userAction.sortOption
                catalog.reset()
                catalog.loadNext()
            }
        }
    }

    private fun loadGenres() {
        genresRequested = true
        viewModelScope.launch {
            // Null hides the genre filter section.
            genreOptions = bookAPI.getCategories().getOrNull()?.filter { it.count > 0 }
            genresRequested = genreOptions != null
        }
    }

    private fun getPreferredLanguage(): BookLanguage {
        val isoCode = preferenceUtil.getString(
            PreferenceUtil.PREFERRED_BOOK_LANG_STR,
            BookLanguage.AllBooks.isoCode
        )
        return BookLanguage.getAllLanguages().find { it.isoCode == isoCode }
            ?: BookLanguage.AllBooks
    }

    private companion object {
        // Books the catalog never lists.
        val HIDDEN_BOOK_IDS = setOf(1513)
    }
}