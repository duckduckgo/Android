/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.features.browseruilock.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface BrowserUiLockStore {
    var json: String
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrowserUiLockStore @Inject constructor(
    private val context: Context,
) : BrowserUiLockStore {

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override var json: String
        get() = preferences.getString(KEY_JSON, DEFAULT_JSON) ?: DEFAULT_JSON
        set(value) {
            preferences.edit { putString(KEY_JSON, value) }
        }

    companion object {
        private const val PREFS_NAME = "com.duckduckgo.contentscopescripts.browseruilock.store"
        private const val KEY_JSON = "json"
        private const val DEFAULT_JSON = "{\"state\":\"enabled\",\"exceptions\":[]}"
    }
}
