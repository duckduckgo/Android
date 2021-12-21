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

import com.duckduckgo.app.remotemessage.impl.matchingattributes.MatchingAttribute
import com.duckduckgo.app.remotemessage.impl.matchingattributes.MatchingAttribute.Unknown
import com.duckduckgo.app.remotemessage.impl.messages.*
import timber.log.Timber

class RemoteMessagingConfigJsonParser(
    private val jsonRemoteMessageMapper: JsonRemoteMessageMapper,
    private val jsonRulesMapper: JsonRulesMapper
) {

    fun map(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig): RemoteConfig {
        val messages = jsonRemoteMessageMapper.map(jsonRemoteMessagingConfig.messages)

        Timber.i("RMF: messages parsed $messages")
        val matchingRules = jsonRulesMapper.map(jsonRemoteMessagingConfig.matchingRules)
        return RemoteConfig(
            messages = messages,
            rules = matchingRules
        )
    }

    private fun parseMatchingRules(
        jsonMatchingRules: List<JsonMatchingRule>
    ): Map<Int, List<MatchingAttribute?>> {
        val matchingRules = mutableMapOf<Int, List<MatchingAttribute?>>()

        jsonMatchingRules.forEach {
            Timber.i("RMF: MatchingRule ${it.id}")

            matchingRules[it.id] = it.attributes.mapNotNull { (key, jsonMatchingAttribute) ->
                Timber.i("RMF: MatchingRule $key")

                return@mapNotNull kotlin.runCatching {
                    when (key) {
                        "locale" -> {
                            MatchingAttribute.Locale(
                                value = jsonMatchingAttribute.value as List<String>,
                                fallback = jsonMatchingAttribute.fallback
                            )
                        }
                        "defaultBrowser" -> {
                            MatchingAttribute.DefaultBrowser(
                                value = jsonMatchingAttribute.value as Boolean,
                                fallback = jsonMatchingAttribute.fallback
                            )
                        }
                        else -> Unknown(fallback = jsonMatchingAttribute.fallback)
                    }
                }.getOrDefault(Unknown(fallback = jsonMatchingAttribute.fallback))
            }.toList()
        }
        return matchingRules
    }

    /*private fun parseMatchingRules(
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
    }*/
}
