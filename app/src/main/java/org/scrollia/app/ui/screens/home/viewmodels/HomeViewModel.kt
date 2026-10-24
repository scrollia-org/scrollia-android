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
import org.scrollia.app.helpers.Constants
import org.scrollia.app.helpers.NetworkObserver
import org.scrollia.app.helpers.Paginator
import org.scrollia.app.helpers.PreferenceUtil
import org.scrollia.app.helpers.book.BookCoverCache
import org.scrollia.app.helpers.book.BookLanguage
import org.scrollia.app.database.library.LibraryDao
import org.scrollia.app.database.progress.ProgressDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class AllBooksState(
    val isLoading: Boolean = false,
    val items: List<Book> = emptyList(),
    val error: String? = null,
    val endReached: Boolean = false,
    val page: Long = 1L
)

data class SearchBarState(
    val searchText: String = "",
    val isSearchBarVisible: Boolean = false,
    val isSortMenuVisible: Boolean = false,
    val isSearching: Boolean = false,
    val searchResults: List<Book> = emptyList()
)

sealed class UserAction {
    data object SearchIconClicked : UserAction()
    data object CloseIconClicked : UserAction()
    data class TextFieldInput(
        val text: String,
        val networkStatus: NetworkObserver.Status
    ) : UserAction()

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
    var allBooksState by mutableStateOf(AllBooksState())
    var searchBarState by mutableStateOf(SearchBarState())

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

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            genreOptions = bookAPI.getCategories()?.filter { it.count > 0 }
        }
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

    private val pagination = Paginator(initialPage = allBooksState.page, onLoadUpdated = {
        allBooksState = allBooksState.copy(isLoading = it)
    }, onRequest = { nextPage ->
        try {
            // Only add delay when loading first page to show shimmer effect
            // and avoid flickering when navigating to home from welcome screen
            // and immediately loading the first page.
            if (nextPage == 1L) delay(400L)
            bookAPI.getAllBooks(nextPage, language.value, genre)
        } catch (exc: Exception) {
            Result.failure(exc)
        }
    }, getNextPage = {
        allBooksState.page + 1L
    }, onError = {
        allBooksState = allBooksState.copy(error = it?.localizedMessage ?: Constants.UNKNOWN_ERR)
    }, onSuccess = { bookSet, newPage ->

        val books = run {
            val books =
                bookSet.books.filter { it.formats.applicationepubzip != null } as ArrayList<Book>
            // Ignore ...
            val index = books.indexOfFirst { it.id == 1513 }
            if (index != -1) {
                books.removeAt(index)
            }
            books // return the list of books
        }

        // Sort client-side over all loaded items (the backend has no sort
        // params); with the current 40-book catalog every page merges into
        // its sorted position as the user scrolls.
        val merged = when (sortOption) {
            SortOption.TITLE -> (allBooksState.items + books).sortedBy { it.title.lowercase() }
            SortOption.AUTHOR -> (allBooksState.items + books)
                .sortedBy { it.authors.firstOrNull()?.name?.lowercase() ?: "" }

            SortOption.POPULARITY -> allBooksState.items + books
        }

        allBooksState = allBooksState.copy(
            items = merged,
            page = newPage,
            endReached = books.isEmpty()
        )
    })

    fun loadNextItems() {
        viewModelScope.launch {
            pagination.loadNextItems()
        }
    }

    fun reloadItems() {
        pagination.reset()
        allBooksState = AllBooksState()
        loadNextItems()
    }

    fun onAction(userAction: UserAction) {
        when (userAction) {
            UserAction.CloseIconClicked -> {
                searchBarState = searchBarState.copy(isSearchBarVisible = false)
            }

            UserAction.SearchIconClicked -> {
                searchBarState = searchBarState.copy(isSearchBarVisible = true)
            }

            is UserAction.TextFieldInput -> {
                searchBarState = searchBarState.copy(searchText = userAction.text)
                if (userAction.networkStatus == NetworkObserver.Status.Available) {
                    searchJob?.cancel()
                    searchJob = viewModelScope.launch {
                        if (userAction.text.isNotBlank()) {
                            searchBarState = searchBarState.copy(isSearching = true)
                        }
                        delay(500L)
                        searchBooks(userAction.text)
                    }
                }
            }

            is UserAction.ApplyFilters -> {
                _language.value = userAction.language
                preferenceUtil.putString(
                    PreferenceUtil.PREFERRED_BOOK_LANG_STR, userAction.language.isoCode
                )
                genre = userAction.genre
                sortOption = userAction.sortOption
                reloadItems()
            }
        }
    }

    private suspend fun searchBooks(query: String) {
        if (query.isBlank()) return // no need to search for empty query
        val bookSet = bookAPI.searchBooks(query)
        val books = bookSet.getOrNull()?.books?.filter { it.formats.applicationepubzip != null }
        searchBarState = searchBarState.copy(
            searchResults = books ?: emptyList(),
            isSearching = false
        )
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