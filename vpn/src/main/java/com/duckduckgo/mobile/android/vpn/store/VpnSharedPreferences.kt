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

package com.duckduckgo.mobile.android.vpn.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject

interface VpnStore {
    var isRunning: Boolean
}

class VpnSharedPreferences @Inject constructor(private val context: Context) : VpnStore {

    override var isRunning: Boolean
        get() = preferences.getBoolean(KEY_RUNNING, false)
        set(isRunning) = preferences.edit(true) { putBoolean(KEY_RUNNING, isRunning) }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.vpn.settings"
        const val KEY_RUNNING = "KEY_RUNNING"
    }
}
