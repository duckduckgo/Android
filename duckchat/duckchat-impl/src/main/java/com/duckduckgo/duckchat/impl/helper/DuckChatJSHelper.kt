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

package com.duckduckgo.duckchat.impl.helper

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

interface DuckChatJSHelper {
    suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ): JsCallbackData?
}

@ContributesBinding(AppScope::class)
class RealDuckChatJSHelper @Inject constructor(
    private val duckChat: DuckChatInternal,
    private val dataStore: DuckChatDataStore,
) : DuckChatJSHelper {

    override suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ): JsCallbackData? = when (method) {
        METHOD_GET_AI_CHAT_NATIVE_HANDOFF_DATA -> id?.let {
            getAIChatNativeHandoffData(featureName, method, it)
        }
        METHOD_GET_AI_CHAT_NATIVE_CONFIG_VALUES -> id?.let {
            getAIChatNativeConfigValues(featureName, method, it)
        }
        METHOD_OPEN_AI_CHAT -> {
            val payload = extractPayload(data)
            dataStore.updateUserPreferences(payload)
            duckChat.openDuckChat()
            null
        }
        METHOD_CLOSE_AI_CHAT -> {
            duckChat.closeDuckChat()
            null
        }
        METHOD_OPEN_AI_CHAT_SETTINGS -> {
            duckChat.openDuckChatSettings()
            null
        }
        else -> null
    }

    private fun getAIChatNativeHandoffData(featureName: String, method: String, id: String): JsCallbackData {
        val jsonPayload = JSONObject().apply {
            put(PLATFORM, ANDROID)
            put(IS_HANDOFF_ENABLED, duckChat.isEnabled())
            put(PAYLOAD, runBlocking { dataStore.fetchAndClearUserPreferences() })
        }
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private fun getAIChatNativeConfigValues(featureName: String, method: String, id: String): JsCallbackData {
        val jsonPayload = JSONObject().apply {
            put(PLATFORM, ANDROID)
            put(IS_HANDOFF_ENABLED, duckChat.isEnabled())
            put(SUPPORTS_CLOSING_AI_CHAT, true)
            put(SUPPORTS_OPENING_SETTINGS, true)
        }
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private fun extractPayload(data: JSONObject?): String? {
        return data?.takeIf {
            it.opt(PAYLOAD) != JSONObject.NULL
        }?.optString(PAYLOAD)
    }

    companion object {
        const val DUCK_CHAT_FEATURE_NAME = "aiChat"
        private const val METHOD_GET_AI_CHAT_NATIVE_HANDOFF_DATA = "getAIChatNativeHandoffData"
        private const val METHOD_GET_AI_CHAT_NATIVE_CONFIG_VALUES = "getAIChatNativeConfigValues"
        private const val METHOD_OPEN_AI_CHAT = "openAIChat"
        private const val METHOD_CLOSE_AI_CHAT = "closeAIChat"
        private const val METHOD_OPEN_AI_CHAT_SETTINGS = "openAIChatSettings"
        private const val PAYLOAD = "aiChatPayload"
        private const val IS_HANDOFF_ENABLED = "isAIChatHandoffEnabled"
        private const val SUPPORTS_CLOSING_AI_CHAT = "supportsClosingAIChat"
        private const val SUPPORTS_OPENING_SETTINGS = "supportsOpeningSettings"
        private const val PLATFORM = "platform"
        private const val ANDROID = "android"
    }
}
