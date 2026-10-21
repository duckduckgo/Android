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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult.Failure
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult.Success
import dev.zacsweers.metro.ContributesBinding
import retrofit2.HttpException
import java.math.BigDecimal
import java.util.Currency
import javax.inject.Inject

interface SubscriptionWithdrawal {
    /**
     * Returns the withdrawal the user can make right now, or null when they are not eligible
     * or eligibility could not be determined.
     */
    suspend fun getEligibility(): WithdrawalEligibility?

    /**
     * Ends the subscription immediately and refunds the user. Safe to call again after a failure:
     * the backend finishes an interrupted withdrawal without refunding twice.
     */
    suspend fun withdraw(): WithdrawalResult

    /**
     * Returns the last withdrawal made on this device, or null if there is none.
     */
    fun getCompletedWithdrawal(): CompletedWithdrawal?

    fun clearCompletedWithdrawal()
}

sealed class WithdrawalResult {
    data class Success(
        val refundedAmount: BigDecimal,
        val currency: String,
    ) : WithdrawalResult()

    data class Failure(val reason: FailureReason) : WithdrawalResult()

    enum class FailureReason {
        NOT_ELIGIBLE,
        NOT_STRIPE_SUBSCRIPTION,
        NO_SUBSCRIPTION,
        UNKNOWN,
    }
}

data class WithdrawalEligibility(
    val eligibleUntil: Long,
    val refundAmount: BigDecimal,
    val refundCurrency: String,
)

@ContributesBinding(AppScope::class)
class RealSubscriptionWithdrawal @Inject constructor(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionsManager: SubscriptionsManager,
    private val withdrawalStore: WithdrawalStore,
    private val currentTimeProvider: CurrentTimeProvider,
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

    override suspend fun withdraw(): WithdrawalResult {
        val response = try {
            subscriptionsService.withdraw()
        } catch (e: HttpException) {
            return Failure(e.toFailureReason())
        } catch (e: Exception) {
            return Failure(WithdrawalResult.FailureReason.UNKNOWN)
        }

        val result = Success(
            refundedAmount = response.refundedAmount.fromMinorUnits(response.currency),
            currency = response.currency.uppercase(),
        )
        // Saved before refreshing, so the expired status emitted by the refresh already renders as a withdrawal
        withdrawalStore.setCompletedWithdrawal(
            CompletedWithdrawal(
                withdrawnAt = currentTimeProvider.currentTimeMillis(),
                refundedAmount = result.refundedAmount,
                currency = result.currency,
            ),
        )

        // The backend expires the subscription in the same request; refresh so access is revoked locally right away
        runCatching { subscriptionsManager.refreshSubscriptionData() }
        runCatching { subscriptionsManager.refreshAccessToken() }

        return result
    }

    override fun getCompletedWithdrawal(): CompletedWithdrawal? = withdrawalStore.getCompletedWithdrawal()

    override fun clearCompletedWithdrawal() = withdrawalStore.clear()

    private fun HttpException.toFailureReason(): WithdrawalResult.FailureReason = when (code()) {
        400 -> WithdrawalResult.FailureReason.NOT_STRIPE_SUBSCRIPTION
        404 -> WithdrawalResult.FailureReason.NO_SUBSCRIPTION
        409 -> WithdrawalResult.FailureReason.NOT_ELIGIBLE
        else -> WithdrawalResult.FailureReason.UNKNOWN
    }

    private fun Long.fromMinorUnits(currencyCode: String): BigDecimal {
        val fractionDigits = runCatching { Currency.getInstance(currencyCode.uppercase()).defaultFractionDigits }
            .getOrNull()
            ?.takeIf { it >= 0 }
            ?: DEFAULT_FRACTION_DIGITS
        return BigDecimal.valueOf(this, fractionDigits)
    }

    private companion object {
        private const val DEFAULT_FRACTION_DIGITS = 2
    }
}
