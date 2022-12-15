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

package com.duckduckgo.app.browser.ui.dialogs

import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R

enum class LaunchInExternalAppOptions(val order: Int, @StringRes val stringRes: Int) {
    OPEN(0, R.string.open),
    CLOSE_TAB(1, R.string.closeTab),
    CANCEL(2, R.string.cancel),
    ;

    companion object {
        fun asOptions(): List<Int> {
            return listOf(OPEN.stringRes, CLOSE_TAB.stringRes, CANCEL.stringRes)
        }
        fun getOptionFromPosition(position: Int): LaunchInExternalAppOptions {
            var defaultOption = OPEN
            values().forEach {
                if (it.order == position) {
                    defaultOption = it
                }
            }
            return defaultOption
        }
    }
}
