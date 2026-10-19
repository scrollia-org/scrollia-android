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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.scrollia.app.helpers.weakHapticFeedback
import org.scrollia.app.ui.navigation.BottomBarScreen
import org.scrollia.app.ui.screens.welcome.viewmodels.WelcomeViewModel
import org.scrollia.app.ui.theme.playfairFont
import org.scrollia.app.ui.theme.poppinsFont
import kotlinx.coroutines.delay

// Colors
private val DarkBackground = Color(0xFF0A0A14)
private val WarmGlow = Color(0xFFD4A574)
private val CreamText = Color(0xFFF5E6C8)
private val SubtleText = Color(0xFF8A8A9A)

// Timeline symbols representing the evolution of writing
private data class WritingEra(
    val symbol: String,
    val label: String,
    val year: String
)

private val writingTimeline = listOf(
    WritingEra("𒀭", "Cuneiform", "3,400 BC"),
    WritingEra("𓂀", "Hieroglyphics", "3,200 BC"),
    WritingEra("𐤀", "Phoenician", "1,050 BC"),
    WritingEra("Α", "Greek", "750 BC"),
    WritingEra("A", "Latin", "600 BC"),
    WritingEra("活", "Movable Type", "1,040 AD"),
)

@Composable
fun WelcomeScreen(navController: NavController) {
    val view = LocalView.current
    val viewModel: WelcomeViewModel = hiltViewModel()

    // Animation state
    var currentPhase by remember { mutableIntStateOf(0) }
    var currentSymbolIndex by remember { mutableIntStateOf(0) }
    var showTitle by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

    // Glow animation
    val glowAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Phase 0: darkness
        delay(500)

        // Phase 1: glow appears
        currentPhase = 1
        glowAlpha.animateTo(0.6f, tween(1500, easing = FastOutSlowInEasing))

        // Phase 2: cycle through writing symbols
        currentPhase = 2
        for (i in writingTimeline.indices) {
            currentSymbolIndex = i
            delay(900)
        }

        // Phase 3: title appears
        delay(300)
        showTitle = true
        delay(1200)

        // Phase 4: tagline
        showTagline = true
        delay(800)

        // Phase 5: button
        showButton = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
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
                                    WarmGlow.copy(alpha = 0.4f),
                                    WarmGlow.copy(alpha = 0.1f),
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
                val era = writingTimeline[currentSymbolIndex]
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(400)) + scaleIn(
                        tween(400),
                        initialScale = 0.8f
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = era.symbol,
                            fontSize = 72.sp,
                            color = CreamText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = era.label,
                            fontSize = 14.sp,
                            fontFamily = poppinsFont,
                            fontWeight = FontWeight.Light,
                            color = SubtleText,
                            textAlign = TextAlign.Center,
                            letterSpacing = 3.sp
                        )
                        Text(
                            text = era.year,
                            fontSize = 12.sp,
                            fontFamily = poppinsFont,
                            fontWeight = FontWeight.Normal,
                            color = SubtleText.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Title: Scrollia
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(1000)) + scaleIn(
                    tween(1000),
                    initialScale = 0.9f
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Scrollia",
                        fontSize = 52.sp,
                        fontFamily = playfairFont,
                        fontWeight = FontWeight.Bold,
                        color = CreamText,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Est. 1455",
                        fontSize = 13.sp,
                        fontFamily = poppinsFont,
                        fontWeight = FontWeight.Light,
                        fontStyle = FontStyle.Italic,
                        color = WarmGlow.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        letterSpacing = 4.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Tagline
            AnimatedVisibility(
                visible = showTagline,
                enter = fadeIn(tween(800)),
            ) {
                Text(
                    text = "5,400 years of reading",
                    fontSize = 16.sp,
                    fontFamily = poppinsFont,
                    fontWeight = FontWeight.Normal,
                    color = SubtleText,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Button
            AnimatedVisibility(
                visible = showButton,
                enter = fadeIn(tween(600)),
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
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmGlow.copy(alpha = 0.15f),
                        contentColor = CreamText
                    )
                ) {
                    Text(
                        text = "Start Reading",
                        fontWeight = FontWeight.Medium,
                        fontFamily = poppinsFont,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
