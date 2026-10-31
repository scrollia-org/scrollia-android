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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.scrollia.app.R
import org.scrollia.app.ui.theme.interFont
import org.scrollia.app.ui.theme.literataFont


@Composable
fun NetworkError(errorMessage: String? = null, onRetryClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 70.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = errorMessage ?: stringResource(id = R.string.network_error),
            modifier = Modifier
                .padding(top = 10.dp, bottom = 18.dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = literataFont,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Button(onClick = onRetryClicked) {
            Text(
                text = stringResource(id = R.string.network_error_retry),
                fontWeight = FontWeight.Medium,
                fontFamily = interFont
            )
        }
    }
}


@Preview
@ExperimentalComposeUiApi
@Composable
fun NoInternetScreenPreview() {
    NetworkError {}
}
