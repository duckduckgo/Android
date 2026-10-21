package com.duckduckgo.subscriptions.impl.withdrawal

import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.duckduckgo.subscriptions.impl.services.WithdrawalEligibilityResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class RealSubscriptionWithdrawalTest {

    private val subscriptionsService: SubscriptionsService = mock()
    private val withdrawal = RealSubscriptionWithdrawal(subscriptionsService)

    @Test
    fun whenEligibleThenReturnEligibility() = runTest {
        whenever(subscriptionsService.withdrawalEligibility()).thenReturn(eligibleResponse())

        val eligibility = withdrawal.getEligibility()

        assertEquals(WithdrawalEligibility(eligibleUntil = 1760950800000, refundAmount = BigDecimal("9.99"), refundCurrency = "GBP"), eligibility)
    }

    @Test
    fun whenNotEligibleThenReturnNull() = runTest {
        whenever(subscriptionsService.withdrawalEligibility()).thenReturn(
            WithdrawalEligibilityResponse(eligible = false, eligibleUntil = null, refundAmount = null, refundCurrency = null, reason = "trial"),
        )

        assertNull(withdrawal.getEligibility())
    }

    @Test
    fun whenEligibleButMissingAmountThenReturnNull() = runTest {
        whenever(subscriptionsService.withdrawalEligibility()).thenReturn(eligibleResponse().copy(refundAmount = null))

        assertNull(withdrawal.getEligibility())
    }

    @Test
    fun whenEligibleButAmountMalformedThenReturnNull() = runTest {
        whenever(subscriptionsService.withdrawalEligibility()).thenReturn(eligibleResponse().copy(refundAmount = "abc"))

        assertNull(withdrawal.getEligibility())
    }

    @Test
    fun whenRequestFailsThenReturnNull() = runTest {
        whenever(subscriptionsService.withdrawalEligibility()).thenThrow(RuntimeException())

        assertNull(withdrawal.getEligibility())
    }

    private fun eligibleResponse() = WithdrawalEligibilityResponse(
        eligible = true,
        eligibleUntil = 1760950800000,
        refundAmount = "9.99",
        refundCurrency = "GBP",
        reason = null,
    )
}
