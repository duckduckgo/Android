/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.matchers

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = JsonToMatchingAttributeMapper::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AttributeMatcherPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class ShownMessageMatcher @Inject constructor(
    private val remoteMessagingRepository: RemoteMessagingRepository,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {
    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        return when (key) {
            "messageShown" -> {
                val value = jsonMatchingAttribute.value
                if (value is List<*>) {
                    val messageIds = value.filterIsInstance<String>()
                    if (messageIds.isNotEmpty()) {
                        return ShownMessageMatchingAttribute(messageIds)
                    }
                }

                return null
            }

            else -> null
        }
    }

    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        if (matchingAttribute is ShownMessageMatchingAttribute) {
            assert(matchingAttribute.messageIds.isNotEmpty())

            val currentMessage = remoteMessagingRepository.message()
            matchingAttribute.messageIds.forEach {
                if (currentMessage?.id != it) {
                    if (remoteMessagingRepository.didShow(it)) return true
                }
            }
            return false
        }
        return null
    }
}

data class ShownMessageMatchingAttribute(
    val messageIds: List<String>,
) : MatchingAttribute
