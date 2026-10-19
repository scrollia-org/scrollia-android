package org.scrollia.app.ui.screens.reader.main.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.scrollia.app.R
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderFont
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderTheme
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderScreenState
import org.scrollia.app.ui.theme.interFont
import kotlinx.coroutines.launch

private enum class ControlButtonType { INCREASE, DECREASE }

@Composable
fun ReaderBottomBar(
    state: ReaderScreenState,
    showFontDialog: MutableState<Boolean>,
    snackBarHostState: SnackbarHostState,
    onFontSizeChanged: (newValue: Int) -> Unit,
    onLineHeightChanged: (newValue: Float) -> Unit,
    onReaderThemeChanged: (newValue: ReaderTheme) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(state.readerTheme.backgroundColor)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextScaleControls(
            fontSize = state.fontSize,
            textColor = state.readerTheme.textColor,
            snackBarHostState = snackBarHostState,
            onFontSizeChanged = onFontSizeChanged
        )
        Spacer(modifier = Modifier.height(16.dp))
        LineHeightControls(
            lineHeight = state.lineHeight,
            textColor = state.readerTheme.textColor,
            snackBarHostState = snackBarHostState,
            onLineHeightChanged = onLineHeightChanged
        )
        Spacer(modifier = Modifier.height(16.dp))
        FontSelectionButton(
            readerFontFamily = state.fontFamily,
            showFontDialog = showFontDialog,
            textColor = state.readerTheme.textColor
        )
        Spacer(modifier = Modifier.height(16.dp))
        ReaderThemeControls(
            readerTheme = state.readerTheme,
            onReaderThemeChanged = onReaderThemeChanged
        )
    }
}

@Composable
private fun ReaderThemeControls(
    readerTheme: ReaderTheme,
    onReaderThemeChanged: (ReaderTheme) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReaderTheme.entries.forEach { theme ->
            val label = when (theme) {
                ReaderTheme.Paper -> stringResource(id = R.string.reader_theme_paper)
                ReaderTheme.Sepia -> stringResource(id = R.string.reader_theme_sepia)
                ReaderTheme.Night -> stringResource(id = R.string.reader_theme_night)
            }
            val selected = theme == readerTheme
            FilledTonalButton(
                onClick = { onReaderThemeChanged(theme) },
                modifier = Modifier
                    .weight(1f)
                    .height(45.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = theme.backgroundColor,
                    contentColor = theme.textColor
                ),
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else theme.textColor.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = label,
                    fontFamily = interFont,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun TextScaleControls(
    fontSize: Int,
    textColor: Color,
    snackBarHostState: SnackbarHostState,
    onFontSizeChanged: (newValue: Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReaderFontSizeButton(
            buttonType = ControlButtonType.DECREASE,
            fontSize = fontSize,
            snackBarHostState = snackBarHostState,
            onFontSizeChanged = onFontSizeChanged
        )
        Box(
            modifier = Modifier
                .height(45.dp)
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ButtonDefaults.filledTonalButtonColors().containerColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fontSize.toString(),
                fontFamily = interFont,
                fontSize = 16.sp,
                color = textColor
            )
        }
        ReaderFontSizeButton(
            buttonType = ControlButtonType.INCREASE,
            fontSize = fontSize,
            snackBarHostState = snackBarHostState,
            onFontSizeChanged = onFontSizeChanged
        )
    }
}

@Composable
private fun LineHeightControls(
    lineHeight: Float,
    textColor: Color,
    snackBarHostState: SnackbarHostState,
    onLineHeightChanged: (newValue: Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReaderLineHeightButton(
            buttonType = ControlButtonType.DECREASE,
            lineHeight = lineHeight,
            snackBarHostState = snackBarHostState,
            onLineHeightChanged = onLineHeightChanged
        )
        Box(
            modifier = Modifier
                .height(45.dp)
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ButtonDefaults.filledTonalButtonColors().containerColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format(LocalConfiguration.current.locales[0], "%.1f", lineHeight),
                fontFamily = interFont,
                fontSize = 16.sp,
                color = textColor
            )
        }
        ReaderLineHeightButton(
            buttonType = ControlButtonType.INCREASE,
            lineHeight = lineHeight,
            snackBarHostState = snackBarHostState,
            onLineHeightChanged = onLineHeightChanged
        )
    }
}

@Composable
private fun FontSelectionButton(
    readerFontFamily: ReaderFont,
    showFontDialog: MutableState<Boolean>,
    textColor: Color
) {
    FilledTonalButton(
        onClick = { showFontDialog.value = true },
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(45.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_reader_font),
                contentDescription = null,
                tint = textColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = readerFontFamily.name,
                color = textColor
            )
        }
    }
}


@Composable
private fun ReaderFontSizeButton(
    buttonType: ControlButtonType,
    fontSize: Int,
    snackBarHostState: SnackbarHostState,
    onFontSizeChanged: (newValue: Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val (iconRes, adjustment) = remember(buttonType) {
        when (buttonType) {
            ControlButtonType.DECREASE -> Pair(R.drawable.ic_reader_text_minus, -5)
            ControlButtonType.INCREASE -> Pair(R.drawable.ic_reader_text_plus, 5)
        }
    }

    val callback: () -> Unit = {
        val newSize = fontSize + adjustment
        when {
            newSize < 50 -> {
                coroutineScope.launch {
                    snackBarHostState.showSnackbar(
                        context.getString(R.string.reader_min_font_size_reached),
                        null
                    )
                }
            }

            newSize > 200 -> {
                coroutineScope.launch {
                    snackBarHostState.showSnackbar(
                        context.getString(R.string.reader_max_font_size_reached),
                        null
                    )
                }
            }

            else -> {
                coroutineScope.launch {
                    onFontSizeChanged(newSize)
                }
            }
        }
    }

    FilledTonalButton(
        onClick = { callback() },
        modifier = Modifier.size(100.dp, 45.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
    }
}

@Composable
private fun ReaderLineHeightButton(
    buttonType: ControlButtonType,
    lineHeight: Float,
    snackBarHostState: SnackbarHostState,
    onLineHeightChanged: (newValue: Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val (iconRes, adjustment) = remember(buttonType) {
        when (buttonType) {
            ControlButtonType.DECREASE -> Pair(R.drawable.ic_reader_line_height_minus, -0.1f)
            ControlButtonType.INCREASE -> Pair(R.drawable.ic_reader_line_height_plus, 0.1f)
        }
    }

    val callback: () -> Unit = {
        val newValue = lineHeight + adjustment
        when {
            newValue < 1.0f -> {
                coroutineScope.launch {
                    snackBarHostState.showSnackbar(
                        context.getString(R.string.reader_min_line_height_reached),
                        null
                    )
                }
            }

            newValue > 3.0f -> {
                coroutineScope.launch {
                    snackBarHostState.showSnackbar(
                        context.getString(R.string.reader_max_line_height_reached),
                        null
                    )
                }
            }

            else -> {
                coroutineScope.launch {
                    onLineHeightChanged(newValue)
                }
            }
        }
    }

    FilledTonalButton(
        onClick = { callback() },
        modifier = Modifier.size(100.dp, 45.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
    }
}

@Composable
fun ReaderFontChooserDialog(
    showFontDialog: MutableState<Boolean>,
    fontFamily: ReaderFont,
    onFontFamilyChanged: (ReaderFont) -> Unit
) {
    val radioOptions = ReaderFont.getAllFonts().map { it.name }
    val (selectedOption, onOptionSelected) = remember {
        mutableStateOf(fontFamily)
    }

    if (showFontDialog.value) {
        AlertDialog(onDismissRequest = {
            showFontDialog.value = false
        }, title = {
            Text(
                text = stringResource(id = R.string.reader_font_style_chooser),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }, text = {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.Center,
            ) {
                radioOptions.forEach { text ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .selectable(
                                selected = (text == selectedOption.name),
                                onClick = { onOptionSelected(ReaderFont.getFontByName(text)) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (text == selectedOption.name),
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.inversePrimary,
                                disabledSelectedColor = Color.Black,
                                disabledUnselectedColor = Color.Black
                            ),
                        )
                        Text(
                            text = text,
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = interFont
                        )
                    }
                }
            }
        }, confirmButton = {
            FilledTonalButton(
                onClick = {
                    showFontDialog.value = false
                    onFontFamilyChanged(selectedOption)
                }, colors = ButtonDefaults.filledTonalButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(id = R.string.theme_dialog_apply_button))
            }
        }, dismissButton = {
            TextButton(onClick = {
                showFontDialog.value = false
            }) {
                Text(stringResource(id = R.string.cancel))
            }
        })
    }
}