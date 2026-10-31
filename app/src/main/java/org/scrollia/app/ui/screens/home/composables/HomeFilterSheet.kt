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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.scrollia.app.R
import org.scrollia.app.api.models.CategoryInfo
import org.scrollia.app.helpers.book.BookLanguage
import org.scrollia.app.helpers.weakHapticFeedback
import org.scrollia.app.ui.screens.categories.composables.BookCategories
import org.scrollia.app.ui.screens.home.viewmodels.SortOption
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont

/**
 * Unified filter sheet for the Home catalog: language, genre (non-empty
 * categories reported by the backend) and sort order. Selections are local
 * until the user taps Apply.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeFilterSheet(
    showSheet: MutableState<Boolean>,
    currentLanguage: BookLanguage,
    currentGenre: String?,
    currentSort: SortOption,
    genreOptions: List<CategoryInfo>?,
    onApply: (BookLanguage, String?, SortOption) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    if (showSheet.value) {
        var language by remember { mutableStateOf(currentLanguage) }
        var genre by remember { mutableStateOf(currentGenre) }
        var sort by remember { mutableStateOf(currentSort) }

        fun dismiss() {
            coroutineScope.launch {
                sheetState.hide()
                delay(300)
                showSheet.value = false
            }
        }

        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { dismiss() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    text = stringResource(id = R.string.filter_sheet_title),
                    textAlign = TextAlign.Center,
                    fontFamily = frauncesFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FilterSectionHeader(titleRes = R.string.filter_section_language)
                Row {
                    FilterChip(
                        selected = language == BookLanguage.AllBooks,
                        onClick = { language = BookLanguage.AllBooks },
                        label = {
                            Text(stringResource(id = R.string.filter_option_all), fontFamily = interFont)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = language == BookLanguage.Spanish,
                        onClick = { language = BookLanguage.Spanish },
                        label = { Text("Español", fontFamily = interFont) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = language == BookLanguage.English,
                        onClick = { language = BookLanguage.English },
                        label = { Text("English", fontFamily = interFont) }
                    )
                }

                if (genreOptions != null) {
                    FilterSectionHeader(titleRes = R.string.filter_section_genre)
                    FlowRow {
                        FilterChip(
                            selected = genre == null,
                            onClick = { genre = null },
                            label = {
                                Text(stringResource(id = R.string.filter_option_all), fontFamily = interFont)
                            },
                            modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
                        )
                        genreOptions.forEach { option ->
                            FilterChip(
                                selected = genre == option.category,
                                onClick = { genre = option.category },
                                label = {
                                    Text(
                                        text = stringResource(
                                            id = BookCategories.getNameRes(option.category)
                                        ) + " · ${option.count}",
                                        fontFamily = interFont
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
                            )
                        }
                    }
                }

                FilterSectionHeader(titleRes = R.string.filter_section_sort)
                Row {
                    FilterChip(
                        selected = sort == SortOption.POPULARITY,
                        onClick = { sort = SortOption.POPULARITY },
                        label = {
                            Text(stringResource(id = R.string.filter_sort_popularity), fontFamily = interFont)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = sort == SortOption.TITLE,
                        onClick = { sort = SortOption.TITLE },
                        label = {
                            Text(stringResource(id = R.string.filter_sort_title), fontFamily = interFont)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = sort == SortOption.AUTHOR,
                        onClick = { sort = SortOption.AUTHOR },
                        label = {
                            Text(stringResource(id = R.string.filter_sort_author), fontFamily = interFont)
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        view.weakHapticFeedback()
                        onApply(BookLanguage.AllBooks, null, SortOption.POPULARITY)
                        dismiss()
                    }) {
                        Text(
                            text = stringResource(id = R.string.filter_clear),
                            fontFamily = interFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = {
                        view.weakHapticFeedback()
                        onApply(language, genre, sort)
                        dismiss()
                    }) {
                        Text(
                            text = stringResource(id = R.string.filter_apply),
                            fontFamily = interFont,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(titleRes: Int) {
    Text(
        text = stringResource(id = titleRes).uppercase(LocalLocale.current.platformLocale),
        fontFamily = interFont,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
    )
}
