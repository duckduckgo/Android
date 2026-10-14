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

package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.subscriptions.impl.SubscriptionTier
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
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
class RMFSubscriptionTierMatchingAttribute @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        return when (matchingAttribute) {
            is PProSubscriptionTierMatchingAttribute -> {
                val currentTier = subscriptionsManager.getSubscription()?.tier ?: return false
                if (currentTier == SubscriptionTier.UNKNOWN) return false

                matchingAttribute.tiers.any { targetTier ->
                    targetTier.equals(currentTier.value, ignoreCase = true)
                }
            }

            else -> null
        }
    }

    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        return when (key) {
            PProSubscriptionTierMatchingAttribute.KEY -> {
                @Suppress("UNCHECKED_CAST")
                val tiers = jsonMatchingAttribute.value as? List<String>
                if (tiers.isNullOrEmpty()) return null

                PProSubscriptionTierMatchingAttribute(tiers = tiers)
            }

            else -> null
        }
    }
}

internal data class PProSubscriptionTierMatchingAttribute(
    val tiers: List<String>,
) : MatchingAttribute {
    companion object {
        const val KEY = "pproSubscriptionTier"
    }
}
