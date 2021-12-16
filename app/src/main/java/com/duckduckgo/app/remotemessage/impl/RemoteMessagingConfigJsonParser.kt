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
import com.duckduckgo.app.remotemessage.impl.messages.RemoteConfig
import com.duckduckgo.app.remotemessage.impl.messages.RemoteMessage
import org.json.JSONObject
import timber.log.Timber

class RemoteMessagingConfigJsonParser(
    private val messagesPluginPoint: PluginPoint<MessagePlugin>,
    private val matchingAttributesPluginPoint: PluginPoint<MatchingAttributePlugin>
) {

    fun map(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig): RemoteConfig {
        val messages = parseRemoteMessages(jsonRemoteMessagingConfig.messages)
        Timber.i("RMF: messages parsed $messages")
        val matchingRules = parseMatchingRules(jsonRemoteMessagingConfig.matchingRules)
        return RemoteConfig(
            messages = messages,
            rules = matchingRules
        )
    }

    private fun parseRemoteMessages(jsonRemoteMessages: List<JsonRemoteMessage>) = jsonRemoteMessages.mapNotNull { it.map() }

    private fun JsonRemoteMessage.map(): RemoteMessage? {
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
            if (content != null) {
               return when (content) {
                    is JsonMessageContent.Small -> {
                        Content.Small(
                            titleText = content.titleText,
                            descriptionText = content.descriptionText
                        )
                    }
                    is JsonMessageContent.BigSingleAction -> {
                        Content.BigSingleAction(
                            titleText = content.titleText,
                            descriptionText = content.descriptionText,
                            placeholder = content.placeholder,
                            primaryActionText = content.primaryActionText,
                            primaryAction = content.primaryAction
                        )
                    }
                    is JsonMessageContent.BigTwoActions -> {
                        Content.BigTwoActions(
                            titleText = content.titleText,
                            descriptionText = content.descriptionText,
                            placeholder = content.placeholder,
                            primaryActionText = content.primaryActionText,
                            primaryAction = content.primaryAction,
                            secondaryActionText = content.secondaryActionText,
                            secondaryAction = content.secondaryAction
                        )
                    }
                    is JsonMessageContent.Medium -> {
                        Content.Medium(
                            titleText = content.titleText,
                            descriptionText = content.descriptionText,
                            placeholder = content.placeholder
                        )
                    }
                }
            }
        }
        return null
    }

    private fun parseMatchingRules(
        jsonMatchingRules: List<JsonMatchingRule>
    ): Map<Int, List<MatchingAttribute?>> {
        val matchingRules = mutableMapOf<Int, List<MatchingAttribute?>>()
        jsonMatchingRules.forEach {
            Timber.i("RMF: MatchingRule ${it.id}")
            matchingRules[it.id] = it.attributes.mapNotNull { (key, jsonObject) ->
                Timber.i("RMF: MatchingRule $key")
                matchingAttributesPluginPoint.getPlugins().forEach { plugin ->
                    val rule = plugin.parse(key, jsonObject.toString())
                    if (rule != null) return@mapNotNull rule
                }
                return@mapNotNull parse<MatchingAttribute.Unknown>(jsonObject.toString())
            }.toList()
        }
        return matchingRules
    }
}