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

package org.scrollia.app.ui.screens.settings.viewmodels

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.scrollia.app.helpers.PreferenceUtil
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class ThemeMode {
    Light, Dark, Auto, Amoled
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceUtil: PreferenceUtil
) : ViewModel() {

    private val _theme = MutableLiveData(ThemeMode.Auto)

    val theme: LiveData<ThemeMode> = _theme

    init {
        _theme.value = ThemeMode.entries.toTypedArray()[getThemeValue()]
    }

    // Getters =============================================================================

    fun setTheme(newTheme: ThemeMode, systemDark: Boolean, updateReader: Boolean = true) {
        _theme.postValue(newTheme)
        preferenceUtil.putInt(PreferenceUtil.APP_THEME_INT, newTheme.ordinal)
        if (updateReader) {
            val readerTheme = when (newTheme) {
                ThemeMode.Light -> ReaderTheme.Paper
                ThemeMode.Dark -> ReaderTheme.Night
                ThemeMode.Amoled -> ReaderTheme.Amoled
                ThemeMode.Auto -> if (systemDark) ReaderTheme.Night else ReaderTheme.Paper
            }
            preferenceUtil.putString(PreferenceUtil.READER_THEME_STR, readerTheme.id)
        }
    }

    fun refreshTheme() {
        _theme.value = ThemeMode.entries.toTypedArray()[getThemeValue()]
    }

    // Getters ============================================================================
    // Used only during initialization except getCurrentTheme()
    private fun getThemeValue(): Int {
        if (preferenceUtil.getBoolean(PreferenceUtil.AMOLED_THEME_BOOL, false)) {
            preferenceUtil.putBoolean(PreferenceUtil.AMOLED_THEME_BOOL, false)
            preferenceUtil.putInt(PreferenceUtil.APP_THEME_INT, ThemeMode.Amoled.ordinal)
            return ThemeMode.Amoled.ordinal
        }
        return preferenceUtil.getInt(PreferenceUtil.APP_THEME_INT, ThemeMode.Auto.ordinal)
    }

    @Composable
    fun getCurrentTheme(): ThemeMode {
        return when (theme.value) {
            ThemeMode.Auto -> if (isSystemInDarkTheme()) ThemeMode.Dark else ThemeMode.Light
            ThemeMode.Amoled -> ThemeMode.Dark
            else -> theme.value!!
        }
    }
}