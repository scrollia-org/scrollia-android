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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.scrollia.app.R
import org.scrollia.app.helpers.BlurTransformation
import org.scrollia.app.ui.screens.settings.viewmodels.ThemeMode
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont

@Composable
fun BookDetailTopUI(
    title: String,
    authors: String,
    imageData: Any?,
    currentThemeMode: ThemeMode,
    progressText: String? = null
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(235.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
        )
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageData)
                .transformations(BlurTransformation())
                .crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.60f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.90f)
                        ), startY = 8f
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val imageBackground =
                    if (currentThemeMode == ThemeMode.Dark) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                    }
                Box(
                    modifier = Modifier
                        .shadow(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(imageBackground)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageData)
                            .crossfade(true).build(),
                        placeholder = painterResource(id = R.drawable.placeholder_cat),
                        contentDescription = null,
                        modifier = Modifier
                            .width(118.dp)
                            .height(160.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = title,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                    fontSize = 22.sp,
                    fontFamily = frauncesFont,
                    maxLines = 3,
                    lineHeight = 28.sp,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Text(
                    text = authors,
                    modifier = Modifier.padding(
                        start = 12.dp, end = 8.dp, top = 2.dp
                    ),
                    fontSize = 16.sp,
                    fontFamily = interFont,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                if (!progressText.isNullOrBlank()) {
                    Text(
                        text = progressText,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                        fontSize = 15.sp,
                        fontFamily = interFont,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    BookDetailTopUI(
        title = "The Complete works of william shakespeare",
        authors = "F. Scott Fitzgerald",
        imageData = null,
        currentThemeMode = ThemeMode.Light
    )

}