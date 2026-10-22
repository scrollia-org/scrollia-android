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


package org.scrollia.app.ui.screens.welcome.composables

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.scrollia.app.R
import org.scrollia.app.helpers.weakHapticFeedback
import org.scrollia.app.ui.navigation.BottomBarScreen
import org.scrollia.app.ui.screens.welcome.viewmodels.WelcomeViewModel
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont
import kotlinx.coroutines.delay

// "Papel y Tinta" dark tokens (docs/DESIGN.md). This screen is intentionally
// always dark — the cinematic metaphor is darkness (before writing) to light
// (the printed book) — regardless of the app theme.
private val DarkBackground = Color(0xFF161815) // dark-bg
private val GoldAccent = Color(0xFFB08D3E)     // gold
private val ParchmentText = Color(0xFFEAD9B8)  // parchment
private val DarkInk = Color(0xFFEDEAE0)        // dark-ink (used at reduced alpha)

// Timeline of the evolution of writing. The glyphs are decorative; if a
// device lacks the font for one, the label and year still carry the meaning.
private data class WritingEra(
    val symbol: String,
    @StringRes val labelRes: Int,
    @StringRes val yearRes: Int
)

private val writingTimeline = listOf(
    WritingEra("𒀭", R.string.welcome_era_cuneiform, R.string.welcome_era_cuneiform_year),
    WritingEra("𓂀", R.string.welcome_era_hieroglyphics, R.string.welcome_era_hieroglyphics_year),
    WritingEra("𐤀", R.string.welcome_era_phoenician, R.string.welcome_era_phoenician_year),
    WritingEra("Α", R.string.welcome_era_greek, R.string.welcome_era_greek_year),
    WritingEra("A", R.string.welcome_era_latin, R.string.welcome_era_latin_year),
    WritingEra("活", R.string.welcome_era_movable_type, R.string.welcome_era_movable_type_year),
    WritingEra("G", R.string.welcome_era_printing_press, R.string.welcome_era_printing_press_year),
)

@Composable
fun WelcomeScreen(navController: NavController) {
    val view = LocalView.current
    val viewModel: WelcomeViewModel = hiltViewModel()

    // Animation state
    var currentPhase by remember { mutableIntStateOf(0) }
    var currentEraIndex by remember { mutableIntStateOf(0) }
    var showTitle by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }
    var skipToEnd by remember { mutableStateOf(false) }

    // Glow animation
    val glowAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Phase 0: darkness
        delay(400)

        // Phase 1: glow appears
        if (!skipToEnd) {
            currentPhase = 1
            glowAlpha.animateTo(0.6f, tween(900, easing = FastOutSlowInEasing))
        }

        // Phase 2: cycle through the writing eras (~550ms each)
        if (!skipToEnd) {
            currentPhase = 2
            for (i in writingTimeline.indices) {
                currentEraIndex = i
                delay(550)
                if (skipToEnd) break
            }
            delay(300)
        }

        // Phase 3: title appears
        showTitle = true
        if (!skipToEnd) delay(900)

        // Phase 4: tagline
        showTagline = true
        if (!skipToEnd) delay(400)

        // Phase 5: button
        showButton = true
    }

    // Tapping anywhere once the intro started skips straight to the final
    // state; the "Start Reading" button remains the only way out.
    LaunchedEffect(skipToEnd) {
        if (skipToEnd) {
            currentPhase = 2
            glowAlpha.snapTo(0.6f)
            showTitle = true
            showTagline = true
            showButton = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .pointerInput(Unit) {
                detectTapGestures { skipToEnd = true }
            },
        contentAlignment = Alignment.Center
    ) {
        // Warm glow behind the center
        if (currentPhase >= 1) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .alpha(glowAlpha.value)
                    .blur(80.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    GoldAccent.copy(alpha = 0.4f),
                                    GoldAccent.copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2, size.height / 2),
                                radius = size.width / 2
                            )
                        )
                    }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Symbol display area
            if (currentPhase >= 2 && !showTitle) {
                AnimatedContent(
                    targetState = currentEraIndex,
                    transitionSpec = {
                        (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.92f))
                            .togetherWith(fadeOut(tween(300)))
                    },
                    label = "writingEra"
                ) { eraIndex ->
                    val era = writingTimeline[eraIndex]
                    val eraLabel = stringResource(era.labelRes)
                    val eraYear = stringResource(era.yearRes)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics {
                            contentDescription = "$eraLabel, $eraYear"
                        }
                    ) {
                        Text(
                            text = era.symbol,
                            fontSize = 72.sp,
                            color = ParchmentText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = eraLabel,
                            fontSize = 14.sp,
                            fontFamily = interFont,
                            fontWeight = FontWeight.Light,
                            color = DarkInk.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                            letterSpacing = 3.sp
                        )
                        Text(
                            text = eraYear,
                            fontSize = 12.sp,
                            fontFamily = interFont,
                            fontWeight = FontWeight.Normal,
                            color = DarkInk.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Title: Scrollia
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(900)) + scaleIn(
                    tween(900),
                    initialScale = 0.9f
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 52.sp,
                        fontFamily = frauncesFont,
                        fontWeight = FontWeight.Bold,
                        color = ParchmentText,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.welcome_est),
                        fontSize = 13.sp,
                        fontFamily = interFont,
                        fontWeight = FontWeight.Light,
                        fontStyle = FontStyle.Italic,
                        color = GoldAccent.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        letterSpacing = 4.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Tagline
            AnimatedVisibility(
                visible = showTagline,
                enter = fadeIn(tween(400)),
            ) {
                Text(
                    text = stringResource(R.string.welcome_tagline),
                    fontSize = 16.sp,
                    fontFamily = interFont,
                    fontWeight = FontWeight.Normal,
                    color = DarkInk.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Button
            AnimatedVisibility(
                visible = showButton,
                enter = fadeIn(tween(400)),
            ) {
                Button(
                    onClick = {
                        view.weakHapticFeedback()
                        viewModel.saveOnBoardingState(completed = true)
                        viewModel.setInternalReaderSetting(true)
                        navController.popBackStack()
                        navController.navigate(BottomBarScreen.Home.route)
                    },
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldAccent.copy(alpha = 0.15f),
                        contentColor = ParchmentText
                    )
                ) {
                    Text(
                        text = stringResource(R.string.welcome_start_button),
                        fontWeight = FontWeight.Medium,
                        fontFamily = interFont,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
