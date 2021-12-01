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
import com.duckduckgo.app.remotemessage.store.RemoteMessagingConfig
import com.duckduckgo.app.remotemessage.store.RemoteMessagingConfigRepository
import okhttp3.internal.toImmutableList
import timber.log.Timber

interface RemoteMessagingConfigProcessor {
    suspend fun process(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig)
}

class RealRemoteMessagingConfigProcessor(
    private val matchingAttributesPluginPoint: PluginPoint<MatchingAttributePlugin>,
    private val remoteMessagingConfigRepository: RemoteMessagingConfigRepository
) : RemoteMessagingConfigProcessor {

    override suspend fun process(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig) {
        Timber.i("RMF: process ${jsonRemoteMessagingConfig.version}")
        val currentVersion = remoteMessagingConfigRepository.get().version
        val newVersion = jsonRemoteMessagingConfig.version

        // if (currentVersion != newVersion) {
        if (true) {
            Timber.i("RMF: new version received")
            val matchingRules = parseMatchingRules(jsonRemoteMessagingConfig.matchingRules)
            remoteMessagingConfigRepository.insert(RemoteMessagingConfig(version = jsonRemoteMessagingConfig.version))
        } else {
            Timber.i("RMF: skip, same version")
        }

    }

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
}
