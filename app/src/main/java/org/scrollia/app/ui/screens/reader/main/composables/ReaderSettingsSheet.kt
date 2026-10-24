package org.scrollia.app.ui.screens.reader.main.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.scrollia.app.R
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderFont
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderScreenState
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderTheme
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReadingMode
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont
import kotlin.math.roundToInt

const val READER_FONT_SIZE_MIN = 50
const val READER_FONT_SIZE_MAX = 200
private const val FONT_SIZE_STEP = 5
const val READER_LINE_HEIGHT_MIN = 1.0f
const val READER_LINE_HEIGHT_MAX = 3.0f
private const val LINE_HEIGHT_STEP = 0.1f

/**
 * Reading settings (docs/DESIGN.md §13). Every control applies live; colors come from the
 * active reader theme, not the app theme, so the sheet previews what the page will look like.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsSheet(
    state: ReaderScreenState,
    onDismiss: () -> Unit,
    onFontFamilyChanged: (ReaderFont) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onReaderThemeChanged: (ReaderTheme) -> Unit,
    onJustifyChanged: (Boolean) -> Unit,
    onReadingModeChanged: (ReadingMode) -> Unit,
) {
    val theme = state.readerTheme
    val ink = theme.textColor
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
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                text = stringResource(id = R.string.reader_settings_title),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontFamily = frauncesFont,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = ink
            )

            SheetSectionHeader(R.string.reader_settings_mode, ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingMode.entries.forEach { mode ->
                    ReaderChip(
                        selected = mode == state.readingMode,
                        onClick = { onReadingModeChanged(mode) },
                        ink = ink
                    ) {
                        Text(
                            text = stringResource(
                                id = when (mode) {
                                    ReadingMode.SCROLL -> R.string.reader_mode_scroll
                                    ReadingMode.PAGED -> R.string.reader_mode_paged
                                }
                            ),
                            fontFamily = interFont,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            SheetSectionHeader(R.string.reader_settings_font, ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFont.getAllFonts().forEach { font ->
                    ReaderChip(
                        selected = font == state.fontFamily,
                        onClick = { onFontFamilyChanged(font) },
                        ink = ink,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(text = fontLabel(font), fontFamily = font.fontFamily, fontSize = 15.sp)
                    }
                }
            }

            SheetSectionHeader(R.string.reader_settings_size, ink)
            SampleLine(state = state, fontSizePref = state.fontSize, lineHeight = 1.4f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A", fontFamily = state.fontFamily.fontFamily, fontSize = 13.sp, color = ink)
                Slider(
                    value = state.fontSize.toFloat(),
                    onValueChange = {
                        val snapped = ((it / FONT_SIZE_STEP).roundToInt() * FONT_SIZE_STEP)
                            .coerceIn(READER_FONT_SIZE_MIN, READER_FONT_SIZE_MAX)
                        if (snapped != state.fontSize) onFontSizeChanged(snapped)
                    },
                    valueRange = READER_FONT_SIZE_MIN.toFloat()..READER_FONT_SIZE_MAX.toFloat(),
                    steps = (READER_FONT_SIZE_MAX - READER_FONT_SIZE_MIN) / FONT_SIZE_STEP - 1,
                    colors = readerSliderColors(ink),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                Text("A", fontFamily = state.fontFamily.fontFamily, fontSize = 22.sp, color = ink)
            }
            ValueCaption(
                text = stringResource(id = R.string.reader_settings_size_value, state.fontSize),
                ink = ink
            )

            SheetSectionHeader(R.string.reader_settings_line_height, ink)
            SampleLine(
                state = state,
                fontSizePref = state.fontSize.coerceAtMost(110),
                lineHeight = state.lineHeight,
                maxLines = 2
            )
            Slider(
                value = state.lineHeight,
                onValueChange = {
                    val snapped = ((it / LINE_HEIGHT_STEP).roundToInt() * LINE_HEIGHT_STEP)
                        .coerceIn(READER_LINE_HEIGHT_MIN, READER_LINE_HEIGHT_MAX)
                    if (snapped != state.lineHeight) onLineHeightChanged(snapped)
                },
                valueRange = READER_LINE_HEIGHT_MIN..READER_LINE_HEIGHT_MAX,
                steps = ((READER_LINE_HEIGHT_MAX - READER_LINE_HEIGHT_MIN) / LINE_HEIGHT_STEP)
                    .roundToInt() - 1,
                colors = readerSliderColors(ink),
            )
            ValueCaption(
                text = String.format(LocalConfiguration.current.locales[0], "%.1f", state.lineHeight),
                ink = ink
            )

            SheetSectionHeader(R.string.reader_settings_theme, ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { option ->
                    ThemeSwatch(
                        theme = option,
                        selected = option == theme,
                        selectedBorder = ink,
                        onClick = { onReaderThemeChanged(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.reader_settings_justify),
                        fontFamily = interFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = ink
                    )
                    Text(
                        text = stringResource(id = R.string.reader_settings_justify_desc),
                        fontFamily = interFont,
                        fontSize = 12.sp,
                        color = ink.copy(alpha = 0.65f)
                    )
                }
                Switch(
                    checked = state.justifyText,
                    onCheckedChange = onJustifyChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.backgroundColor,
                        checkedTrackColor = ink.copy(alpha = 0.8f),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = ink.copy(alpha = 0.6f),
                        uncheckedTrackColor = ink.copy(alpha = 0.08f),
                        uncheckedBorderColor = ink.copy(alpha = 0.4f),
                    )
                )
            }
        }
    }
}

@Composable
private fun fontLabel(font: ReaderFont): String = when (font) {
    ReaderFont.System -> stringResource(id = R.string.reader_font_system)
    else -> font.name
}

@Composable
private fun SheetSectionHeader(titleRes: Int, ink: Color) {
    Text(
        text = stringResource(id = titleRes).uppercase(LocalConfiguration.current.locales[0]),
        fontFamily = interFont,
        color = ink.copy(alpha = 0.65f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun ValueCaption(text: String, ink: Color) {
    Text(
        text = text,
        fontFamily = interFont,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = ink.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/** A line of book text rendered with the current reader typography: the preview is the control. */
@Composable
private fun SampleLine(
    state: ReaderScreenState,
    fontSizePref: Int,
    lineHeight: Float,
    maxLines: Int = 1
) {
    Text(
        text = stringResource(id = R.string.reader_settings_sample),
        fontFamily = state.fontFamily.fontFamily,
        fontSize = ReaderLayout.fontSizeSp(fontSizePref).sp,
        lineHeight = lineHeight.em,
        color = state.readerTheme.textColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
private fun ThemeSwatch(
    theme: ReaderTheme,
    selected: Boolean,
    selectedBorder: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (theme) {
        ReaderTheme.Paper -> stringResource(id = R.string.reader_theme_paper)
        ReaderTheme.Sepia -> stringResource(id = R.string.reader_theme_sepia)
        ReaderTheme.Night -> stringResource(id = R.string.reader_theme_night)
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(8.dp),
        color = theme.backgroundColor,
        contentColor = theme.textColor,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) selectedBorder else theme.textColor.copy(alpha = 0.25f)
        )
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Aa",
                fontFamily = frauncesFont,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                color = theme.textColor
            )
            Text(text = label, fontFamily = interFont, fontSize = 12.sp, color = theme.textColor)
        }
    }
}

/** Chip in the reader's ink: 4 dp radius, check mark when selected. */
@Composable
private fun ReaderChip(
    selected: Boolean,
    onClick: () -> Unit,
    ink: Color,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else null,
        shape = RoundedCornerShape(4.dp),
        colors = readerChipColors(ink),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = ink.copy(alpha = 0.3f),
            selectedBorderColor = ink.copy(alpha = 0.6f)
        ),
        modifier = modifier
    )
}

@Composable
private fun readerChipColors(ink: Color) = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = ink,
    iconColor = ink,
    selectedContainerColor = ink.copy(alpha = 0.1f),
    selectedLabelColor = ink,
    selectedLeadingIconColor = ink,
)

@Composable
private fun readerSliderColors(ink: Color) = SliderDefaults.colors(
    thumbColor = ink,
    activeTrackColor = ink.copy(alpha = 0.8f),
    inactiveTrackColor = ink.copy(alpha = 0.15f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)
