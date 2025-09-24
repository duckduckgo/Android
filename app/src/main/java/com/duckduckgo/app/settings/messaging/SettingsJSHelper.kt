/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.settings.messaging

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority
import logcat.logcat
import org.json.JSONObject

interface SettingsJSHelper {
    suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ): JsCallbackData?
}

@ContributesBinding(AppScope::class)
class RealSettingsJSHelper @Inject constructor() : SettingsJSHelper {

    override suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ): JsCallbackData? = when (method) {
        METHOD_OPEN_NATIVE_SETTINGS -> {
            val returnTo = data?.optString(PARAM_RETURN)
            openNativeSettings(returnTo)
            null
        }
        else -> null
    }

    private fun openNativeSettings(
        returnTo: String?,
    ) {
        when (returnTo) {
            ID_AI_FEATURES_SETTINGS -> {
                logcat { "Return: $returnTo" }
            }
            ID_PRIVATE_SEARCH_SETTINGS -> {
                logcat { "Return: $returnTo" }
            }
            else -> {
                logcat(LogPriority.WARN) { "Unknown settings return value: $returnTo" }
            }
        }
    }

    companion object {
        const val SERP_SETTINGS_FEATURE_NAME = "serpSettings"
        const val METHOD_OPEN_NATIVE_SETTINGS = "openNativeSettings"
        private const val ID_AI_FEATURES_SETTINGS = "aiFeatures"
        private const val ID_PRIVATE_SEARCH_SETTINGS = "privateSearch"
        private const val PARAM_RETURN = "return"
    }
}
