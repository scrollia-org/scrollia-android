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

package org.scrollia.app.ui.screens.detail.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.scrollia.app.api.BookAPI
import org.scrollia.app.api.models.Book
import org.scrollia.app.api.models.BookSet
import org.scrollia.app.api.models.ExtraInfo
import org.scrollia.app.database.library.LibraryDao
import org.scrollia.app.database.library.LibraryItem
import org.scrollia.app.helpers.Constants
import org.scrollia.app.helpers.PreferenceUtil
import org.scrollia.app.helpers.book.BookDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class BookDetailScreenState(
    val isLoading: Boolean = true,
    val bookSet: BookSet = BookSet(0, null, null, emptyList()),
    val extraInfo: ExtraInfo = ExtraInfo(),
    val extraInfoBookId: Int? = null,
    val bookLibraryItem: LibraryItem? = null,
    val error: String? = null
)


@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookAPI: BookAPI,
    val libraryDao: LibraryDao,
    val bookDownloader: BookDownloader,
    private val preferenceUtil: PreferenceUtil
) : ViewModel() {
    var state by mutableStateOf(BookDetailScreenState())
        private set

    fun getInternalReaderSetting() = preferenceUtil.getBoolean(
        PreferenceUtil.INTERNAL_READER_BOOL, true
    )

    fun getBookDetails(bookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // This method can be called multiple times. like if
                // request fails and user clicks on retry button.
                // So, we are setting isLoading to true before making
                // the request to show the loading indicator in the UI.
                state = state.copy(isLoading = true)
                val bookSet = bookAPI.getBookById(bookId).getOrNull()!!
                // If API response is cached, it will not show the loading
                // indicator. So, we are adding a delay to show the loading
                // indicator. This is just for better UX.
                if (bookSet.isCached) delay(400)
                state = state.copy(
                    bookSet = bookSet,
                    bookLibraryItem = libraryDao.getItemByBookId(bookId.toInt()),
                    isLoading = false,
                    error = null
                )
            } catch (exc: Exception) {
                state = state.copy(
                    error = exc.localizedMessage ?: Constants.UNKNOWN_ERR,
                    isLoading = false
                )
            }
        }
    }

    fun getExtraInfo(bookId: Int, bookName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            state = state.copy(extraInfo = ExtraInfo(), extraInfoBookId = bookId)
            val extraInfo = bookAPI.getExtraInfo(bookName) ?: ExtraInfo()
            if (state.extraInfoBookId == bookId) {
                state = state.copy(extraInfo = extraInfo)
            }
        }
    }

    fun reFetchLibraryItem(bookId: Int, onComplete: (LibraryItem) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val libraryItem = libraryDao.getItemByBookId(bookId)
            state = state.copy(bookLibraryItem = libraryItem)
            libraryItem?.let { withContext(Dispatchers.Main) { onComplete(libraryItem) } }
        }
    }

    fun downloadBook(book: Book) {
        bookDownloader.downloadBook(book)
    }
}