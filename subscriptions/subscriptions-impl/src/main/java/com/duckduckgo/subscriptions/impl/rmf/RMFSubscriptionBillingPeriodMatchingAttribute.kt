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
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
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
class RMFSubscriptionBillingPeriodMatchingAttribute @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        if (matchingAttribute is PProBillingPeriodMatchingAttribute) {
            return matchingAttribute.value == subscriptionsManager.getSubscription()?.billingPeriod
        }
        return null
    }

    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        if (key == PProBillingPeriodMatchingAttribute.KEY) {
            val value = jsonMatchingAttribute.value as? String
            return value.takeIf { !it.isNullOrEmpty() }?.let {
                PProBillingPeriodMatchingAttribute(value = it)
            }
        }
        return null
    }
}

internal data class PProBillingPeriodMatchingAttribute(
    val value: String,
) : MatchingAttribute {
    companion object {
        const val KEY = "pproBillingPeriod"
    }
}
