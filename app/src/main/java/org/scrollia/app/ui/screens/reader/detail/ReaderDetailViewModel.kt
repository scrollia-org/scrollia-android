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


package org.scrollia.app.ui.screens.reader.detail

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.scrollia.app.api.BookAPI
import org.scrollia.app.database.library.LibraryDao
import org.scrollia.app.database.progress.ProgressDao
import org.scrollia.app.database.progress.ProgressData
import org.scrollia.app.epub.EpubParser
import org.scrollia.app.epub.models.EpubChapter
import org.scrollia.app.helpers.NetworkObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


data class ReaderDetailScreenState(
    val isLoading: Boolean = true,
    val title: String = "",
    val authors: String = "",
    val coverImage: Any? = null,
    val chapters: List<EpubChapter> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ReaderDetailViewModel @Inject constructor(
    private val bookAPI: BookAPI,
    private val libraryDao: LibraryDao,
    private val progressDao: ProgressDao,
    private val epubParser: EpubParser
) : ViewModel() {

    var state by mutableStateOf(ReaderDetailScreenState())

    var progressData: Flow<ProgressData?>? = null
        private set

    fun loadEbookData(libraryItemId: String, networkStatus: NetworkObserver.Status) {
        viewModelScope.launch(Dispatchers.IO) {
            state = state.copy(isLoading = true, error = null)
            try {
                val id = libraryItemId.toInt()
                val libraryItem = libraryDao.getItemById(id)
                    ?: throw IllegalStateException("Library item not found")
                progressData = progressDao.getReaderDataAsFlow(id)
                val coverImage: String? = try {
                    if (!libraryItem.isImported && networkStatus == NetworkObserver.Status.Available) {
                        bookAPI.getExtraInfo(libraryItem.title)?.coverImage
                    } else null
                } catch (exc: Exception) {
                    Log.e("ReaderDetailViewModel", "Failed to fetch cover image.", exc)
                    null
                }
                val isInternalChineseBook =
                    !libraryItem.isImported && epubParser.peekLanguage(libraryItem.filePath) == "zh"
                val epubBook = epubParser.createEpubBook(
                    libraryItem.filePath,
                    shouldUseToc = !isInternalChineseBook
                )
                state = state.copy(
                    title = libraryItem.title,
                    authors = libraryItem.authors,
                    coverImage = coverImage ?: epubBook.coverImage,
                    chapters = epubBook.chapters,
                )
                delay(350)
                state = state.copy(isLoading = false)
            } catch (exc: Exception) {
                if (exc is CancellationException) throw exc
                Log.e("ReaderDetailViewModel", "Could not open ebook details", exc)
                state = state.copy(
                    isLoading = false,
                    error = exc.localizedMessage ?: "Ebook could not be opened"
                )
            }
        }
    }
}
