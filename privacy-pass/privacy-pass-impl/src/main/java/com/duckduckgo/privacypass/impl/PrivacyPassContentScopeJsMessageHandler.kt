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

package com.duckduckgo.privacypass.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.privacypass.api.PrivacyPassManager
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class PrivacyPassContentScopeJsMessageHandler @Inject constructor(
    private val privacyPassManager: PrivacyPassManager,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {

        override fun process(
            jsMessage: JsMessage,
            jsMessaging: JsMessaging,
            jsMessageCallback: JsMessageCallback?,
        ) {
            appScope.launch(dispatcherProvider.io()) {
                try {
                    val result = when (jsMessage.method) {
                        METHOD_ISSUE -> handleIssue(jsMessage.params)
                        METHOD_SPEND -> handleSpend(jsMessage.params)
                        METHOD_BALANCE -> handleBalance(jsMessage.params)
                        METHOD_REDEEM -> handleRedeem(jsMessage.params)
                        else -> null
                    }

                    if (result != null) {
                        jsMessage.id?.let { id ->
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    params = result,
                                    featureName = jsMessage.featureName,
                                    method = jsMessage.method,
                                    id = id,
                                ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    logcat(ERROR) { "PrivacyPass: error handling ${jsMessage.method}: ${e.message}" }
                    jsMessage.id?.let { id ->
                        jsMessaging.onResponse(
                            JsCallbackData(
                                params = JSONObject().put("error", e.message ?: "Unknown error"),
                                featureName = jsMessage.featureName,
                                method = jsMessage.method,
                                id = id,
                            ),
                        )
                    }
                }
            }
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = FEATURE_NAME
        override val methods: List<String> = listOf(METHOD_ISSUE, METHOD_SPEND, METHOD_BALANCE, METHOD_REDEEM)
    }

    private suspend fun handleIssue(params: JSONObject): JSONObject {
        val issuer = params.optString("issuer", "")
        val credits = params.optInt("credits", 0)
        val credential = privacyPassManager.issueCredential(issuer, credits)
        return JSONObject().apply {
            put("credentialId", credential.credentialId)
            put("credits", credential.credits)
        }
    }

    private suspend fun handleSpend(params: JSONObject): JSONObject {
        val credentialId = params.optString("credentialId", "")
        val amount = params.optInt("amount", 0)
        val result = privacyPassManager.spendCredits(credentialId, amount)
        return JSONObject().apply {
            put("credentialId", result.credentialId)
            put("remainingCredits", result.remainingCredits)
            put("token", result.token)
        }
    }

    private suspend fun handleBalance(params: JSONObject): JSONObject {
        val credentialId = params.optString("credentialId", "")
        val result = privacyPassManager.balance(credentialId)
        return JSONObject().apply {
            put("credentialId", result.credentialId)
            put("credits", result.credits)
        }
    }

    private suspend fun handleRedeem(params: JSONObject): JSONObject {
        val token = params.optString("token", "")
        val result = privacyPassManager.redeemToken(token)
        return JSONObject().apply {
            put("success", result.success)
            put("message", result.message)
        }
    }

    companion object {
        private const val FEATURE_NAME = "privacyPass"
        private const val METHOD_ISSUE = "issue"
        private const val METHOD_SPEND = "spend"
        private const val METHOD_BALANCE = "balance"
        private const val METHOD_REDEEM = "redeem"
    }
}
