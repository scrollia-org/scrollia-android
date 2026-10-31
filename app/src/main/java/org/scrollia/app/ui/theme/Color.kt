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

package org.scrollia.app.ui.theme

import androidx.compose.ui.graphics.Color

// "Paper and Ink" palette projected onto Material 3 roles.
// Token anchors: paper #FAF7F0, ink #1A1A18, ink-soft #5C5A52,
// library #2D5A3D, gold #B08D3E, border #E5E0D5; dark set dark-*.

// Light Theme Colors
val primaryLight = Color(0xFF2D5A3D)          // library
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFC9E7CF)
val onPrimaryContainerLight = Color(0xFF0E2317)
val secondaryLight = Color(0xFF4F6355)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFD2E5D6)
val onSecondaryContainerLight = Color(0xFF0E1F14)
val tertiaryLight = Color(0xFFB08D3E)         // gold (decorative accents only)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFF3E5C2)
val onTertiaryContainerLight = Color(0xFF3D2E08)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF410002)
val backgroundLight = Color(0xFFFAF7F0)       // paper
val onBackgroundLight = Color(0xFF1A1A18)     // ink
val surfaceLight = Color(0xFFFAF7F0)          // paper
val onSurfaceLight = Color(0xFF1A1A18)        // ink
val surfaceVariantLight = Color(0xFFECE7DB)
val onSurfaceVariantLight = Color(0xFF5C5A52) // ink-soft
val outlineLight = Color(0xFF8B8674)          // functional border (AA non-text)
val outlineVariantLight = Color(0xFFE5E0D5)   // border token (decorative)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2E2D28)
val inverseOnSurfaceLight = Color(0xFFF1EEE4)
val inversePrimaryLight = Color(0xFF8FB89B)
val surfaceDimLight = Color(0xFFDDD8CC)
val surfaceBrightLight = Color(0xFFFAF7F0)
val surfaceContainerLowestLight = Color(0xFFFFFFFF) // paper-raised
val surfaceContainerLowLight = Color(0xFFF4F1E8)
val surfaceContainerLight = Color(0xFFEFEAE0)
val surfaceContainerHighLight = Color(0xFFE9E4D8)
val surfaceContainerHighestLight = Color(0xFFE3DECF)

// Dark Theme Colors
val primaryDark = Color(0xFF8FB89B)           // desaturated light library green
val onPrimaryDark = Color(0xFF122417)
val primaryContainerDark = Color(0xFF244733)
val onPrimaryContainerDark = Color(0xFFC2E6CB)
val secondaryDark = Color(0xFFA3B5A4)
val onSecondaryDark = Color(0xFF1C2A1E)
val secondaryContainerDark = Color(0xFF37453A)
val onSecondaryContainerDark = Color(0xFFD5E2D3)
val tertiaryDark = Color(0xFFD4AF5E)          // brighter gold for dark bg
val onTertiaryDark = Color(0xFF3A2C08)
val tertiaryContainerDark = Color(0xFF5C4A1E)
val onTertiaryContainerDark = Color(0xFFF0DFAF)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF161815)        // dark-bg
val onBackgroundDark = Color(0xFFEDEAE0)      // dark-ink
val surfaceDark = Color(0xFF161815)           // dark-bg
val onSurfaceDark = Color(0xFFEDEAE0)         // dark-ink
val surfaceVariantDark = Color(0xFF44463B)
val onSurfaceVariantDark = Color(0xFFA8A598)
val outlineDark = Color(0xFF8E8B7D)           // functional border (AA non-text)
val outlineVariantDark = Color(0xFF35382E)    // dark border token
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFEDEAE0)
val inverseOnSurfaceDark = Color(0xFF2E2D28)
val inversePrimaryDark = Color(0xFF2D5A3D)
val surfaceDimDark = Color(0xFF161815)
val surfaceBrightDark = Color(0xFF3C3E36)
val surfaceContainerLowestDark = Color(0xFF10110E)
val surfaceContainerLowDark = Color(0xFF1D1F1A)
val surfaceContainerDark = Color(0xFF23251F)  // dark-paper
val surfaceContainerHighDark = Color(0xFF2D2F28)
val surfaceContainerHighestDark = Color(0xFF383A31)

// Reader themes, independent from the app theme.
val ReaderPaperBackground = Color(0xFFFAF7F0)
val ReaderPaperText = Color(0xFF1A1A18)
val ReaderSepiaBackground = Color(0xFFF4ECD8) // sepia-bg
val ReaderSepiaText = Color(0xFF433422)       // sepia-ink
val ReaderNightBackground = Color(0xFF161815) // dark-bg
val ReaderNightText = Color(0xFFEDEAE0)       // dark-ink
