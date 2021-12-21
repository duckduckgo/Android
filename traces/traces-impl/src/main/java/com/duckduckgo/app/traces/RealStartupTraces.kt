/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.traces

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.traces.api.StartupTraces
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealStartupTraces @Inject constructor(private val context: Context) : StartupTraces {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override var isTraceEnabled: Boolean
        get() = preferences.getBoolean(ENABLE_KEY, false)
        set(value) {
            preferences.edit(true) { putBoolean(ENABLE_KEY, value) }
        }

    companion object {
        private const val FILENAME = "com.duckduckgo.app.traces.preference"
        private const val ENABLE_KEY = "com.duckduckgo.app.traces.preference.enable"
    }
}
