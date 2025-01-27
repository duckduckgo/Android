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

package com.duckduckgo.app.browser.duckchat

import com.duckduckgo.app.browser.commands.Command
import com.duckduckgo.app.browser.commands.Command.SendResponseToJs
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import org.json.JSONObject

interface DuckChatJSHelper {
    suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ): Command?
}

@ContributesBinding(AppScope::class)
class RealDuckChatJSHelper @Inject constructor(
    private val duckChat: DuckChat,
    private val preferencesStore: DuckChatPreferencesStore,
) : DuckChatJSHelper {

    override suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ): Command? = when (method) {
        METHOD_GET_AI_CHAT_NATIVE_HANDOFF_DATA -> id?.let {
            SendResponseToJs(getAIChatNativeHandoffData(featureName, method, it))
        }
        METHOD_GET_AI_CHAT_NATIVE_CONFIG_VALUES -> id?.let {
            SendResponseToJs(getAIChatNativeConfigValues(featureName, method, it))
        }
        METHOD_OPEN_AI_CHAT -> {
            val payload = extractPayload(data)
            preferencesStore.updateUserPreferences(payload)
            duckChat.openDuckChat()
            null
        }
        else -> null
    }

    private fun getAIChatNativeHandoffData(featureName: String, method: String, id: String): JsCallbackData {
        val jsonPayload = JSONObject().apply {
            put(PLATFORM, ANDROID)
            put(IS_HANDOFF_ENABLED, duckChat.isEnabled())
            put(PAYLOAD, preferencesStore.fetchAndClearUserPreferences())
        }
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private fun getAIChatNativeConfigValues(featureName: String, method: String, id: String): JsCallbackData {
        val jsonPayload = JSONObject().apply {
            put(PLATFORM, ANDROID)
            put(IS_HANDOFF_ENABLED, duckChat.isEnabled())
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
        private const val PAYLOAD = "aiChatPayload"
        private const val IS_HANDOFF_ENABLED = "isAIChatHandoffEnabled"
        private const val PLATFORM = "platform"
        private const val ANDROID = "android"
    }
}
