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

package com.duckduckgo.sync.impl.messaging

import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedIn
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.pixels.SyncAccountOperation
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.LogPriority
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class GetScopedSyncAuthTokenHandler @Inject constructor(
    private val syncApi: SyncApi,
    private val syncStore: SyncStore,
    private val deviceSyncState: DeviceSyncState,
    private val syncPixels: SyncPixels,
) : ContentScopeJsMessageHandlersPlugin {
    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                if (jsMessage.id.isNullOrEmpty()) return

                logcat { "DuckChat-Sync: ${jsMessage.method} called" }

                if (!deviceSyncState.isFeatureEnabled()) {
                    sendErrorResponse(jsMessaging, jsMessage, "sync unavailable")
                    return
                }

                if (deviceSyncState.getAccountState() !is SignedIn) {
                    sendErrorResponse(jsMessaging, jsMessage, "sync off")
                    return
                }

                val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
                    ?: run {
                        sendErrorResponse(jsMessaging, jsMessage, "token unavailable")
                        return
                    }

                val jsonPayload = runCatching {
                    handleRescopeTokenResult(syncApi.rescopeToken(token, SCOPE))
                }.getOrElse { e ->
                    logcat(LogPriority.ERROR) { "DuckChat-Sync: exception during rescope token: ${e.message}" }
                    createErrorPayload("internal error")
                }

                sendResponse(jsMessaging, jsMessage, jsonPayload)
            }

            private fun handleRescopeTokenResult(result: Result<String>): JSONObject {
                return when (result) {
                    is Result.Success -> {
                        logcat(LogPriority.INFO) { "DuckChat-Sync: rescope token succeeded" }
                        createSuccessPayload(result.data)
                    }

                    is Result.Error -> {
                        logcat(LogPriority.ERROR) { "DuckChat-Sync: rescope token failed: code=${result.code}, reason=${result.reason}" }
                        syncPixels.fireSyncAccountErrorPixel(result, SyncAccountOperation.RESCOPE_TOKEN)
                        createErrorPayload(result.reason)
                    }
                }
            }

            private fun createSuccessPayload(token: String): JSONObject {
                return JSONObject().apply {
                    put("ok", true)
                    put(
                        "payload",
                        JSONObject().apply {
                            put("token", token)
                        },
                    )
                }
            }

            private fun createErrorPayload(reason: String): JSONObject {
                return JSONObject().apply {
                    put("ok", false)
                    put("reason", reason)
                }
            }

            private fun sendErrorResponse(
                jsMessaging: JsMessaging,
                jsMessage: JsMessage,
                error: String,
            ) {
                sendResponse(jsMessaging, jsMessage, createErrorPayload(error))
            }

            private fun sendResponse(
                jsMessaging: JsMessaging,
                jsMessage: JsMessage,
                jsonPayload: JSONObject,
            ) {
                runCatching {
                    jsMessaging.onResponse(JsCallbackData(jsonPayload, featureName, jsMessage.method, jsMessage.id!!)).also {
                        logcat { "DuckChat-Sync: responded to ${jsMessage.method} with payload" }
                    }
                }.onFailure { e ->
                    logcat(LogPriority.ERROR) { "DuckChat-Sync: failed to send response for ${jsMessage.method}: ${e.message}" }
                }
            }

            override val allowedDomains: List<String> =
                listOf(
                    AppUrl.Url.HOST,
                    HOST_DUCK_AI,
                )

            override val featureName: String = "aiChat"
            override val methods: List<String> = listOf("getScopedSyncAuthToken")
        }

    private companion object {
        private const val SCOPE = "ai_chats"
        private const val HOST_DUCK_AI = "duck.ai"
    }
}
