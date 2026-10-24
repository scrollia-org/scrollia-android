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

package org.scrollia.app.ui.screens.home.composables

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import org.scrollia.app.R
import org.scrollia.app.api.models.Book
import org.scrollia.app.helpers.ContentState
import org.scrollia.app.helpers.NetworkObserver
import org.scrollia.app.helpers.PagedState
import org.scrollia.app.helpers.SearchState
import org.scrollia.app.helpers.book.BookLanguage
import org.scrollia.app.helpers.book.BookUtils
import org.scrollia.app.ui.common.BookItemCard
import org.scrollia.app.ui.common.BookItemShimmerLoader
import org.scrollia.app.ui.common.AppendFooter
import org.scrollia.app.ui.common.LoadFailureState
import org.scrollia.app.ui.common.LoadMoreEffect
import org.scrollia.app.ui.common.NoBooksAvailable
import org.scrollia.app.ui.common.ProgressDots
import org.scrollia.app.ui.navigation.Screens
import org.scrollia.app.ui.screens.home.viewmodels.HomeViewModel
import org.scrollia.app.ui.screens.home.viewmodels.UserAction
import org.scrollia.app.ui.screens.main.bottomNavPadding
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont
import androidx.compose.runtime.getValue


@Composable
fun HomeScreen(navController: NavController, networkStatus: NetworkObserver.Status) {

    val viewModel: HomeViewModel = hiltViewModel()
    val searchState by viewModel.searchState.collectAsState()

    LaunchedEffect(networkStatus) {
        if (networkStatus == NetworkObserver.Status.Available) viewModel.onNetworkAvailable()
    }

    // With search open, Back first clears the text, then closes search; only then may it
    // leave Home.
    BackHandler(enabled = searchState.isVisible) {
        if (searchState.text.isNotEmpty()) {
            viewModel.onAction(UserAction.TextFieldInput(""))
        } else {
            viewModel.onAction(UserAction.CloseIconClicked)
        }
    }

    val showFilterSheet = remember { mutableStateOf(false) }
    HomeFilterSheet(
        showSheet = showFilterSheet,
        currentLanguage = viewModel.language.value,
        currentGenre = viewModel.genre,
        currentSort = viewModel.sortOption,
        genreOptions = viewModel.genreOptions,
        onApply = { language, genre, sort ->
            viewModel.onAction(UserAction.ApplyFilters(language, genre, sort))
        }
    )

    HomeScreenScaffold(
        viewModel = viewModel,
        searchState = searchState,
        navController = navController,
        showFilterSheet = showFilterSheet
    )
}


@Composable
private fun HomeScreenScaffold(
    viewModel: HomeViewModel,
    searchState: SearchState<Book>,
    navController: NavController,
    showFilterSheet: MutableState<Boolean>
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = bottomNavPadding),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp)
            ) {
                Crossfade(
                    modifier = Modifier.animateContentSize(),
                    targetState = searchState.isVisible,
                    animationSpec = tween(durationMillis = 200), label = "search cross fade"
                ) {
                    if (it) {
                        SearchAppBar(onCloseIconClicked = {
                            viewModel.onAction(UserAction.CloseIconClicked)
                        }, onInputValueChange = { newText ->
                            viewModel.onAction(UserAction.TextFieldInput(newText))
                        }, text = searchState.text, onSearchClicked = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        })
                    } else {
                        HomeTopAppBar(
                            bookLanguage = viewModel.language.value,
                            filtersActive = viewModel.filtersActive,
                            onSearchIconClicked = {
                                viewModel.onAction(UserAction.SearchIconClicked)
                            }, onFilterIconClicked = {
                                showFilterSheet.value = true
                            })
                    }
                }
                HorizontalDivider(
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            }
        },
    ) { paddingValues ->
        HomeScreenContents(
            viewModel = viewModel,
            searchState = searchState,
            navController = navController,
            paddingValues = paddingValues
        )
    }
}

@Composable
private fun HomeScreenContents(
    viewModel: HomeViewModel,
    searchState: SearchState<Book>,
    navController: NavController,
    paddingValues: PaddingValues
) {
    val catalogState by viewModel.catalogState.collectAsState()
    // Hoisted so each list keeps its position while the other is shown, across tab changes
    // (saved with the destination) and rotation.
    val catalogGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()
    // New filters start a new list at the top; coming back to Home keeps the position.
    var shownGeneration by rememberSaveable { mutableIntStateOf(catalogState.generation) }
    LaunchedEffect(catalogState.generation) {
        if (catalogState.generation != shownGeneration) {
            shownGeneration = catalogState.generation
            catalogGridState.scrollToItem(0)
        }
    }
    var shownQuery by rememberSaveable { mutableStateOf(searchState.query) }
    LaunchedEffect(searchState.query) {
        if (searchState.query != shownQuery) {
            shownQuery = searchState.query
            searchGridState.scrollToItem(0)
        }
    }
    val onOpenBook: (Book) -> Unit = { book ->
        navController.navigate(Screens.BookDetailScreen.withBookId(book.id.toString()))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
    ) {
        if (searchState.isActive) {
            SearchBookList(
                searchState = searchState,
                gridState = searchGridState,
                onRetry = { viewModel.retrySearch() },
                onOpenBook = onOpenBook
            )
        } else {
            AllBooksList(
                catalogState = catalogState,
                gridState = catalogGridState,
                onRetry = { viewModel.retryCatalog() },
                onLoadNextItems = { viewModel.loadNextItems() },
                onOpenBook = onOpenBook,
                header = {
                    ContinueReadingRail(
                        items = viewModel.continueReading,
                        onOpenDetails = { id ->
                            navController.navigate(
                                Screens.ReaderDetailScreen.withLibraryItemId(id.toString())
                            )
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun AllBooksList(
    catalogState: PagedState<Book>,
    gridState: LazyGridState,
    onRetry: () -> Unit,
    onLoadNextItems: () -> Unit,
    onOpenBook: (Book) -> Unit,
    header: @Composable () -> Unit
) {
    // The header (Continue reading) stays reachable whatever the catalog's state: a catalog
    // failure must not hide the reader's own books.
    when (val content = catalogState.content) {
        ContentState.RESULTS -> {
            LoadMoreEffect(gridState = gridState, onLoadMore = onLoadNextItems)
            BookGrid(
                books = catalogState.items,
                gridState = gridState,
                onOpenBook = onOpenBook,
                header = header,
                footer = {
                    AppendFooter(
                        isAppending = catalogState.isAppending,
                        failure = catalogState.appendFailure,
                        onRetry = onRetry
                    )
                }
            )
        }

        else -> Column {
            Box(modifier = Modifier.padding(horizontal = 8.dp)) { header() }
            when (content) {
                ContentState.LOADING -> BookItemShimmerLoader()
                ContentState.EMPTY -> NoBooksAvailable(
                    text = stringResource(id = R.string.catalog_empty)
                )
                else -> LoadFailureState(failure = catalogState.failure!!, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun SearchBookList(
    searchState: SearchState<Book>,
    gridState: LazyGridState,
    onRetry: () -> Unit,
    onOpenBook: (Book) -> Unit
) {
    when (searchState.content) {
        ContentState.LOADING -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            ProgressDots()
        }

        ContentState.RESULTS -> BookGrid(
            books = searchState.results,
            gridState = gridState,
            onOpenBook = onOpenBook
        )

        ContentState.EMPTY -> NoBooksAvailable(
            text = stringResource(id = R.string.search_empty, searchState.query)
        )

        ContentState.OFFLINE, ContentState.ERROR ->
            LoadFailureState(failure = searchState.failure!!, onRetry = onRetry)
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    gridState: LazyGridState,
    onOpenBook: (Book) -> Unit,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 8.dp, end = 8.dp),
        columns = GridCells.Adaptive(295.dp)
    ) {
        if (header != null) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(books, key = { it.id }) { item ->
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
                    onOpenBook(item)
                }
            }
        }
        if (footer != null) {
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    }
}

@Composable
private fun HomeTopAppBar(
    bookLanguage: BookLanguage,
    filtersActive: Boolean,
    onSearchIconClicked: () -> Unit,
    onFilterIconClicked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (bookLanguage == BookLanguage.AllBooks)
                stringResource(id = R.string.home_header) else bookLanguage.name,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = frauncesFont
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onFilterIconClicked) {
            Box {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_filter),
                    contentDescription = stringResource(id = R.string.filter_icon_desc),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(30.dp)
                )
                if (filtersActive) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
        }
        IconButton(onClick = onSearchIconClicked) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_search),
                contentDescription = stringResource(id = R.string.home_search_icon_desc),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}


@Composable
private fun SearchAppBar(
    onCloseIconClicked: () -> Unit,
    onInputValueChange: (String) -> Unit,
    text: String,
    onSearchClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        val focusRequester = remember { FocusRequester() }
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            value = text,
            onValueChange = { onInputValueChange(it) },
            placeholder = {
                Text(
                    text = stringResource(id = R.string.home_search_placeholder),
                    fontFamily = interFont,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(
                        alpha = 0.5f
                    )
                )
            },
            trailingIcon = {
                IconButton(onClick = {
                    if (text.isNotEmpty()) {
                        onInputValueChange("")
                    } else {
                        onCloseIconClicked()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(id = R.string.home_search_close_desc),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchClicked() }),
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.height(5.6.dp))

        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

}


@Composable
@Preview(showBackground = true)
fun HomeScreenPreview() {
    HomeScreen(navController = rememberNavController(), NetworkObserver.Status.Unavailable)
}