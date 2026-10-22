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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import org.scrollia.app.R
import org.scrollia.app.helpers.weakHapticFeedback
import org.scrollia.app.ui.common.CustomTopAppBar
import org.scrollia.app.ui.common.ProgressDots
import org.scrollia.app.ui.navigation.Screens
import org.scrollia.app.ui.screens.categories.viewmodels.CategoriesViewModel
import org.scrollia.app.ui.screens.main.bottomNavPadding
import org.scrollia.app.ui.theme.interFont


@Composable
fun CategoriesScreen(navController: NavController) {
    val viewModel: CategoriesViewModel = hiltViewModel()
    val state = viewModel.state

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        topBar = {
            CustomTopAppBar(
                headerText = stringResource(id = R.string.categories_header),
                iconRes = R.drawable.ic_nav_categories
            )
        }, content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                // Only show categories that have books when the backend
                // reports counts; fall back to the full static list when the
                // endpoint is unavailable.
                val categories = state.categories?.let { infos ->
                    BookCategories.ALL.filter { cat ->
                        infos.any { it.category == cat.category && it.count > 0 }
                    }
                }
                if (categories == null && state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ProgressDots()
                    }
                } else {
                    val items = categories ?: BookCategories.ALL.asList()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(168.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp, end = 8.dp, bottom = bottomNavPadding),
                        content = {
                            items(items.size) { idx ->
                                val category = items[idx]
                                CategoriesItem(
                                    category = stringResource(id = category.nameRes),
                                    onClick = {
                                        navController.navigate(
                                            Screens.CategoryDetailScreen.withCategory(category.category)
                                        )
                                    }
                                )
                            }
                        },
                    )
                }
            }
        })

}


@Composable
private fun CategoriesItem(category: String, onClick: () -> Unit) {
    val view = LocalView.current
    Card(
        modifier = Modifier
            .height(90.dp)
            .width(160.dp)
            .padding(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        ),
        shape = RoundedCornerShape(6.dp),
        onClick = {
            view.weakHapticFeedback()
            onClick()
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                modifier = Modifier.padding(2.dp),
                text = category,
                fontSize = 17.sp,
                fontStyle = MaterialTheme.typography.headlineMedium.fontStyle,
                fontFamily = interFont,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@ExperimentalMaterial3Api
@Composable
@Preview(showBackground = true)
fun CategoriesScreenPreview() {
    CategoriesScreen(rememberNavController())
}