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

package org.scrollia.app.ui.screens.reader.main.viewmodel

import android.content.pm.ActivityInfo

/**
 * How [org.scrollia.app.ui.screens.reader.main.activities.ReaderActivity]
 * should lock the screen while reading. Stored by [id] in preferences.
 */
enum class ReaderOrientation(val id: String, val activityInfo: Int) {
    System("system", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    Portrait("portrait", ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    Landscape("landscape", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

    companion object {
        fun getById(id: String?): ReaderOrientation =
            entries.firstOrNull { it.id == id } ?: System
    }
}
