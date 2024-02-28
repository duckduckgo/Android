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

package com.duckduckgo.user.agent.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface UserAgentFeatureToggleStore {
    fun get(
        featureName: UserAgentFeatureName,
        defaultValue: Boolean,
    ): Boolean

    fun getMinSupportedVersion(featureName: UserAgentFeatureName): Int

    fun insert(toggle: UserAgentFeatureToggle)
    fun deleteAll()
}

class RealUserAgentFeatureToggleStore(private val context: Context) : UserAgentFeatureToggleStore {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override fun get(
        featureName: UserAgentFeatureName,
        defaultValue: Boolean,
    ): Boolean {
        return preferences.getBoolean(featureName.value, defaultValue)
    }

    override fun getMinSupportedVersion(featureName: UserAgentFeatureName): Int {
        return preferences.getInt("${featureName.value}$MIN_SUPPORTED_VERSION", 0)
    }

    override fun insert(toggle: UserAgentFeatureToggle) {
        preferences.edit {
            putBoolean(toggle.featureName, toggle.enabled)
            toggle.minSupportedVersion?.let {
                putInt("${toggle.featureName}$MIN_SUPPORTED_VERSION", it)
            }
        }
    }

    override fun deleteAll() {
        preferences.edit().clear().apply()
    }

    companion object {
        const val FILENAME = "com.duckduckgo.user.agent.store.toggles"
        const val MIN_SUPPORTED_VERSION = "MinSupportedVersion"
    }
}

data class UserAgentFeatureToggle(
    val featureName: String,
    val enabled: Boolean,
    val minSupportedVersion: Int?,
)
