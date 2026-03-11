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

package com.duckduckgo.duckchat.internal

import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.MessageBridgeContentScopeConfigPlugin
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.MessageBridgeFeatureName.MessageBridge
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.MessageBridgeRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class, replaces = [MessageBridgeContentScopeConfigPlugin::class])
class InternalMessageBridgeContentScopeConfigPlugin @Inject constructor(
    private val messageBridgeRepository: MessageBridgeRepository,
    private val duckAiHostProvider: InternalDuckAiHostProvider,
) : ContentScopeConfigPlugin {

    override fun config(): String {
        val featureName = MessageBridge.value
        val config = overrideHost(messageBridgeRepository.messageBridgeEntity.json)
        return "\"$featureName\":$config"
    }

    override fun preferences(): String? {
        return null
    }

    private fun overrideHost(json: String): String {
        duckAiHostProvider.getCustomUrl() ?: return json
        return json.replace("\"duck.ai\"", "\"duck.ai\",\"${duckAiHostProvider.getHost()}\"")
    }
}
