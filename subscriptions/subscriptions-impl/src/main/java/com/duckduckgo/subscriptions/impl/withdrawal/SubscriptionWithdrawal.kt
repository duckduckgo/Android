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
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.duckduckgo.subscriptions.impl.services.WithdrawResponse
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult.Failure
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult.Success
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import dev.zacsweers.metro.ContributesBinding
import retrofit2.HttpException
import java.math.BigDecimal
import javax.inject.Inject

interface SubscriptionWithdrawal {
    /**
     * Returns the withdrawal the user can make right now, or null when they are not eligible
     * or eligibility could not be determined.
     */
    suspend fun getEligibility(): WithdrawalEligibility?

    /**
     * Ends the subscription immediately, refunds the user and, on success, removes the account from this device.
     * Safe to call again after a failure: the backend finishes an interrupted withdrawal without refunding twice.
     */
    suspend fun withdraw(): WithdrawalResult
}

sealed class WithdrawalResult {
    /** [refund] is null when the backend completed the withdrawal but the refunded amount could not be read. */
    data class Success(val refund: Refund?) : WithdrawalResult()

    data class Failure(val reason: FailureReason) : WithdrawalResult()

    enum class FailureReason {
        NOT_ELIGIBLE,
        NOT_STRIPE_SUBSCRIPTION,
        NO_SUBSCRIPTION,
        UNKNOWN,
    }
}

data class Refund(
    val amount: BigDecimal,
    val currency: String,
)

data class WithdrawalEligibility(
    val eligibleUntil: Long,
    val refundAmount: BigDecimal,
    val refundCurrency: String,
)

@ContributesBinding(AppScope::class)
class RealSubscriptionWithdrawal @Inject constructor(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionsManager: SubscriptionsManager,
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
        val refund = try {
            subscriptionsService.withdraw().toRefund()
        } catch (e: HttpException) {
            return Failure(e.toFailureReason())
        } catch (e: JsonDataException) {
            // A body that can't be decoded only arrives with a 2xx, so the backend has already withdrawn the user
            null
        } catch (e: JsonEncodingException) {
            null
        } catch (e: Exception) {
            return Failure(WithdrawalResult.FailureReason.UNKNOWN)
        }

        // The withdrawn account has nothing left to manage, so it goes the same way as removing it from the device
        subscriptionsManager.signOut()

        return Success(refund)
    }

    private fun HttpException.toFailureReason(): WithdrawalResult.FailureReason = when (code()) {
        400 -> WithdrawalResult.FailureReason.NOT_STRIPE_SUBSCRIPTION
        404 -> WithdrawalResult.FailureReason.NO_SUBSCRIPTION
        409 -> WithdrawalResult.FailureReason.NOT_ELIGIBLE
        else -> WithdrawalResult.FailureReason.UNKNOWN
    }

    private fun WithdrawResponse.toRefund(): Refund? {
        val amount = refundedAmount?.toBigDecimalOrNull() ?: return null
        val currency = currency?.takeIf { it.isNotBlank() } ?: return null
        return Refund(amount = amount, currency = currency.uppercase())
    }
}
