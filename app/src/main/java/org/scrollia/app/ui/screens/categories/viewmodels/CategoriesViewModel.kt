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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.scrollia.app.api.BookAPI
import org.scrollia.app.api.HttpStatusException
import org.scrollia.app.api.models.CategoryInfo
import org.scrollia.app.helpers.ContentState
import org.scrollia.app.helpers.LoadFailure
import org.scrollia.app.ui.screens.categories.composables.BookCategories
import javax.inject.Inject

data class CategoriesState(
    val isLoading: Boolean = true,
    val categories: List<BookCategories> = emptyList(),
    val failure: LoadFailure? = null
) {
    val content: ContentState
        get() = when {
            categories.isNotEmpty() -> ContentState.RESULTS
            failure != null -> failure.contentState
            isLoading -> ContentState.LOADING
            else -> ContentState.EMPTY
        }

    companion object {
        /**
         * The categories that have books. A backend without `/categories` (HTTP 404) is the one
         * deliberate fallback to the built-in list; every other failure is reported as such.
         */
        fun from(result: Result<List<CategoryInfo>>): CategoriesState = result.fold(
            onSuccess = { infos ->
                CategoriesState(
                    isLoading = false,
                    categories = BookCategories.ALL.filter { category ->
                        infos.any { it.category == category.category && it.count > 0 }
                    }
                )
            },
            onFailure = { error ->
                if (error is HttpStatusException && error.code == 404) {
                    CategoriesState(isLoading = false, categories = BookCategories.ALL.asList())
                } else {
                    CategoriesState(isLoading = false, failure = LoadFailure.of(error))
                }
            }
        )
    }
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val booksApi: BookAPI) : ViewModel() {

    var state by mutableStateOf(CategoriesState())
        private set

    init {
        load()
    }

    fun retry() {
        if (state.isLoading || state.failure == null) return
        load()
    }

    fun retryAfterReconnect() {
        if (state.failure == LoadFailure.OFFLINE) retry()
    }

    private fun load() {
        state = state.copy(isLoading = true, failure = null)
        viewModelScope.launch {
            state = CategoriesState.from(booksApi.getCategories())
        }
    }
}
