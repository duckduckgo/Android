/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.networkprotection.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider

interface NetworkProtectionPrefs {
    fun putString(
        key: String,
        value: String?,
    )

    fun getString(
        key: String,
        default: String?,
    ): String?

    fun putLong(
        key: String,
        value: Long,
    )

    fun getLong(
        key: String,
        default: Long,
    ): Long
}

class RealNetworkProtectionPrefs constructor(
    private val vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
) : NetworkProtectionPrefs {
    private val prefs: SharedPreferences by lazy {
        vpnSharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = true)
    }

    override fun putString(
        key: String,
        value: String?,
    ) {
        prefs.edit { putString(key, value) }
    }

    override fun getString(
        key: String,
        default: String?,
    ): String? = prefs.getString(key, default)

    companion object {
        private const val FILENAME = "com.duckduckgo.networkprotection.store.prefs.v1"
    }

    override fun putLong(
        key: String,
        value: Long,
    ) {
        prefs.edit { putLong(key, value) }
    }

    override fun getLong(
        key: String,
        default: Long,
    ): Long = prefs.getLong(key, default)
}
