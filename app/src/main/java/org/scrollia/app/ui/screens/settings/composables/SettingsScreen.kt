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

package org.scrollia.app.ui.screens.settings.composables

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import org.scrollia.app.MainActivity
import org.scrollia.app.R
import org.scrollia.app.helpers.Utils
import org.scrollia.app.helpers.getActivity
import org.scrollia.app.ui.common.CustomTopAppBar
import org.scrollia.app.ui.navigation.Screens
import org.scrollia.app.ui.screens.main.bottomNavPadding
import org.scrollia.app.ui.screens.settings.viewmodels.SettingsViewModel
import org.scrollia.app.ui.screens.settings.viewmodels.ThemeMode
import org.scrollia.app.ui.theme.interFont
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel = (context.getActivity() as MainActivity).settingsViewModel

    val snackBarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = Modifier.padding(bottom = bottomNavPadding),
        snackbarHost = { SnackbarHost(snackBarHostState) },
        topBar = {
            CustomTopAppBar(
                headerText = stringResource(id = R.string.settings_header),
                iconRes = R.drawable.ic_nav_settings
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Ordered by frequency of use: appearance first, legal/about last.
                AppearanceOptionsUI(viewModel = viewModel)
                ReaderOptionsUI(viewModel = viewModel)
                GeneralOptionsUI(viewModel = viewModel, snackBarHostState = snackBarHostState)
                AboutUI(navController = navController)
            }
        }
    }
}

/**
 * Section header following the editorial "caption" style from
 * docs/DESIGN.md: Inter medium, small, uppercase, wide tracking.
 * Never gold (tertiary) text — it fails contrast on paper.
 */
@Composable
private fun SettingsSectionHeader(titleRes: Int) {
    Text(
        text = stringResource(id = titleRes).uppercase(Locale.getDefault()),
        fontFamily = interFont,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun AppearanceOptionsUI(viewModel: SettingsViewModel) {
    val context = LocalContext.current

    // Display settings for the theme
    val appThemeState = viewModel.theme.observeAsState(initial = ThemeMode.Auto)
    val selectedAppTheme = when (appThemeState.value) {
        ThemeMode.Light -> stringResource(id = R.string.theme_option_light)
        ThemeMode.Dark -> stringResource(id = R.string.theme_option_dark)
        else -> stringResource(id = R.string.theme_option_system)
    }
    val appThemeDialog = remember { mutableStateOf(false) }
    val appThemeRadioOpts = listOf(
        stringResource(id = R.string.theme_option_light),
        stringResource(id = R.string.theme_option_dark),
        stringResource(id = R.string.theme_option_system)
    )
    val (selectedOption, onOptionSelected) = remember { mutableStateOf(selectedAppTheme) }

    // Display settings for the amoled theme
    val amoledState = viewModel.amoledTheme.observeAsState(initial = false)

    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        SettingsSectionHeader(titleRes = R.string.settings_section_appearance)
        SettingItem(
            icon = Icons.Filled.Build,
            mainText = stringResource(id = R.string.theme_setting),
            subText = selectedAppTheme,
            onClick = { appThemeDialog.value = true }
        )
        SettingItemWIthSwitch(
            icon = Icons.Filled.Settings,
            mainText = stringResource(id = R.string.amoled_theme_setting),
            subText = stringResource(id = R.string.amoled_theme_setting_desc),
            switchState = amoledState,
            onCheckChange = {
                viewModel.setAmoledTheme(it)
            })
    }

    if (appThemeDialog.value) {
        AlertDialog(onDismissRequest = {
            appThemeDialog.value = false
        }, title = {
            Text(
                text = stringResource(id = R.string.theme_dialog_title),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }, text = {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.Center,
            ) {
                appThemeRadioOpts.forEach { text ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .selectable(
                                selected = (text == selectedOption),
                                onClick = { onOptionSelected(text) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (text == selectedOption),
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
                    appThemeDialog.value = false

                    when (selectedOption) {
                        context.getString(R.string.theme_option_light) -> {
                            viewModel.setTheme(ThemeMode.Light)
                        }

                        context.getString(R.string.theme_option_dark) -> {
                            viewModel.setTheme(ThemeMode.Dark)
                        }

                        context.getString(R.string.theme_option_system) -> {
                            viewModel.setTheme(ThemeMode.Auto)
                        }
                    }
                }, colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(id = R.string.theme_dialog_apply_button))
            }
        }, dismissButton = {
            TextButton(onClick = {
                appThemeDialog.value = false
            }) {
                Text(stringResource(id = R.string.cancel))
            }
        })
    }
}


@Composable
private fun ReaderOptionsUI(viewModel: SettingsViewModel) {
    val context = LocalContext.current

    val internalReaderState = viewModel.internalReader.observeAsState(initial = true)

    val internalReaderValue = when (internalReaderState.value) {
        true -> stringResource(id = R.string.reader_option_inbuilt)
        false -> stringResource(id = R.string.reader_option_external)
    }
    val showReaderDialog = remember { mutableStateOf(false) }
    val radioOptions = listOf(
        stringResource(id = R.string.reader_option_inbuilt),
        stringResource(id = R.string.reader_option_external)
    )
    val (selectedOption, onOptionSelected) = remember { mutableStateOf(internalReaderValue) }

    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        SettingsSectionHeader(titleRes = R.string.settings_section_reader)
        SettingItem(
            icon = ImageVector.vectorResource(id = R.drawable.ic_settings_reader),
            mainText = stringResource(id = R.string.default_reader_setting),
            subText = internalReaderValue,
            onClick = { showReaderDialog.value = true }
        )
    }

    if (showReaderDialog.value) {
        AlertDialog(onDismissRequest = {
            showReaderDialog.value = false
        }, title = {
            Text(
                text = stringResource(id = R.string.default_reader_dialog_title),
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
                                selected = (text == selectedOption),
                                onClick = { onOptionSelected(text) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (text == selectedOption),
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
                    showReaderDialog.value = false

                    when (selectedOption) {
                        context.getString(R.string.reader_option_inbuilt) -> {
                            viewModel.setInternalReaderValue(true)
                        }

                        context.getString(R.string.reader_option_external) -> {
                            viewModel.setInternalReaderValue(false)
                        }
                    }
                }, colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(id = R.string.confirm))
            }
        }, dismissButton = {
            TextButton(onClick = {
                showReaderDialog.value = false
            }) {
                Text(stringResource(id = R.string.cancel))
            }
        })
    }
}


@Composable
private fun GeneralOptionsUI(
    viewModel: SettingsViewModel,
    snackBarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val googleBooksApiSwitchState = viewModel.useGoogleApi.observeAsState(initial = true)
    val openLibraryAtStartState = viewModel.openLibraryAtStart.observeAsState(initial = false)

    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        SettingsSectionHeader(titleRes = R.string.general_settings_header)
        SettingItem(
            icon = Icons.Filled.Place,
            mainText = stringResource(id = R.string.default_locale_setting),
            subText = stringResource(id = R.string.default_locale_setting_desc),
            onClick = {
                // App locale setting is only available on Android 13 and above.
                // Also, it's not functional on devices running MIUI even on Android 13,
                // Thanks to Xiaomi's broken implementation of standard Android APIs.
                // See: https://github.com/Pool-Of-Tears/GreenStash/issues/130 for more.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Utils.isMiui()) {
                    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                } else {
                    coroutineScope.launch {
                        snackBarHostState.showSnackbar(
                            context.getString(R.string.locale_setting_not_found)
                        )
                    }
                }
            }
        )
        SettingItemWIthSwitch(
            icon = ImageVector.vectorResource(id = R.drawable.ic_settings_library_start),
            mainText = stringResource(id = R.string.open_library_at_start_setting),
            subText = stringResource(id = R.string.open_library_at_start_setting_desc),
            switchState = openLibraryAtStartState,
            onCheckChange = {
                viewModel.setOpenLibraryAtStartValue(it)
            }
        )
        SettingItemWIthSwitch(
            icon = ImageVector.vectorResource(id = R.drawable.ic_settings_google_api),
            mainText = stringResource(id = R.string.extra_info_setting),
            subText = stringResource(id = R.string.extra_info_setting_desc),
            switchState = googleBooksApiSwitchState,
            onCheckChange = {
                viewModel.setUseGoogleApiValue(it)
            }
        )
    }
}


@Composable
private fun AboutUI(navController: NavController) {
    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        SettingsSectionHeader(titleRes = R.string.settings_section_about)
        SettingItem(
            icon = Icons.Filled.Info,
            mainText = stringResource(id = R.string.about_setting),
            subText = stringResource(id = R.string.about_setting_desc),
            onClick = { navController.navigate(Screens.AboutScreen.route) }
        )
        SettingItem(
            icon = Icons.AutoMirrored.Filled.List,
            mainText = stringResource(id = R.string.license_setting),
            subText = stringResource(id = R.string.license_setting_desc),
            onClick = { navController.navigate(Screens.OSLScreen.route) }
        )
    }
}
