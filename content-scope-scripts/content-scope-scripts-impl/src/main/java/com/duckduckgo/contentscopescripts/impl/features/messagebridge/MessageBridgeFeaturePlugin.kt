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

import com.duckduckgo.contentscopescripts.impl.features.messagebridge.MessageBridgeFeatureName.MessageBridge
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.MessageBridgeEntity
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.MessageBridgeRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class MessageBridgeFeaturePlugin @Inject constructor(
    private val messageBridgeRepository: MessageBridgeRepository,
) : PrivacyFeaturePlugin {

    override fun store(featureName: String, jsonString: String): Boolean {
        val messageBridgeFeatureName = messageBridgeFeatureValueOf(featureName) ?: return false
        if (messageBridgeFeatureName.value == this.featureName) {
            val entity = MessageBridgeEntity(json = jsonString)
            messageBridgeRepository.updateAll(messageBridgeEntity = entity)
            return true
        }
        return false
    }

    override val featureName: String = MessageBridge.value
}
