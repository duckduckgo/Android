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

import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_ONLY
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_OR_15_MINS
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_OR_30_MINS
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_OR_5_MINS
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_OR_5_SECONDS
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_OR_60_MINS

enum class ClearWhenOption {
    APP_EXIT_ONLY,
    APP_EXIT_OR_5_MINS,
    APP_EXIT_OR_15_MINS,
    APP_EXIT_OR_30_MINS,
    APP_EXIT_OR_60_MINS,

    // only available to debug builds
    APP_EXIT_OR_5_SECONDS,
    ;

    fun getOptionIndex(): Int {
        return when (this) {
            APP_EXIT_ONLY -> 1
            APP_EXIT_OR_5_MINS -> 2
            APP_EXIT_OR_15_MINS -> 3
            APP_EXIT_OR_30_MINS -> 4
            APP_EXIT_OR_60_MINS -> 5
            APP_EXIT_OR_5_SECONDS -> 6
        }
    }
}

fun Int.getClearWhenForIndex(): ClearWhenOption {
    return when (this) {
        2 -> APP_EXIT_OR_5_MINS
        3 -> APP_EXIT_OR_15_MINS
        4 -> APP_EXIT_OR_30_MINS
        5 -> APP_EXIT_OR_60_MINS
        6 -> APP_EXIT_OR_5_SECONDS
        else -> APP_EXIT_ONLY
    }
}
