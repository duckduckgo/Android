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
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.ChatState.HIDE
import com.duckduckgo.duckchat.impl.ChatState.SHOW
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.ReportMetric
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.regex.Pattern
import javax.inject.Inject

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
    private val duckChatPixels: DuckChatPixels,
    private val dataStore: DuckChatDataStore,
) : DuckChatJSHelper {
    private val migrationItems = mutableListOf<String>()
    override suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ): JsCallbackData? =
        when (method) {
            METHOD_GET_AI_CHAT_NATIVE_HANDOFF_DATA ->
                id?.let {
                    getAIChatNativeHandoffData(featureName, method, it)
                }

            METHOD_GET_AI_CHAT_NATIVE_CONFIG_VALUES ->
                id?.let {
                    getAIChatNativeConfigValues(featureName, method, it)
                }

            METHOD_OPEN_AI_CHAT -> {
                val payload = extractPayload(data)
                dataStore.updateUserPreferences(payload)
                duckChat.openNewDuckChatSession()
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

            METHOD_RESPONSE_STATE -> {
                ChatState
                    .fromValue(data?.optString("status"))
                    ?.let { status -> duckChat.updateChatState(status) }
                null
            }

            METHOD_HIDE_CHAT_INPUT -> {
                duckChat.updateChatState(HIDE)
                null
            }

            METHOD_SHOW_CHAT_INPUT -> {
                duckChat.updateChatState(SHOW)
                null
            }

            METHOD_OPEN_KEYBOARD ->
                id?.let {
                    val selector = extractOpenKeyboardSelector(data) ?: DEFAULT_SELECTOR
                    getOpenKeyboardResponse(featureName, method, it, selector)
                }

            REPORT_METRIC -> {
                ReportMetric
                    .fromValue(data?.optString("metricName"))
                    ?.let { reportMetric -> duckChatPixels.sendReportMetricPixel(reportMetric) }
                null
            }

            METHOD_STORE_MIGRATION_DATA -> id?.let {
                getStoreMigrationDataResponse(featureName, method, it, data)
            }

            METHOD_GET_MIGRATION_INFO -> id?.let {
                getMigrationInfoResponse(featureName, method, it)
            }

            METHOD_GET_MIGRATION_DATA_BY_INDEX -> id?.let {
                getMigrationDataByIndexResponse(featureName, method, it, data)
            }

            METHOD_CLEAR_MIGRATION_DATA -> id?.let {
                getClearMigrationDataResponse(featureName, method, it)
            }

            else -> null
        }

    private fun getAIChatNativeHandoffData(
        featureName: String,
        method: String,
        id: String,
    ): JsCallbackData {
        val jsonPayload =
            JSONObject().apply {
                put(PLATFORM, ANDROID)
                put(IS_HANDOFF_ENABLED, duckChat.isDuckChatFeatureEnabled())
                put(AI_CHAT_PAYLOAD, runBlocking { dataStore.fetchAndClearUserPreferences() })
            }
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private fun getAIChatNativeConfigValues(
        featureName: String,
        method: String,
        id: String,
    ): JsCallbackData {
        val jsonPayload =
            JSONObject().apply {
                put(PLATFORM, ANDROID)
                put(IS_HANDOFF_ENABLED, duckChat.isDuckChatFeatureEnabled())
                put(SUPPORTS_CLOSING_AI_CHAT, true)
                put(SUPPORTS_OPENING_SETTINGS, true)
                put(SUPPORTS_NATIVE_CHAT_INPUT, false)
                put(SUPPORTS_IMAGE_UPLOAD, duckChat.isImageUploadEnabled())
                put(SUPPORTS_STANDALONE_MIGRATION, duckChat.isStandaloneMigrationSupported())
            }
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private fun getOpenKeyboardResponse(
        featureName: String,
        method: String,
        id: String,
        selector: String,
    ): JsCallbackData {
        val jsonPayload =
            JSONObject().apply {
                val jsCall = "document.getElementsByName($selector)[0]?.focus();"
                put(SELECTOR, jsCall)
                put(SUCCESS, true)
                put(ERROR, "")
            }
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private fun extractPayload(data: JSONObject?): String? =
        data
            ?.takeIf {
                it.opt(AI_CHAT_PAYLOAD) != JSONObject.NULL
            }?.optString(AI_CHAT_PAYLOAD)

    private fun extractOpenKeyboardSelector(data: JSONObject?): String? {
        val fullSelector =
            data
                ?.takeIf {
                    it.opt(METHOD_OPEN_KEYBOARD_PAYLOAD) != JSONObject.NULL
                }?.optString(METHOD_OPEN_KEYBOARD_PAYLOAD)
        return fullSelector?.let {
            val pattern = Pattern.compile("""\[name="([^"]*)"\]""")
            val matcher = pattern.matcher(it)
            if (matcher.find()) {
                matcher.group(1)
            } else {
                null
            }
        }
    }

    private fun getStoreMigrationDataResponse(
        featureName: String,
        method: String,
        id: String,
        data: JSONObject?,
    ): JsCallbackData {
        val item = data?.optString(SERIALIZED_MIGRATION_FILE)
        if (item != null && item != JSONObject.NULL) {
            synchronized(migrationItems) { migrationItems.add(item) }
        }
        val jsonPayload = JSONObject()
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private fun getMigrationInfoResponse(
        featureName: String,
        method: String,
        id: String,
    ): JsCallbackData {
        val count = synchronized(migrationItems) { migrationItems.size }
        val jsonPayload = JSONObject().apply { put(COUNT, count) }
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private fun getMigrationDataByIndexResponse(
        featureName: String,
        method: String,
        id: String,
        data: JSONObject?,
    ): JsCallbackData {
        val index = data?.optInt(INDEX, -1) ?: -1
        val value = synchronized(migrationItems) {
            if (index in 0 until migrationItems.size) migrationItems[index] else null
        }
        val jsonPayload = JSONObject().apply {
            if (value != null) put(SERIALIZED_MIGRATION_FILE, value)
        }
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private fun getClearMigrationDataResponse(
        featureName: String,
        method: String,
        id: String,
    ): JsCallbackData {
        synchronized(migrationItems) { migrationItems.clear() }
        val jsonPayload = JSONObject()
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    companion object {
        const val DUCK_CHAT_FEATURE_NAME = "aiChat"
        private const val METHOD_GET_AI_CHAT_NATIVE_HANDOFF_DATA = "getAIChatNativeHandoffData"
        private const val METHOD_GET_AI_CHAT_NATIVE_CONFIG_VALUES = "getAIChatNativeConfigValues"
        private const val METHOD_OPEN_AI_CHAT = "openAIChat"
        private const val METHOD_CLOSE_AI_CHAT = "closeAIChat"
        private const val METHOD_OPEN_AI_CHAT_SETTINGS = "openAIChatSettings"
        private const val METHOD_RESPONSE_STATE = "responseState"
        private const val METHOD_HIDE_CHAT_INPUT = "hideChatInput"
        private const val METHOD_SHOW_CHAT_INPUT = "showChatInput"
        const val METHOD_OPEN_KEYBOARD = "openKeyboard"
        private const val AI_CHAT_PAYLOAD = "aiChatPayload"
        private const val METHOD_OPEN_KEYBOARD_PAYLOAD = "selector"
        private const val IS_HANDOFF_ENABLED = "isAIChatHandoffEnabled"
        private const val SUPPORTS_CLOSING_AI_CHAT = "supportsClosingAIChat"
        private const val SUPPORTS_OPENING_SETTINGS = "supportsOpeningSettings"
        private const val SUPPORTS_NATIVE_CHAT_INPUT = "supportsNativeChatInput"
        private const val SUPPORTS_IMAGE_UPLOAD = "supportsImageUpload"
        private const val SUPPORTS_STANDALONE_MIGRATION = "supportsStandaloneMigration"
        private const val REPORT_METRIC = "reportMetric"
        private const val PLATFORM = "platform"
        private const val ANDROID = "android"
        const val SELECTOR = "selector"
        private const val DEFAULT_SELECTOR = "'user-prompt'"
        private const val SUCCESS = "success"
        private const val ERROR = "error"

        // Migration messaging constants
        private const val METHOD_STORE_MIGRATION_DATA = "storeMigrationData"
        private const val METHOD_GET_MIGRATION_INFO = "getMigrationInfo"
        private const val METHOD_GET_MIGRATION_DATA_BY_INDEX = "getMigrationDataByIndex"
        private const val METHOD_CLEAR_MIGRATION_DATA = "clearMigrationData"

        private const val SERIALIZED_MIGRATION_FILE = "serializedMigrationFile"
        private const val COUNT = "count"
        private const val INDEX = "index"
    }
}
