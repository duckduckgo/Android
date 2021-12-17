/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.global.view

import android.text.Editable
import android.text.TextWatcher

abstract class TextChangedWatcher : TextWatcher {

    abstract override fun afterTextChanged(editable: Editable)

    override fun beforeTextChanged(p0: CharSequence, p1: Int, p2: Int, p3: Int) {}
    override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {}
}
