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

package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
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
class RMFPProSubscriptionStatusMatchingAttribute @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        if (matchingAttribute is PProSubscriptionStatusMatchingAttribute) {
            val status = subscriptionsManager.getSubscription()?.status
            return status != null && status.matchesRmfValue(matchingAttribute.value)
        }
        return null
    }

    private fun SubscriptionStatus.matchesRmfValue(value: List<String>): Boolean {
        value.forEach {
            val shouldMatch = when (it) {
                STATUS_ACTIVE -> this == AUTO_RENEWABLE || this == GRACE_PERIOD
                STATUS_EXPIRING -> this == NOT_AUTO_RENEWABLE
                STATUS_EXPIRED -> this == EXPIRED || this == INACTIVE
                else -> false
            }

            if (shouldMatch) return true
        }

        return false
    }

    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        if (key == PProSubscriptionStatusMatchingAttribute.KEY) {
            val value = jsonMatchingAttribute.value as? List<String>
            return value.takeUnless { it.isNullOrEmpty() }?.let {
                PProSubscriptionStatusMatchingAttribute(value = it)
            }
        }
        return null
    }

    companion object {
        private const val STATUS_ACTIVE = "active"
        private const val STATUS_EXPIRING = "expiring"
        private const val STATUS_EXPIRED = "expired"
    }
}

internal data class PProSubscriptionStatusMatchingAttribute(
    val value: List<String>,
) : MatchingAttribute {
    companion object {
        const val KEY = "pproSubscriptionStatus"
    }
}
