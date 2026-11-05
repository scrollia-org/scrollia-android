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

package org.scrollia.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.scrollia.app.R
import org.scrollia.app.helpers.weakHapticFeedback
import org.scrollia.app.ui.common.placeholder.placeholder
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont


@Composable
fun BookItemCard(
    title: String,
    author: String,
    language: String,
    coverImageUrl: String?,
    loadingEffect: Boolean = false,
    onClick: () -> Unit
) {
    val view = LocalView.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            view.weakHapticFeedback()
            onClick()
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(108.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .placeholder(isLoading = loadingEffect)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverImageUrl)
                        .crossfade(true)
                        .build(),
                    placeholder = painterResource(id = R.drawable.placeholder_book),
                    contentDescription = stringResource(id = R.string.cover_image_desc),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = title,
                    modifier = Modifier.placeholder(isLoading = loadingEffect),
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontFamily = frauncesFont,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = author,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .placeholder(isLoading = loadingEffect),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    fontFamily = interFont,
                    fontSize = 13.sp
                )
                Text(
                    text = language,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .placeholder(isLoading = loadingEffect),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = interFont
                )
            }
        }
    }
}

@Composable
fun BookItemShimmerLoader() {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 8.dp, end = 8.dp),
            columns = GridCells.Adaptive(295.dp)
        ) {
            items(16) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    BookItemCard(
                        title = "Crime and Punishment",
                        author = "Fyodor Dostoyevsky",
                        language = "English",
                        coverImageUrl = "No",
                        loadingEffect = true,
                        onClick = {}
                    )
                }
            }
        }
    }
}

@ExperimentalCoilApi
@ExperimentalMaterial3Api
@Preview
@Composable
fun BookCardPreview() {
    BookItemCard(
        title = "Crime and Punishment",
        author = "Fyodor Dostoyevsky",
        language = "English",
        coverImageUrl = "https://www.gutenberg.org/cache/epub/2554/pg2554.cover.medium.jpg",
        onClick = {}
    )
}
