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

package com.duckduckgo.request.filterer.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.request.filterer.api.RequestFiltererFeatureName

interface RequestFiltererFeatureToggleStore {
    fun deleteAll()

    fun get(
        featureName: RequestFiltererFeatureName,
        defaultValue: Boolean,
    ): Boolean

    fun getMinSupportedVersion(featureName: RequestFiltererFeatureName): Int

    fun insert(toggle: RequestFiltererFeatureToggles)
}

class RealRequestFiltererFeatureToggleStore(private val context: Context) : RequestFiltererFeatureToggleStore {
    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override fun deleteAll() {
        preferences.edit().clear().apply()
    }

    override fun get(featureName: RequestFiltererFeatureName, defaultValue: Boolean): Boolean {
        return preferences.getBoolean(featureName.value, defaultValue)
    }

    override fun getMinSupportedVersion(featureName: RequestFiltererFeatureName): Int {
        return preferences.getInt("${featureName.value}$MIN_SUPPORTED_VERSION", 0)
    }

    override fun insert(toggle: RequestFiltererFeatureToggles) {
        preferences.edit {
            putBoolean(toggle.featureName.value, toggle.enabled)
            toggle.minSupportedVersion?.let {
                putInt("${toggle.featureName.value}$MIN_SUPPORTED_VERSION", it)
            }
        }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.request.filterer.store.toggles"
        const val MIN_SUPPORTED_VERSION = "MinSupportedVersion"
    }
}

data class RequestFiltererFeatureToggles(
    val featureName: RequestFiltererFeatureName,
    val enabled: Boolean,
    val minSupportedVersion: Int?,
)
