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

package com.duckduckgo.sync.impl.favicons

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.sync.api.favicons.FaviconsFetchingStore

class SyncFaviconFetchingStore(
    context: Context,
) : FaviconsFetchingStore {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override var isFaviconsFetchingEnabled: Boolean
        get() = preferences.getBoolean(KEY_FAVICONS_FETCHING_ENABLED, false)
        set(enabled) = preferences.edit(commit = true) { putBoolean(KEY_FAVICONS_FETCHING_ENABLED, enabled) }

    override var promptShown: Boolean
        get() = preferences.getBoolean(KEY_FAVICONS_PROMPT_SHOWN, false)
        set(enabled) = preferences.edit(commit = true) { putBoolean(KEY_FAVICONS_PROMPT_SHOWN, enabled) }

    companion object {
        private const val FILENAME = "com.duckduckgo.favicons.store.v1"
        private const val KEY_FAVICONS_FETCHING_ENABLED = "KEY_FAVICONS_FETCHING_ENABLED"
        private const val KEY_FAVICONS_PROMPT_SHOWN = "KEY_FAVICONS_PROMPT_SHOWN"
    }
}
