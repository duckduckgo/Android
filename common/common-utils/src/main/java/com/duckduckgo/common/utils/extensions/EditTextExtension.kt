/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.common.utils.extensions

import android.widget.EditText
import com.duckduckgo.common.utils.text.TextChangedWatcher

fun EditText.isDifferent(newInput: String?): Boolean = text.toString() != newInput

fun EditText.updateIfDifferent(newInput: String) {
    if (isDifferent(newInput)) {
        setText(newInput)
    }
}

fun EditText.replaceTextChangedListener(textWatcher: TextChangedWatcher) {
    removeTextChangedListener(textWatcher)
    addTextChangedListener(textWatcher)
}
