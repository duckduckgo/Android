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
import com.duckduckgo.remote.messaging.impl.RemoteMessagingRepository
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import javax.inject.Inject

@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<JsonToMatchingAttributeMapper>(),
)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<AttributeMatcherPlugin>(),
)
@SingleIn(AppScope::class)
class InteractedWithMessageMatcher @Inject constructor(
    private val remoteMessagingRepository: RemoteMessagingRepository,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {
    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        return when (key) {
            "interactedWithMessage" -> {
                val value = jsonMatchingAttribute.value as? List<String>
                value.takeUnless { it.isNullOrEmpty() }?.let { messageIds ->
                    InteractedWithMessageMatchingAttribute(messageIds)
                }
            }

            else -> null
        }
    }

    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        if (matchingAttribute is InteractedWithMessageMatchingAttribute) {
            assert(matchingAttribute.messageIds.isNotEmpty())

            remoteMessagingRepository.dismissedMessages().forEach {
                if ((matchingAttribute.messageIds).contains(it)) {
                    return true
                }
            }
            return false
        }
        return null
    }
}

data class InteractedWithMessageMatchingAttribute(
    val messageIds: List<String>,
) : MatchingAttribute
