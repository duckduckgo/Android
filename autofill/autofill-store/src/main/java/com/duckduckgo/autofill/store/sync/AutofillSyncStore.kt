/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.store.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.sync.api.engine.FeatureSyncStore

class AutofillSyncStore constructor(private val context: Context) : FeatureSyncStore {
    override var modifiedSince: String
        get() = preferences.getString(KEY_MODIFIED_SINCE, "0") ?: "0"
        set(value) = preferences.edit(true) { putString(KEY_MODIFIED_SINCE, value) }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.autofill.sync.store"
        private const val KEY_MODIFIED_SINCE = "KEY_MODIFIED_SINCE"
    }
}
