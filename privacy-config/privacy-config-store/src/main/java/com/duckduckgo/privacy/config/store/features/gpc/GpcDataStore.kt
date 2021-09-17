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

package com.duckduckgo.privacy.config.store.features.gpc

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface GpcDataStore {

    var gpcEnabled: Boolean
}

class GpcSharedPreferences constructor(private val context: Context) : GpcDataStore {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override var gpcEnabled: Boolean
        get() = preferences.getBoolean(KEY_GPC_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_GPC_ENABLED, enabled) }

    companion object {
        const val FILENAME = "com.duckduckgo.privacy.config.impl.features.gpc"
        const val KEY_GPC_ENABLED = "KEY_GPC_ENABLED"
    }
}
