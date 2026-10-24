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

package org.scrollia.app.ui.screens.categories.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.scrollia.app.R
import org.scrollia.app.helpers.ContentState
import org.scrollia.app.helpers.NetworkObserver
import org.scrollia.app.helpers.book.BookUtils
import org.scrollia.app.ui.common.BookItemCard
import org.scrollia.app.ui.common.BookItemShimmerLoader
import org.scrollia.app.ui.common.BookLanguageSheet
import org.scrollia.app.ui.common.CustomTopAppBar
import org.scrollia.app.ui.common.AppendFooter
import org.scrollia.app.ui.common.LoadFailureState
import org.scrollia.app.ui.common.LoadMoreEffect
import org.scrollia.app.ui.common.NoBooksAvailable
import org.scrollia.app.ui.navigation.Screens
import org.scrollia.app.ui.screens.categories.viewmodels.CategoryViewModel


@Composable
fun CategoryDetailScreen(
    category: String, navController: NavController, networkStatus: NetworkObserver.Status
) {
    val viewModel: CategoryViewModel = hiltViewModel()
    val showLanguageSheet = remember { mutableStateOf(false) }

    BookLanguageSheet(
        showBookLanguage = showLanguageSheet,
        selectedLanguage = viewModel.language.value,
        onLanguageChange = { viewModel.changeLanguage(it) }
    )

    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()
    LaunchedEffect(category) { viewModel.loadBookByCategory(category) }
    LaunchedEffect(networkStatus) {
        if (networkStatus == NetworkObserver.Status.Available) viewModel.retryAfterReconnect()
    }
    // A new language starts a new list at the top; coming back keeps the position.
    var shownGeneration by rememberSaveable { mutableIntStateOf(state.generation) }
    LaunchedEffect(state.generation) {
        if (state.generation != shownGeneration) {
            shownGeneration = state.generation
            gridState.scrollToItem(0)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                headerText = stringResource(id = BookCategories.getNameRes(category)),
                actionIcon = Icons.Filled.Create,
                onBackButtonClicked = { navController.navigateUp() },
                onActionClicked = { showLanguageSheet.value = true }
            )
        }, content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(it)
            ) {
                when (state.content) {
                    ContentState.LOADING -> BookItemShimmerLoader()

                    ContentState.EMPTY -> NoBooksAvailable(
                        text = stringResource(id = R.string.no_books_found_for_lang_and_cat)
                            .format(
                                viewModel.language.value.name
                                    .lowercase(LocalConfiguration.current.locales[0])
                            )
                    )

                    ContentState.OFFLINE, ContentState.ERROR -> LoadFailureState(
                        failure = state.failure!!,
                        onRetry = { viewModel.retry() }
                    )

                    ContentState.RESULTS -> {
                        LoadMoreEffect(
                            gridState = gridState,
                            onLoadMore = { viewModel.loadNextItems() }
                        )
                        LazyVerticalGrid(
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(start = 8.dp, end = 8.dp),
                            columns = GridCells.Adaptive(295.dp)
                        ) {
                            items(state.items, key = { book -> book.id }) { item ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BookItemCard(
                                        title = item.title,
                                        author = BookUtils.getAuthorsAsString(item.authors),
                                        language = BookUtils.getLanguagesAsString(item.languages),
                                        subjects = BookUtils.getSubjectsAsString(item.subjects, 3),
                                        coverImageUrl = item.formats.imagejpeg
                                    ) {
                                        navController.navigate(
                                            Screens.BookDetailScreen.withBookId(item.id.toString())
                                        )
                                    }
                                }
                            }
                            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                                AppendFooter(
                                    isAppending = state.isAppending,
                                    failure = state.appendFailure,
                                    onRetry = { viewModel.retry() }
                                )
                            }
                        }
                    }
                }
            }
        })
}
