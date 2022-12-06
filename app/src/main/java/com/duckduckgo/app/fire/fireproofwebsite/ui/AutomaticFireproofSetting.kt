/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.fire.fireproofwebsite.ui

import com.duckduckgo.app.browser.R

enum class AutomaticFireproofSetting(val stringRes: Int) {
    ASK_EVERY_TIME(R.string.fireproofWebsiteSettingsSelectionDialogAskEveryTime),
    ALWAYS(R.string.fireproofWebsiteSettingsSelectionDialogAlways),
    NEVER(R.string.fireproofWebsiteSettingsSelectionDialogNever),
    ;

    fun getOptionIndex(): Int {
        return when (this) {
            ASK_EVERY_TIME -> 1
            ALWAYS -> 2
            NEVER -> 3
        }
    }

    companion object {
        fun Int.getFireproofSettingOptionForIndex(): AutomaticFireproofSetting {
            return when (this) {
                2 -> ALWAYS
                3 -> NEVER
                else -> ASK_EVERY_TIME
            }
        }
    }
}
