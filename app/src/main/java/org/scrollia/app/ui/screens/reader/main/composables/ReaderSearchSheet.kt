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

package org.scrollia.app.ui.screens.reader.main.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.scrollia.app.R
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderScreenState
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderSearchHit
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchSheet(
    state: ReaderScreenState,
    onDismiss: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearch: (String) -> Unit,
    onHitClick: (ReaderSearchHit) -> Unit,
) {
    val theme = state.readerTheme
    val ink = theme.textColor
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.backgroundColor,
        contentColor = ink,
        scrimColor = theme.scrimColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ink.copy(alpha = 0.3f)) },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                text = stringResource(id = R.string.reader_search_title),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontFamily = frauncesFont,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = ink
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.reader_search_placeholder),
                        fontFamily = interFont,
                        color = ink.copy(alpha = 0.45f)
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSearch(state.searchQuery)
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ink,
                    unfocusedTextColor = ink,
                    cursorColor = ink,
                    focusedBorderColor = ink.copy(alpha = 0.6f),
                    unfocusedBorderColor = ink.copy(alpha = 0.25f),
                    focusedContainerColor = theme.backgroundColor,
                    unfocusedContainerColor = theme.backgroundColor,
                )
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            when {
                state.searchInProgress -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(24.dp),
                        color = ink
                    )
                }

                state.lastSearchQuery.isNotBlank() &&
                    state.lastSearchQuery == state.searchQuery.trim() &&
                    state.searchHits.isEmpty() &&
                    !state.searchInProgress -> {
                    Text(
                        text = stringResource(id = R.string.reader_search_empty),
                        modifier = Modifier.padding(top = 20.dp),
                        fontFamily = interFont,
                        fontSize = 14.sp,
                        color = ink.copy(alpha = 0.65f)
                    )
                }

                state.searchHits.isNotEmpty() -> {
                    Text(
                        text = stringResource(
                            id = R.string.reader_search_count,
                            state.searchHits.size
                        ),
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        fontFamily = interFont,
                        fontSize = 12.sp,
                        color = ink.copy(alpha = 0.65f)
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(state.searchHits, key = {
                            "${it.chapterIndex}:${it.itemIndex}:${it.snippet.hashCode()}"
                        }) { hit ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onHitClick(hit) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = hit.chapterTitle,
                                    fontFamily = interFont,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = ink
                                )
                                Text(
                                    text = hit.snippet,
                                    modifier = Modifier.padding(top = 2.dp),
                                    fontFamily = interFont,
                                    fontSize = 14.sp,
                                    color = ink.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
