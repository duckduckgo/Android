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

enum class AutomaticFireproofDialogOptions(val order: Int, @StringRes val stringRes: Int) {
    ALWAYS(0, R.string.automaticFireproofWebsiteLoginDialogFirstOption),
    FIREPROOF_THIS_SITE(1, R.string.automaticFireproofWebsiteLoginDialogSecondOption),
    NOT_NOW(2, R.string.automaticFireproofWebsiteLoginDialogThirdOption),
    ;

    companion object {
        fun asOptions(): List<Int> {
            return listOf(ALWAYS.stringRes, FIREPROOF_THIS_SITE.stringRes, NOT_NOW.stringRes)
        }
        fun getOptionFromPosition(position: Int): AutomaticFireproofDialogOptions {
            var defaultOption = ALWAYS
            values().forEach {
                if (it.order == position) {
                    defaultOption = it
                }
            }
            return defaultOption
        }
    }
}
