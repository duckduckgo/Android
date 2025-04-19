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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.isExpired
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
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
class RMFPProDaysUntilExpiryRenewalMatchingAttribute @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val currentTimeProvider: CurrentTimeProvider,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        return when (matchingAttribute) {
            is PProDaysUntilExpiryRenewalMatchingAttribute -> {
                val subscription = subscriptionsManager.getSubscription()
                return if (subscription == null || subscription.status.isExpired() ||
                    matchingAttribute == PProDaysUntilExpiryRenewalMatchingAttribute()
                ) {
                    false
                } else {
                    val daysUntilRenewalOrExpiry = daysUntilRenewalOrExpiry(subscription.expiresOrRenewsAt)
                    if (!matchingAttribute.value.isDefaultValue()) {
                        matchingAttribute.value == daysUntilRenewalOrExpiry
                    } else {
                        (matchingAttribute.min.isDefaultValue() || daysUntilRenewalOrExpiry >= matchingAttribute.min) &&
                            (matchingAttribute.max.isDefaultValue() || daysUntilRenewalOrExpiry <= matchingAttribute.max)
                    }
                }
            }

            else -> null
        }
    }

    private fun Int.isDefaultValue(): Boolean {
        return this == -1
    }

    private fun daysUntilRenewalOrExpiry(expiresOrRenewsAt: Long): Int {
        return TimeUnit.MILLISECONDS.toDays(expiresOrRenewsAt - currentTimeProvider.currentTimeMillis()).toInt()
    }

    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        return when (key) {
            PProDaysUntilExpiryRenewalMatchingAttribute.KEY -> {
                try {
                    PProDaysUntilExpiryRenewalMatchingAttribute(
                        min = jsonMatchingAttribute.min.toIntOrDefault(-1),
                        max = jsonMatchingAttribute.max.toIntOrDefault(-1),
                        value = jsonMatchingAttribute.value.toIntOrDefault(-1),
                    )
                } catch (e: Exception) {
                    null
                }
            }

            else -> null
        }
    }
}

internal data class PProDaysUntilExpiryRenewalMatchingAttribute(
    val min: Int = -1,
    val max: Int = -1,
    val value: Int = -1,
) : MatchingAttribute {
    companion object {
        const val KEY = "pproDaysUntilExpiryOrRenewal"
    }
}
