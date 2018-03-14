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

package com.duckduckgo.app.tabs

import android.content.Intent
import com.duckduckgo.app.tabs.Intent.Companion.TAB_ID_EXTRA

class Intent {
    companion object {
        const val TAB_ID_EXTRA: String = "TAB_ID_EXTRA"
    }
}

var Intent.tabId: String?
    get() = getStringExtra(TAB_ID_EXTRA)
    set(value) {
        putExtra(TAB_ID_EXTRA, value)
    }
