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

package com.duckduckgo.remote.messaging.impl.mappers

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MessageActionMapperPlugin
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.Surface
import com.duckduckgo.remote.messaging.impl.RemoteMessagingFeatureToggles
import com.duckduckgo.remote.messaging.impl.models.JsonRemoteMessagingConfig
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import logcat.LogPriority.INFO
import logcat.logcat

class RemoteMessagingConfigJsonMapper(
    private val appBuildConfig: AppBuildConfig,
    private val matchingAttributeMappers: Set<JsonToMatchingAttributeMapper>,
    private val actionMappers: Set<MessageActionMapperPlugin>,
    private val remoteMessagingFeatureToggles: RemoteMessagingFeatureToggles,
) {
    fun map(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig): RemoteConfig {
        val messages = jsonRemoteMessagingConfig.messages.mapToRemoteMessage(appBuildConfig.deviceLocale, actionMappers)
        logcat(INFO) { "RMF: messages parsed $messages ${Thread.currentThread().name}" }

        val updatedMessages: List<RemoteMessage> =
            if (!remoteMessagingFeatureToggles.remoteMessageModalSurface().isEnabled()) {
                messages.mapNotNull { message ->
                    if (message.surfaces.isEmpty() || message.surfaces.contains(Surface.NEW_TAB_PAGE)) message else null
                }
            } else {
                messages
            }

        logcat(INFO) { "RMF: updatedMessages parsed $updatedMessages ${Thread.currentThread().name}" }
        val rules = jsonRemoteMessagingConfig.rules.mapToMatchingRules(matchingAttributeMappers)
        return RemoteConfig(
            messages = updatedMessages,
            rules = rules,
        )
    }
}
