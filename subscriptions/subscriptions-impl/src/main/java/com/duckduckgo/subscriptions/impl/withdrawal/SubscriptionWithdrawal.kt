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

package com.duckduckgo.subscriptions.impl.withdrawal

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import dev.zacsweers.metro.ContributesBinding
import java.math.BigDecimal
import javax.inject.Inject

interface SubscriptionWithdrawal {
    /**
     * Returns the withdrawal the user can make right now, or null when they are not eligible
     * or eligibility could not be determined.
     */
    suspend fun getEligibility(): WithdrawalEligibility?
}

data class WithdrawalEligibility(
    val eligibleUntil: Long,
    val refundAmount: BigDecimal,
    val refundCurrency: String,
)

@ContributesBinding(AppScope::class)
class RealSubscriptionWithdrawal @Inject constructor(
    private val subscriptionsService: SubscriptionsService,
) : SubscriptionWithdrawal {

    override suspend fun getEligibility(): WithdrawalEligibility? {
        val response = runCatching { subscriptionsService.withdrawalEligibility() }.getOrNull() ?: return null
        if (!response.eligible) return null

        val eligibleUntil = response.eligibleUntil ?: return null
        val refundAmount = response.refundAmount?.toBigDecimalOrNull() ?: return null
        val refundCurrency = response.refundCurrency?.takeIf { it.isNotBlank() } ?: return null

        return WithdrawalEligibility(
            eligibleUntil = eligibleUntil,
            refundAmount = refundAmount,
            refundCurrency = refundCurrency,
        )
    }
}
