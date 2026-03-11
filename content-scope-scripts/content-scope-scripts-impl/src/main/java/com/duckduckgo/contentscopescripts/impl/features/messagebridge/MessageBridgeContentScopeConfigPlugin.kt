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

package com.duckduckgo.contentscopescripts.impl.features.messagebridge

import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.MessageBridgeFeatureName.MessageBridge
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.MessageBridgeRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

@ContributesMultibinding(AppScope::class)
class MessageBridgeContentScopeConfigPlugin @Inject constructor(
    private val messageBridgeRepository: MessageBridgeRepository,
    private val duckAiHostProvider: DuckAiHostProvider,
) : ContentScopeConfigPlugin {

    override fun config(): String {
        val featureName = MessageBridge.value
        val config = messageBridgeRepository.messageBridgeEntity.json
        val modifiedConfig = addOverrideHost(config)
        return "\"$featureName\":$modifiedConfig"
    }

    override fun preferences(): String? {
        return null
    }

    private fun addOverrideHost(json: String): String {
        val host = duckAiHostProvider.getCustomHost() ?: return json
        return runCatching {
            val parsedJson = JSONTokener(json).nextValue()
            when (parsedJson) {
                is JSONObject -> {
                    addHostToArraysContainingDuckAi(parsedJson, host)
                    parsedJson.toString()
                }
                is JSONArray -> {
                    addHostToArraysContainingDuckAi(parsedJson, host)
                    parsedJson.toString()
                }
                else -> json
            }
        }.getOrElse { json }
    }

    private fun addHostToArraysContainingDuckAi(jsonObject: JSONObject, host: String) {
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = jsonObject.opt(key)) {
                is JSONObject -> addHostToArraysContainingDuckAi(value, host)
                is JSONArray -> addHostToArraysContainingDuckAi(value, host)
            }
        }
    }

    private fun addHostToArraysContainingDuckAi(jsonArray: JSONArray, host: String) {
        var hasDuckAi = false
        var hasHost = false
        for (index in 0 until jsonArray.length()) {
            when (val value = jsonArray.opt(index)) {
                is JSONObject -> addHostToArraysContainingDuckAi(value, host)
                is JSONArray -> addHostToArraysContainingDuckAi(value, host)
                is String -> {
                    if (value == DEFAULT_DUCK_AI_HOST) hasDuckAi = true
                    if (value == host) hasHost = true
                }
            }
        }

        if (hasDuckAi && !hasHost) {
            jsonArray.put(host)
        }
    }

    private companion object {
        const val DEFAULT_DUCK_AI_HOST = "duck.ai"
    }
}
