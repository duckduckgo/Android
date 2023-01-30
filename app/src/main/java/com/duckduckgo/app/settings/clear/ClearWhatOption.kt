/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.settings.clear

import com.duckduckgo.app.settings.clear.ClearWhatOption.CLEAR_NONE
import com.duckduckgo.app.settings.clear.ClearWhatOption.CLEAR_TABS_AND_DATA
import com.duckduckgo.app.settings.clear.ClearWhatOption.CLEAR_TABS_ONLY

enum class ClearWhatOption {
    CLEAR_NONE,
    CLEAR_TABS_ONLY,
    CLEAR_TABS_AND_DATA,
    ;

    fun getOptionIndex(): Int {
        return when (this) {
            CLEAR_NONE -> 1
            CLEAR_TABS_ONLY -> 2
            CLEAR_TABS_AND_DATA -> 3
        }
    }
}

fun Int.getClearWhatOptionForIndex(): ClearWhatOption {
    return when (this) {
        2 -> CLEAR_TABS_ONLY
        3 -> CLEAR_TABS_AND_DATA
        else -> CLEAR_NONE
    }
}
