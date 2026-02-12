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

import android.graphics.Bitmap
import android.util.Base64
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.ChatState.HIDE
import com.duckduckgo.duckchat.impl.ChatState.SHOW
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.ReportMetric
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import javax.inject.Inject

interface DuckChatJSHelper {
    suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
        mode: Mode = Mode.FULL,
        pageContext: String = "",
        tabId: String = "",
    ): JsCallbackData?

    fun onNativeAction(action: NativeAction): SubscriptionEventData
}

enum class Mode {
    FULL,
    CONTEXTUAL,
}

enum class NativeAction {
    NEW_CHAT,
    SIDEBAR,
    DUCK_AI_SETTINGS,
}

@ContributesBinding(AppScope::class)
class RealDuckChatJSHelper @Inject constructor(
    private val duckChat: DuckChatInternal,
    private val duckChatPixels: DuckChatPixels,
    private val dataStore: DuckChatDataStore,
    private val faviconManager: FaviconManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : DuckChatJSHelper {

    private val registerOpenedJob = ConflatedJob()

    override suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
        mode: Mode,
        pageContext: String,
        tabId: String,
    ): JsCallbackData? {
        fun registerDuckChatIsOpenDebounced(windowMs: Long = 500L) {
            // we debounced because METHOD_GET_AI_CHAT_NATIVE_HANDOFF_DATA can be called more than once
            // in some cases, eg. when opening duck.ai with query already
            registerOpenedJob += appCoroutineScope.launch(dispatcherProvider.io()) {
                delay(windowMs)
                duckChatPixels.reportOpen()
            }
        }

        return when (method) {
            METHOD_GET_AI_CHAT_NATIVE_HANDOFF_DATA ->
                id?.let {
                    getAIChatNativeHandoffData(featureName, method, it)
                }.also { registerDuckChatIsOpenDebounced() }

            METHOD_GET_AI_CHAT_NATIVE_CONFIG_VALUES ->
                id?.let {
                    getAIChatNativeConfigValues(featureName, method, it, mode)
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
                    ?.let { reportMetric ->
                        duckChatPixels.sendReportMetricPixel(reportMetric)
                    }
                null
            }

            METHOD_GET_PAGE_CONTEXT -> {
                id?.let {
                    val reason = data?.optString(REASON) ?: REASON_USER_ACTION
                    logcat { "Duck.ai Contextual: getAIChatPageContext reason $reason" }
                    if (pageContext.isNotEmpty()) {
                        if (reason == REASON_USER_ACTION) {
                            duckChatPixels.reportContextualPageContextManuallyAttachedFrontend()
                            getPageContextResponse(featureName, method, it, pageContext, tabId)
                        } else {
                            null
                        }
                    } else {
                        logcat { "Duck.ai Contextual: page context is empty, can't add it" }
                        null
                    }
                }
            }

            METHOD_TOGGLE_PAGE_CONTEXT -> {
                val isEnabled = data?.optBoolean(ENABLED)
                if (isEnabled != null) {
                    if (!isEnabled) {
                        duckChatPixels.reportContextualPageContextRemovedFrontend()
                    }
                }
                null
            }

            else -> {
                logcat { "Duck.ai: JS method $method" }
                null
            }
        }
    }

    override fun onNativeAction(action: NativeAction): SubscriptionEventData {
        val subscriptionName = when (action) {
            NativeAction.NEW_CHAT -> SUBSCRIPTION_NEW_CHAT
            NativeAction.SIDEBAR -> SUBSCRIPTION_TOGGLE_SIDEBAR
            NativeAction.DUCK_AI_SETTINGS -> SUBSCRIPTION_DUCK_AI_SETTINGS
        }

        return SubscriptionEventData(
            DUCK_CHAT_FEATURE_NAME,
            subscriptionName,
            JSONObject(),
        )
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
        mode: Mode,
    ): JsCallbackData {
        val jsonPayload =
            JSONObject().apply {
                put(PLATFORM, ANDROID)
                put(IS_HANDOFF_ENABLED, duckChat.isDuckChatFeatureEnabled())
                put(SUPPORTS_CLOSING_AI_CHAT, true)
                put(SUPPORTS_OPENING_SETTINGS, true)
                put(SUPPORTS_NATIVE_CHAT_INPUT, false)
                put(SUPPORTS_CHAT_ID_RESTORATION, duckChat.isDuckChatFullScreenModeEnabled())
                put(SUPPORTS_IMAGE_UPLOAD, duckChat.isImageUploadEnabled())
                put(SUPPORTS_STANDALONE_MIGRATION, duckChat.isStandaloneMigrationEnabled())
                put(SUPPORTS_CHAT_FULLSCREEN_MODE, duckChat.isDuckChatFullScreenModeEnabled() && mode == Mode.FULL)
                put(SUPPORTS_CHAT_CONTEXTUAL_MODE, duckChat.isDuckChatContextualModeEnabled() && mode == Mode.CONTEXTUAL)
                put(SUPPORTS_CHAT_SYNC, duckChat.isChatSyncFeatureEnabled())
                put(SUPPORTS_PAGE_CONTEXT, duckChat.isDuckChatContextualModeEnabled() && mode == Mode.CONTEXTUAL)
            }.also { logcat { "DuckChat-Sync: getAIChatNativeConfigValues $it" } }
        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private suspend fun getPageContextResponse(
        featureName: String,
        method: String,
        id: String,
        pageContext: String,
        tabId: String,
    ): JsCallbackData {
        val json = JSONObject(pageContext)
        val url = json.optString("url").takeIf { it.isNotBlank() }
        if (url != null) {
            val favicon = faviconManager.loadFromDisk(tabId, url)
            if (favicon != null) {
                logcat { "Duck.ai: Found favicon for tab $tabId and url $url" }
                val faviconBase64 = encodeBitmapToBase64(favicon)
                json.put(
                    "favicon",
                    JSONArray().put(
                        JSONObject().apply {
                            put("href", faviconBase64)
                            put("rel", "icon")
                        },
                    ),
                )
            }
        }

        val params =
            JSONObject().apply {
                put(
                    PAGE_CONTEXT,
                    json,
                )
            }

        return JsCallbackData(params, featureName, method, id)
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val encoded = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        return "data:image/png;base64,$encoded"
    }

    private fun getOpenKeyboardResponse(
        featureName: String,
        method: String,
        id: String,
        selector: String,
    ): JsCallbackData {
        val jsonPayload =
            JSONObject().apply {
                val jsCall = "document.getElementsByName('$selector')[0]?.focus();"
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

    companion object {
        const val DUCK_CHAT_FEATURE_NAME = "aiChat"
        private const val METHOD_GET_AI_CHAT_NATIVE_HANDOFF_DATA = "getAIChatNativeHandoffData"
        private const val METHOD_GET_AI_CHAT_NATIVE_CONFIG_VALUES = "getAIChatNativeConfigValues"
        private const val METHOD_OPEN_AI_CHAT = "openAIChat"
        const val METHOD_CLOSE_AI_CHAT = "closeAIChat"
        private const val METHOD_OPEN_AI_CHAT_SETTINGS = "openAIChatSettings"
        private const val METHOD_RESPONSE_STATE = "responseState"
        private const val METHOD_HIDE_CHAT_INPUT = "hideChatInput"
        private const val METHOD_SHOW_CHAT_INPUT = "showChatInput"
        const val METHOD_GET_PAGE_CONTEXT = "getAIChatPageContext"
        const val METHOD_OPEN_KEYBOARD = "openKeyboard"
        private const val METHOD_TOGGLE_PAGE_CONTEXT = "togglePageContextTelemetry"
        private const val AI_CHAT_PAYLOAD = "aiChatPayload"
        private const val METHOD_OPEN_KEYBOARD_PAYLOAD = "selector"
        private const val IS_HANDOFF_ENABLED = "isAIChatHandoffEnabled"
        private const val PAGE_CONTEXT = "pageContext"
        private const val SUPPORTS_CLOSING_AI_CHAT = "supportsClosingAIChat"
        private const val SUPPORTS_OPENING_SETTINGS = "supportsOpeningSettings"
        private const val SUPPORTS_NATIVE_CHAT_INPUT = "supportsNativeChatInput"
        private const val SUPPORTS_IMAGE_UPLOAD = "supportsImageUpload"
        private const val SUPPORTS_CHAT_ID_RESTORATION = "supportsURLChatIDRestoration"
        private const val SUPPORTS_STANDALONE_MIGRATION = "supportsStandaloneMigration"
        private const val SUPPORTS_CHAT_FULLSCREEN_MODE = "supportsAIChatFullMode"
        private const val SUPPORTS_CHAT_CONTEXTUAL_MODE = "supportsAIChatContextualMode"
        private const val SUPPORTS_CHAT_SYNC = "supportsAIChatSync"
        private const val SUPPORTS_PAGE_CONTEXT = "supportsPageContext"
        private const val REPORT_METRIC = "reportMetric"
        private const val PLATFORM = "platform"
        private const val ANDROID = "android"
        private const val REASON = "reason"
        private const val REASON_USER_ACTION = "userAction"
        private const val ENABLED = "enabled"
        const val SELECTOR = "selector"
        private const val DEFAULT_SELECTOR = "'user-prompt'"
        private const val SUCCESS = "success"
        private const val ERROR = "error"
        private const val SUBSCRIPTION_NEW_CHAT = "submitNewChatAction"
        private const val SUBSCRIPTION_TOGGLE_SIDEBAR = "submitToggleSidebarAction"
        private const val SUBSCRIPTION_DUCK_AI_SETTINGS = "submitOpenSettingsAction"
    }
}
