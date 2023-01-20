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

package com.duckduckgo.autofill.store.feature

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.autofill.api.feature.AutofillFeatureName
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName

interface AutofillFeatureToggleStore {
    fun deleteAll()

    fun get(
        featureName: AutofillFeatureName,
        defaultValue: Boolean,
    ): Boolean

    fun get(
        featureName: AutofillSubfeatureName,
        defaultValue: Boolean,
    ): Boolean

    fun getMinSupportedVersion(featureName: AutofillFeatureName): Int
    fun getMinSupportedVersion(featureName: AutofillSubfeatureName): Int

    fun insert(toggle: AutofillFeatureToggles)
    fun insert(toggle: AutofillSubfeatureToggle)
}

class RealAutofillFeatureToggleStore(private val context: Context) : AutofillFeatureToggleStore {
    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override fun deleteAll() {
        preferences.edit().clear().apply()
    }

    override fun get(
        featureName: AutofillFeatureName,
        defaultValue: Boolean,
    ): Boolean = preferences.getBoolean(featureName.value, defaultValue)

    override fun get(
        featureName: AutofillSubfeatureName,
        defaultValue: Boolean,
    ): Boolean = preferences.getBoolean(featureName.value, defaultValue)

    override fun getMinSupportedVersion(featureName: AutofillFeatureName): Int = getMinSupportedVersion(featureName.value)
    override fun getMinSupportedVersion(featureName: AutofillSubfeatureName): Int = getMinSupportedVersion(featureName.value)
    private fun getMinSupportedVersion(key: String): Int = preferences.getInt("${key}$MIN_SUPPORTED_VERSION", 0)

    override fun insert(toggle: AutofillFeatureToggles) = insert(toggle.featureName.value, toggle.enabled, toggle.minSupportedVersion)
    override fun insert(toggle: AutofillSubfeatureToggle) = insert(toggle.featureName.value, toggle.enabled, toggle.minSupportedVersion)
    private fun insert(
        key: String,
        enabled: Boolean,
        minSupportedVersion: Int?,
    ) {
        preferences.edit {
            putBoolean(key, enabled)
            minSupportedVersion?.let {
                putInt("${key}$MIN_SUPPORTED_VERSION", it)
            }
        }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.autofill.store.toggles"
        const val MIN_SUPPORTED_VERSION = "MinSupportedVersion"
    }
}

data class AutofillFeatureToggles(
    val featureName: AutofillFeatureName,
    val enabled: Boolean,
    val minSupportedVersion: Int?,
)

data class AutofillSubfeatureToggle(
    val featureName: AutofillSubfeatureName,
    val enabled: Boolean,
    val minSupportedVersion: Int?,
)
