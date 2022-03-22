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

package com.duckduckgo.privacy.config.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.privacy.config.api.PrivacyFeatureName

interface PrivacyFeatureTogglesDataStore {
    fun get(
        featureName: PrivacyFeatureName,
        defaultValue: Boolean
    ): Boolean

    fun insert(toggle: PrivacyFeatureToggles)
    fun deleteAll()
}

class PrivacyFeatureTogglesSharedPreferences constructor(private val context: Context) :
    PrivacyFeatureTogglesDataStore {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override fun get(
        featureName: PrivacyFeatureName,
        defaultValue: Boolean
    ): Boolean {
        return preferences.getBoolean(featureName.value, defaultValue)
    }

    override fun insert(toggle: PrivacyFeatureToggles) {
        preferences.edit { putBoolean(toggle.featureName.value, toggle.enabled) }
    }

    override fun deleteAll() {
        preferences.edit().clear().apply()
    }

    companion object {
        const val FILENAME = "com.duckduckgo.privacy.config.store.toggles"
    }
}

data class PrivacyFeatureToggles(
    val featureName: PrivacyFeatureName,
    val enabled: Boolean
)
