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
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.LogPriority
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class GetSyncStatusHandler @Inject constructor(
    private val deviceSyncState: DeviceSyncState,
    private val syncStore: SyncStore,
) : ContentScopeJsMessageHandlersPlugin {
    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                if (jsMessage.id.isNullOrEmpty()) return

                logcat(LogPriority.WARN) { "DuckChat-Sync: ${jsMessage.method} called" }

                val jsonPayload = runCatching {
                    val syncAvailable = deviceSyncState.isFeatureEnabled()
                    val signedIn = syncStore.isSignedIn()

                    val payload = JSONObject().apply {
                        put("syncAvailable", syncAvailable)
                        put("userId", if (signedIn) (syncStore.userId ?: JSONObject.NULL) else JSONObject.NULL)
                        put("deviceId", if (signedIn) (syncStore.deviceId ?: JSONObject.NULL) else JSONObject.NULL)
                        put("deviceName", if (signedIn) (syncStore.deviceName ?: JSONObject.NULL) else JSONObject.NULL)
                        put("deviceType", if (signedIn) "mobile" else JSONObject.NULL)
                    }

                    JSONObject().apply {
                        put("ok", true)
                        put("payload", payload)
                    }
                }.getOrElse { e ->
                    logcat(LogPriority.ERROR) { "DuckChat-Sync: exception getting sync status: ${e.message}" }
                    JSONObject().apply {
                        put("ok", false)
                        put("reason", "internal error")
                    }
                }

                jsMessaging.onResponse(JsCallbackData(jsonPayload, featureName, jsMessage.method, jsMessage.id!!)).also {
                    logcat { "DuckChat-Sync: responded to ${jsMessage.method} with $jsonPayload" }
                }
            }

            override val allowedDomains: List<String> =
                listOf(
                    AppUrl.Url.HOST,
                    HOST_DUCK_AI,
                )

            override val featureName: String = "aiChat"
            override val methods: List<String> = listOf("getSyncStatus")
        }

    private companion object {
        private const val HOST_DUCK_AI = "duck.ai"
    }
}
