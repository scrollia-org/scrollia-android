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

package org.scrollia.app.ui.screens.library.composables

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.psoffritti.taptargetcompose.TapTargetCoordinator
import com.psoffritti.taptargetcompose.TapTargetStyle
import com.psoffritti.taptargetcompose.TextDefinition
import org.scrollia.app.MainActivity
import org.scrollia.app.R
import org.scrollia.app.database.library.LibraryItem
import org.scrollia.app.database.progress.ProgressData
import org.scrollia.app.helpers.Constants
import org.scrollia.app.helpers.book.BookUtils
import org.scrollia.app.helpers.getActivity
import org.scrollia.app.helpers.isScrollingUp
import org.scrollia.app.helpers.weakHapticFeedback
import org.scrollia.app.ui.common.CustomTopAppBar
import org.scrollia.app.ui.common.NoBooksAvailable
import org.scrollia.app.ui.navigation.BottomBarScreen
import org.scrollia.app.ui.navigation.Screens
import org.scrollia.app.ui.screens.library.viewmodels.LibrarySort
import org.scrollia.app.ui.screens.library.viewmodels.LibrarySorting
import org.scrollia.app.ui.screens.library.viewmodels.LibraryViewModel
import org.scrollia.app.ui.screens.main.bottomNavPadding
import org.scrollia.app.ui.screens.main.navigateToTab
import org.scrollia.app.ui.screens.settings.viewmodels.SettingsViewModel
import org.scrollia.app.ui.screens.settings.viewmodels.ThemeMode
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont
import org.scrollia.app.ui.theme.literataFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController) {
    val view = LocalView.current
    val context = LocalContext.current
    val viewModel: LibraryViewModel = hiltViewModel()

    val snackBarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val libraryItems by viewModel.allItems.observeAsState(initial = emptyList())

    val showImportDialog = remember { mutableStateOf(false) }
    val importBookLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            // If no files are selected, return.
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            // Show dialog to indicate import process.
            showImportDialog.value = true
            // Start books import.
            viewModel.importBooks(
                fileUris = uris,
                onComplete = {
                    // Hide dialog and show success message.
                    showImportDialog.value = false
                    coroutineScope.launch {
                        snackBarHostState.showSnackbar(
                            message = context.getString(R.string.epub_imported),
                            actionLabel = context.getString(R.string.ok),
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                onError = {
                    // Hide dialog and show error message.
                    showImportDialog.value = false
                    coroutineScope.launch {
                        snackBarHostState.showSnackbar(
                            message = context.getString(R.string.error),
                            actionLabel = context.getString(R.string.ok),
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        }

    val showTapTargets = remember { mutableStateOf(false) }
    LaunchedEffect(key1 = viewModel.showOnboardingTapTargets.value) {
        delay(500) // Delay to prevent flickering
        showTapTargets.value = viewModel.showOnboardingTapTargets.value
    }

    TapTargetCoordinator(
        showTapTargets = showTapTargets.value,
        onComplete = { viewModel.onboardingComplete() }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackBarHostState) },
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = bottomNavPadding),
            topBar = {
                CustomTopAppBar(
                    headerText = stringResource(id = R.string.library_header),
                    iconRes = R.drawable.ic_nav_library
                )
            },
            floatingActionButton = {
                val density = LocalDensity.current
                AnimatedVisibility(
                    visible = libraryItems.isNotEmpty() &&
                            !showImportDialog.value && lazyListState.isScrollingUp(),
                    enter = slideInVertically {
                        with(density) { 40.dp.roundToPx() }
                    } + fadeIn(),
                    exit = fadeOut(
                        animationSpec = keyframes {
                            this.durationMillis = 120
                        }
                    )
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            view.weakHapticFeedback()
                            importBookLauncher.launch(arrayOf(Constants.EPUB_MIME_TYPE))
                        },
                        modifier = Modifier.tapTarget(
                            precedence = 0,
                            title = TextDefinition(
                                text = stringResource(id = R.string.import_button_onboarding),
                                textStyle = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            description = TextDefinition(
                                text = stringResource(id = R.string.import_button_onboarding_desc),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            tapTargetStyle = TapTargetStyle(
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                tapTargetHighlightColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                backgroundAlpha = 1f,
                            ),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(id = R.string.import_button_desc),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.import_button_text),
                            fontWeight = FontWeight.Medium,
                            fontFamily = interFont,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                    }
                }
            }
        ) { paddingValues ->
            LibraryContents(
                viewModel = viewModel,
                lazyListState = lazyListState,
                snackBarHostState = snackBarHostState,
                navController = navController,
                paddingValues = paddingValues,
                onImportClick = { importBookLauncher.launch(arrayOf(Constants.EPUB_MIME_TYPE)) }
            )

            if (showImportDialog.value) {
                BasicAlertDialog(onDismissRequest = {}) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        //  .padding(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(24.dp))
                            Text(
                                text = stringResource(id = R.string.epub_importing),
                                fontFamily = interFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 17.sp,
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun LibraryContents(
    viewModel: LibraryViewModel,
    lazyListState: LazyListState,
    snackBarHostState: SnackbarHostState,
    navController: NavController,
    paddingValues: PaddingValues,
    onImportClick: () -> Unit
) {
    val context = LocalContext.current
    val settingsVm = (context.getActivity() as MainActivity).settingsViewModel
    val libraryItems = viewModel.allItems.observeAsState(listOf()).value
    val progress by viewModel.progress.collectAsState(initial = emptyMap())
    val sort by viewModel.sort
    val sortedItems = remember(libraryItems, progress, sort) {
        LibrarySorting.sort(libraryItems, progress, sort)
    }

    // Show tooltip for library screen.
    LaunchedEffect(key1 = true) {
        if (viewModel.shouldShowLibraryTooltip()) {
            val result = snackBarHostState.showSnackbar(
                message = context.getString(R.string.library_tooltip),
                actionLabel = context.getString(R.string.got_it),
                duration = SnackbarDuration.Indefinite
            )

            when (result) {
                SnackbarResult.ActionPerformed -> {
                    viewModel.libraryTooltipDismissed()
                }

                SnackbarResult.Dismissed -> {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
    ) {
        if (libraryItems.isEmpty()) {
            EmptyLibrary(
                onExploreClick = { navController.navigateToTab(BottomBarScreen.Explore.route) },
                onImportClick = onImportClick
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                state = lazyListState
            ) {
                item(key = "library_sort") {
                    LibrarySortRow(sort = sort, onSortSelected = { viewModel.setSort(it) })
                }
                items(
                    count = sortedItems.size,
                    key = { i -> sortedItems[i].id }
                ) { i ->
                    val item = sortedItems[i]
                    if (item.fileExist()) {
                        LibraryLazyItem(
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                            item = item,
                            progress = progress[item.id],
                            snackBarHostState = snackBarHostState,
                            navController = navController,
                            viewModel = viewModel,
                            settingsVm = settingsVm
                        )
                    } else {
                        viewModel.deleteItemFromDB(item)
                    }
                }
            }

        }
    }
}

@Composable
private fun EmptyLibrary(
    onExploreClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_read_book),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = stringResource(id = R.string.empty_library),
            fontFamily = frauncesFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = stringResource(id = R.string.empty_library_description),
            fontFamily = literataFont,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
        FilledTonalButton(
            onClick = onExploreClick,
            modifier = Modifier
                .padding(top = 28.dp)
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(text = stringResource(id = R.string.empty_library_action))
        }
        OutlinedButton(
            onClick = onImportClick,
            modifier = Modifier
                .padding(top = 12.dp)
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(text = stringResource(id = R.string.empty_library_import))
        }
    }
}

@Composable
private fun LibraryLazyItem(
    modifier: Modifier,
    item: LibraryItem,
    progress: ProgressData?,
    snackBarHostState: SnackbarHostState,
    navController: NavController,
    viewModel: LibraryViewModel,
    settingsVm: SettingsViewModel
) {
    val context = LocalContext.current
    val coverFile by produceState<File?>(initialValue = null, item.id, item.filePath) {
        value = viewModel.coverFile(item)
    }

    val coroutineScope = rememberCoroutineScope()
    val openDeleteDialog = remember { mutableStateOf(false) }

    // Swipe actions to show book details.
    val detailsAction = SwipeAction(
        icon = painterResource(
            id = if (settingsVm.getCurrentTheme() == ThemeMode.Dark) R.drawable.ic_info else R.drawable.ic_info_white
        ), background = MaterialTheme.colorScheme.primary, onSwipe = {
            viewModel.viewModelScope.launch {
                delay(250L)
                if (item.isImported) {
                    snackBarHostState.showSnackbar(
                        message = context.getString(R.string.external_book_info_unavailable),
                        actionLabel = context.getString(R.string.ok),
                        duration = SnackbarDuration.Short
                    )
                } else {
                    navController.navigate(
                        Screens.BookDetailScreen.withBookId(
                            item.bookId.toString()
                        )
                    )
                }
            }
        })

    SwipeableActionsBox(
        modifier = modifier.padding(vertical = 4.dp),
        endActions = listOf(detailsAction),
        swipeThreshold = 85.dp
    ) {
        LibraryCard(
            title = item.title,
            author = item.authors,
            coverFile = coverFile,
            progress = progress,
            onReadClick = {
                BookUtils.openBookFile(
                    libraryItem = item,
                    navController = navController
                )
            },
            onDeleteClick = { openDeleteDialog.value = true })
    }

    if (openDeleteDialog.value) {
        AlertDialog(onDismissRequest = {
            openDeleteDialog.value = false
        }, title = {
            Text(
                text = stringResource(id = R.string.library_delete_dialog_title),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }, confirmButton = {
            FilledTonalButton(
                onClick = {
                    openDeleteDialog.value = false
                    val fileDeleted = item.deleteFile()
                    if (fileDeleted) {
                        viewModel.deleteItemFromDB(item)
                    } else {
                        coroutineScope.launch {
                            snackBarHostState.showSnackbar(
                                message = context.getString(R.string.error),
                                actionLabel = context.getString(R.string.ok),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(stringResource(id = R.string.confirm))
            }
        }, dismissButton = {
            TextButton(onClick = {
                openDeleteDialog.value = false
            }) {
                Text(stringResource(id = R.string.cancel))
            }
        })
    }
}

@Composable
private fun LibraryCard(
    title: String,
    author: String,
    coverFile: File?,
    progress: ProgressData? = null,
    onReadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable { onReadClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(108.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                if (coverFile != null) {
                    AsyncImage(
                        model = coverFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = title,
                        fontFamily = frauncesFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontFamily = frauncesFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            lineHeight = 23.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = author,
                            fontFamily = interFont,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(id = R.string.library_delete_button),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (progress != null && (progress.finished || progress.progressPercent() > 0)) {
                    LibraryProgress(
                        progress = progress,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                FilledTonalButton(
                    onClick = onReadClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_read_book),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            id = if (progress != null && !progress.finished && progress.progressPercent() > 0) {
                                R.string.continue_reading_button
                            } else R.string.library_read_button
                        ),
                        fontFamily = interFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/** Sort chips above the list, using the same visual language as the Home filter sheet. */
@Composable
private fun LibrarySortRow(sort: LibrarySort, onSortSelected: (LibrarySort) -> Unit) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibrarySort.entries.forEach { option ->
            FilterChip(
                selected = sort == option,
                onClick = {
                    view.weakHapticFeedback()
                    onSortSelected(option)
                },
                label = {
                    Text(
                        text = stringResource(
                            id = when (option) {
                                LibrarySort.RECENTLY_READ -> R.string.library_sort_recent
                                LibrarySort.TITLE -> R.string.library_sort_title
                            }
                        ),
                        fontFamily = interFont
                    )
                }
            )
        }
    }
}

/** Discreet progress: a thin line with a percentage, or a Finished marker. */
@Composable
private fun LibraryProgress(progress: ProgressData, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (progress.finished) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(id = R.string.library_finished),
                fontFamily = interFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LinearProgressIndicator(
                progress = { progress.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .width(120.dp)
                    .height(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.progress_percent, progress.progressPercent()),
                fontFamily = interFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@ExperimentalMaterial3Api
@Composable
@Preview
fun LibraryScreenPreview() {
    LibraryCard(
        title = "The Idiot",
        author = "Fyodor Dostoevsky",
        coverFile = null,
        onReadClick = {},
        onDeleteClick = {}
    )
}
