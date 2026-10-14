/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.internalfeedback

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

/**
 * Answers the internal feedback form's requests for device info and attachments.
 *
 * Not gated on internal-user state: the form is behind SSO and the bridge is only enabled for its host.
 */
@ContributesMultibinding(AppScope::class)
class InternalFeedbackContentScopeJsMessageHandler @Inject constructor(
    private val deviceInfoProvider: InternalFeedbackDeviceInfoProvider,
    private val screenshotStore: InternalFeedbackScreenshotStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                val id = jsMessage.id ?: return
                appCoroutineScope.launch {
                    val params = when (jsMessage.method) {
                        GET_DEVICE_INFO_METHOD -> getDeviceInfo()
                        GET_ATTACHMENTS_METHOD -> getAttachments()
                        else -> return@launch
                    }
                    jsMessaging.onResponse(
                        JsCallbackData(
                            params = params,
                            featureName = jsMessage.featureName,
                            method = jsMessage.method,
                            id = id,
                        ),
                    )
                }
            }

            override val allowedDomains: List<String> = listOf(INTERNAL_FEEDBACK_HOST)
            override val featureName: String = FEATURE_NAME
            override val methods: List<String> = listOf(GET_DEVICE_INFO_METHOD, GET_ATTACHMENTS_METHOD)
        }

    // An empty reply still resolves the page's request; it then shows the device info as missing instead of waiting.
    private suspend fun getDeviceInfo(): JSONObject =
        deviceInfoProvider.getDeviceInfo()
            .onFailure { logcat(WARN) { "Internal feedback: failed to get device info: ${it.asLog()}" } }
            .getOrDefault(JSONObject())

    // An empty object tells the form there's no screenshot, e.g. when it was opened directly instead of from the browser menu.
    private suspend fun getAttachments(): JSONObject {
        val screenshot = try {
            screenshotStore.takeEncodedScreenshot()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logcat(WARN) { "Internal feedback: failed to get screenshot: ${e.asLog()}" }
            null
        } ?: return JSONObject()
        return JSONObject().put(
            "screenshot",
            JSONObject()
                .put("base64", screenshot)
                .put("mimeType", "image/png"),
        )
    }

    companion object {
        private const val INTERNAL_FEEDBACK_HOST = "internalapps.duckduckgo.com"
        private const val FEATURE_NAME = "internalFeedback"
        private const val GET_DEVICE_INFO_METHOD = "getDeviceInfo"
        private const val GET_ATTACHMENTS_METHOD = "getAttachments"
    }
}
