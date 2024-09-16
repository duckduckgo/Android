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

package com.duckduckgo.sync.settings.impl

import android.content.*
import androidx.core.content.*
import com.duckduckgo.di.scopes.*
import com.squareup.anvil.annotations.*
import dagger.*
import javax.inject.*

interface SettingsSyncStore {
    var serverModifiedSince: String
    var startTimeStamp: String
    var clientModifiedSince: String
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSettingsSyncStore @Inject constructor(private val context: Context) : SettingsSyncStore {
    override var serverModifiedSince: String
        get() = preferences.getString(KEY_MODIFIED_SINCE, "0") ?: "0"
        set(value) = preferences.edit(true) { putString(KEY_MODIFIED_SINCE, value) }
    override var startTimeStamp: String
        get() = preferences.getString(KEY_START_TIMESTAMP, "0") ?: "0"
        set(value) = preferences.edit(true) { putString(KEY_START_TIMESTAMP, value) }
    override var clientModifiedSince: String
        get() = preferences.getString(KEY_CLIENT_MODIFIED_SINCE, "0") ?: "0"
        set(value) = preferences.edit(true) { putString(KEY_CLIENT_MODIFIED_SINCE, value) }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.settings.sync"
        private const val KEY_MODIFIED_SINCE = "KEY_MODIFIED_SINCE"
        private const val KEY_START_TIMESTAMP = "KEY_START_TIMESTAMP"
        private const val KEY_CLIENT_MODIFIED_SINCE = "KEY_CLIENT_MODIFIED_SINCE"
    }
}
