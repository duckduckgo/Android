/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.remotemessage.impl

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.remotemessage.impl.matchingattributes.MatchingAttribute
import com.duckduckgo.app.remotemessage.impl.matchingattributes.parse
import com.duckduckgo.app.remotemessage.impl.messages.Content
import com.duckduckgo.app.remotemessage.impl.messages.RemoteMessage
import com.duckduckgo.app.remotemessage.store.RemoteMessagingConfig
import com.duckduckgo.app.remotemessage.store.RemoteMessagingConfigRepository
import okhttp3.internal.toImmutableList
import org.json.JSONObject
import timber.log.Timber
import java.lang.IllegalArgumentException

interface RemoteMessagingConfigProcessor {
    suspend fun process(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig)
}

class RealRemoteMessagingConfigProcessor(
    private val messagesPluginPoint: PluginPoint<MessagePlugin>,
    private val matchingAttributesPluginPoint: PluginPoint<MatchingAttributePlugin>,
    private val remoteMessagingConfigRepository: RemoteMessagingConfigRepository
) : RemoteMessagingConfigProcessor {

    override suspend fun process(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig) {
        Timber.i("RMF: process ${jsonRemoteMessagingConfig.version}")
        val currentVersion = remoteMessagingConfigRepository.get().version
        val newVersion = jsonRemoteMessagingConfig.version

        // if (currentVersion != newVersion) {
        if (true) {
            val messages = parseRemoteMessages(jsonRemoteMessagingConfig.messages)
            Timber.i("RMF: messages parsed $messages")
            val matchingRules = parseMatchingRules(jsonRemoteMessagingConfig.matchingRules)
            remoteMessagingConfigRepository.insert(RemoteMessagingConfig(version = jsonRemoteMessagingConfig.version))
        } else {
            Timber.i("RMF: skip, same version")
        }
    }

    private fun parseRemoteMessages(jsonMessages: List<Message>) = jsonMessages.map { it.map() }

    private fun parseMatchingRules(
        jsonMatchingRules: List<MatchingRule>
    ): Map<Int, List<MatchingAttribute?>> {
        val matchingRules = mutableMapOf<Int, List<MatchingAttribute?>>()
        jsonMatchingRules.forEach {
            Timber.i("RMF: MatchingRule ${it.id}")
            matchingRules[it.id] = it.attributes.map { (key, jsonObject) ->
                Timber.i("RMF: MatchingRule $key")
                matchingAttributesPluginPoint.getPlugins().forEach { plugin ->
                    val rule = plugin.parse(key, jsonObject.toString())
                    if (rule != null) return@map rule
                }
                return@map parse<MatchingAttribute.Unknown>(jsonObject.toString())
            }.toImmutableList()
        }
        return matchingRules
    }

    private fun Message.map(): RemoteMessage? {
        return runCatching {
            RemoteMessage(
                id = this.id,
                messageType = this.messageType,
                content = this.content?.mapToContent(this.messageType) ?: throw IllegalArgumentException(),
                matchingRules = this.matchingRules.orEmpty(),
                exclusionRules = this.exclusionRules.orEmpty()
            )
        }.onFailure {
            Timber.i("RMF: error $it")
        }.getOrNull()
    }

    private fun JSONObject.mapToContent(messageType: String): Content? {
        messagesPluginPoint.getPlugins().forEach { plugin ->
            val content = plugin.parse(messageType, this.toString())
            if (content != null) return content
        }
        return null
    }
}
