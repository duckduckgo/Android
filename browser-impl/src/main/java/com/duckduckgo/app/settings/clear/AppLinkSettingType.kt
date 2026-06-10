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

package com.duckduckgo.app.settings.clear

enum class AppLinkSettingType {
    ASK_EVERYTIME,
    ALWAYS,
    NEVER,
    ;

    fun getOptionIndex(): Int {
        return when (this) {
            ASK_EVERYTIME -> 1
            ALWAYS -> 2
            NEVER -> 3
        }
    }
}

fun Int.getAppLinkSettingForIndex(): AppLinkSettingType {
    return when (this) {
        2 -> AppLinkSettingType.ALWAYS
        3 -> AppLinkSettingType.NEVER
        else -> AppLinkSettingType.ASK_EVERYTIME
    }
}
