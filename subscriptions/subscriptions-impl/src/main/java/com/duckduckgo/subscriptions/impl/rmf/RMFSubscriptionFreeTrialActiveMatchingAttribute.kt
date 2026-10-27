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

package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
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
class RMFSubscriptionFreeTrialActiveMatchingAttribute @Inject constructor(
    private val authRepository: AuthRepository,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        return when (matchingAttribute) {
            is SubscriptionFreeTrialActiveMatchingAttribute -> {
                authRepository.isFreeTrialActive() == matchingAttribute.remoteValue
            }

            else -> null
        }
    }

    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        return when (key) {
            SubscriptionFreeTrialActiveMatchingAttribute.KEY -> {
                jsonMatchingAttribute.value?.let {
                    SubscriptionFreeTrialActiveMatchingAttribute(jsonMatchingAttribute.value as Boolean)
                }
            }
            else -> null
        }
    }
}

private data class SubscriptionFreeTrialActiveMatchingAttribute(
    val remoteValue: Boolean,
) : MatchingAttribute {
    companion object {
        const val KEY = "subscriptionFreeTrialActive"
    }
}
