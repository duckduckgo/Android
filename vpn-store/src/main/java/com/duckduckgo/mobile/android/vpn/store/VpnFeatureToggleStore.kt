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

package com.duckduckgo.mobile.android.vpn.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface VpnFeatureToggleStore {
    fun deleteAll()

    fun get(
        featureName: String,
        defaultValue: Boolean
    ): Boolean?

    fun insert(toggle: VpnFeatureToggles)
}

class RealVpnFeatureToggleStore(private val context: Context) : VpnFeatureToggleStore {
    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_MULTI_PROCESS)

    override fun deleteAll() {
        preferences.edit().clear().apply()
    }

    override fun get(featureName: String, defaultValue: Boolean): Boolean? {
        return if (preferences.contains(featureName)) {
            preferences.getBoolean(featureName, defaultValue)
        } else {
            null
        }
    }

    override fun insert(toggle: VpnFeatureToggles) {
        preferences.edit { putBoolean(toggle.featureName, toggle.enabled) }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.vpn.atp.config.store.toggles"
    }
}

data class VpnFeatureToggles(
    val featureName: String,
    val enabled: Boolean,
)
