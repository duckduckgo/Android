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

import android.util.Base64
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatConstants
import com.duckduckgo.duckchat.impl.DuckChatConstants.HOST_DUCK_AI
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedIn
import com.duckduckgo.sync.api.SyncCrypto
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DecryptWithSyncMasterKeyHandler @Inject constructor(
    private val crypto: SyncCrypto,
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

                val syncError = validateSyncState()
                if (syncError != null) {
                    responder.sendError(syncError).also {
                        logcat(LogPriority.WARN) { "DuckChat-Sync: $syncError" }
                    }
                    return
                }

                // get encrypted data from params
                val data = extractData(jsMessage)
                if (data == null) {
                    responder.sendError(ERROR_INVALID_PARAMETERS).also {
                        logcat(LogPriority.WARN) { "DuckChat-Sync: $ERROR_INVALID_PARAMETERS" }
                    }
                    return
                }

                // decode base64Url to get encrypted bytes
                val encryptedBytes = decodeBase64Url(data)
                if (encryptedBytes == null) {
                    responder.sendError(ERROR_INVALID_PARAMETERS).also {
                        logcat(LogPriority.WARN) { "DuckChat-Sync: $ERROR_INVALID_PARAMETERS" }
                    }
                    return
                }

                // decrypt (input is bytes, output is bytes)
                val decryptedBytes = decryptData(encryptedBytes)
                if (decryptedBytes == null) {
                    responder.sendError(ERROR_DECRYPTION_FAILED).also {
                        logcat(LogPriority.WARN) { "DuckChat-Sync: $ERROR_DECRYPTION_FAILED" }
                    }
                    return
                }

                // encode decrypted bytes as base64Url for response
                val decryptedData = Base64.encodeToString(decryptedBytes, Base64.NO_WRAP).applyUrlSafetyFromB64()

                logcat { "DuckChat-Sync: Decrypted data successfully" }

                // send decrypted data back to JS
                val payload = JSONObject().apply { put(RESPONSE_KEY_DECRYPTED_DATA, decryptedData) }
                responder.sendSuccess(payload)
            }

            private fun validateSyncState(): String? {
                if (!deviceSyncState.isFeatureEnabled()) {
                    return ERROR_SYNC_DISABLED
                }
                if (deviceSyncState.getAccountState() !is SignedIn) {
                    return ERROR_SYNC_OFF
                }
                return null
            }

            private fun extractData(jsMessage: JsMessage): String? {
                val data = jsMessage.params.optString("data", "")
                return data.takeIf { it.isNotEmpty() }
            }

            private fun decodeBase64Url(base64Url: String): ByteArray? {
                return runCatching {
                    val standardB64 = base64Url.removeUrlSafetyToRestoreB64()
                    Base64.decode(standardB64, Base64.NO_WRAP)
                }.onFailure { e ->
                    logcat(LogPriority.ERROR) { "Error decoding base64Url: $base64Url. ${e.asLog()}" }
                }.getOrNull()
            }

            private fun decryptData(data: ByteArray): ByteArray? {
                return runCatching {
                    crypto.decrypt(data)
                }.onFailure { e ->
                    logcat(LogPriority.ERROR) { "Error decrypting data because ${e.asLog()}" }
                }.getOrNull()
            }

            override val allowedDomains: List<String> = listOf(
                AppUrl.Url.HOST,
                HOST_DUCK_AI,
            )

            override val featureName: String = DuckChatConstants.JS_MESSAGING_FEATURE_NAME
            override val methods: List<String> = listOf("decryptWithSyncMasterKey")
        }

    private companion object {
        private const val RESPONSE_KEY_DECRYPTED_DATA = "decryptedData"
        private const val ERROR_SYNC_DISABLED = "sync unavailable"
        private const val ERROR_SYNC_OFF = "sync off"
        private const val ERROR_INVALID_PARAMETERS = "invalid parameters"
        private const val ERROR_DECRYPTION_FAILED = "decryption failed"
    }
}
