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

package com.duckduckgo.duckchat.impl.messaging.sync

import android.content.Context
import android.content.Intent
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatConstants
import com.duckduckgo.duckchat.impl.DuckChatConstants.HOST_DUCK_AI
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedIn
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SetUpSyncHandler @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val context: Context,
    private val deviceSyncState: DeviceSyncState,
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

                val responder = SyncJsResponder(jsMessaging, jsMessage, featureName)

                val setupError = validateSetupState(jsMessage.method)
                if (setupError != null) {
                    responder.sendError(setupError)
                    return
                }

                globalActivityStarter.startIntent(context, SyncActivityWithEmptyParams)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }?.let { context.startActivity(it) }
            }

            private fun validateSetupState(method: String): String? {
                if (!deviceSyncState.isFeatureEnabled()) {
                    return ERROR_SETUP_UNAVAILABLE
                }
                if (method == METHOD_SETUP_SYNC && deviceSyncState.getAccountState() is SignedIn) {
                    return ERROR_SYNC_ALREADY_ON
                }
                return null
            }

            override val allowedDomains: List<String> =
                listOf(
                    AppUrl.Url.HOST,
                    HOST_DUCK_AI,
                )

            override val featureName: String = DuckChatConstants.JS_MESSAGING_FEATURE_NAME
            override val methods: List<String> = listOf(METHOD_SYNC_SETTINGS, METHOD_SETUP_SYNC)
        }

    private companion object {
        private const val METHOD_SYNC_SETTINGS = "sendToSyncSettings"
        private const val METHOD_SETUP_SYNC = "sendToSetupSync"
        private const val ERROR_SETUP_UNAVAILABLE = "setup unavailable"
        private const val ERROR_SYNC_ALREADY_ON = "sync already on"
    }
}
