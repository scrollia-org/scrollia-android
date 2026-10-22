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
import org.scrollia.app.api.models.CategoryInfo
import javax.inject.Inject

data class CategoriesState(
    val isLoading: Boolean = true,
    // Null when the /categories endpoint is unavailable; the UI then falls
    // back to the built-in static category list.
    val categories: List<CategoryInfo>? = null
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val booksApi: BookAPI) : ViewModel() {

    var state by mutableStateOf(CategoriesState())
        private set

    init {
        viewModelScope.launch {
            state = CategoriesState(isLoading = false, categories = booksApi.getCategories())
        }
    }
}
