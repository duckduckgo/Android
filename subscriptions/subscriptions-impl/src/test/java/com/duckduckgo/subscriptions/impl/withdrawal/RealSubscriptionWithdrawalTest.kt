package com.duckduckgo.subscriptions.impl.withdrawal

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.duckduckgo.subscriptions.impl.services.WithdrawResponse
import com.duckduckgo.subscriptions.impl.services.WithdrawalEligibilityResponse
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult.Failure
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult.FailureReason
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult.Success
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import java.math.BigDecimal

class RealSubscriptionWithdrawalTest {

    private val subscriptionsService: SubscriptionsService = mock()
    private val subscriptionsManager: SubscriptionsManager = mock()
    private val withdrawalStore: WithdrawalStore = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock {
        on { currentTimeMillis() } doReturn NOW
    }
    private val withdrawal = RealSubscriptionWithdrawal(subscriptionsService, subscriptionsManager, withdrawalStore, currentTimeProvider)

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

    @Test
    fun whenWithdrawSucceedsThenReturnRefundedAmountInMajorUnits() = runTest {
        whenever(subscriptionsService.withdraw()).thenReturn(WithdrawResponse(refundedAmount = 999, currency = "gbp"))

        val result = withdrawal.withdraw()

        assertEquals(Success(refundedAmount = BigDecimal("9.99"), currency = "GBP"), result)
    }

    @Test
    fun whenWithdrawSucceedsForZeroDecimalCurrencyThenAmountIsNotScaled() = runTest {
        whenever(subscriptionsService.withdraw()).thenReturn(WithdrawResponse(refundedAmount = 1500, currency = "jpy"))

        val result = withdrawal.withdraw() as Success

        assertEquals(BigDecimal("1500"), result.refundedAmount)
    }

    @Test
    fun whenWithdrawSucceedsThenRefreshSubscriptionAndAccessToken() = runTest {
        whenever(subscriptionsService.withdraw()).thenReturn(WithdrawResponse(refundedAmount = 999, currency = "gbp"))

        withdrawal.withdraw()

        verify(subscriptionsManager).refreshSubscriptionData()
        verify(subscriptionsManager).refreshAccessToken()
    }

    @Test
    fun whenWithdrawSucceedsButRefreshFailsThenStillReturnSuccess() = runTest {
        whenever(subscriptionsService.withdraw()).thenReturn(WithdrawResponse(refundedAmount = 999, currency = "gbp"))
        whenever(subscriptionsManager.refreshSubscriptionData()).thenThrow(RuntimeException())

        val result = withdrawal.withdraw()

        assertEquals(Success(refundedAmount = BigDecimal("9.99"), currency = "GBP"), result)
        verify(subscriptionsManager).refreshAccessToken()
    }

    @Test
    fun whenWithdrawSucceedsThenSaveCompletedWithdrawalBeforeRefreshing() = runTest {
        whenever(subscriptionsService.withdraw()).thenReturn(WithdrawResponse(refundedAmount = 999, currency = "gbp"))

        withdrawal.withdraw()

        inOrder(withdrawalStore, subscriptionsManager) {
            verify(withdrawalStore).setCompletedWithdrawal(
                CompletedWithdrawal(withdrawnAt = NOW, refundedAmount = BigDecimal("9.99"), currency = "GBP"),
            )
            verify(subscriptionsManager).refreshSubscriptionData()
        }
    }

    @Test
    fun whenWithdrawFailsThenDoNotSaveCompletedWithdrawal() = runTest {
        whenever(subscriptionsService.withdraw()).thenThrow(httpException(500))

        withdrawal.withdraw()

        verify(withdrawalStore, never()).setCompletedWithdrawal(any())
    }

    @Test
    fun whenWithdrawReturns409ThenNotEligible() = runTest {
        whenever(subscriptionsService.withdraw()).thenThrow(httpException(409))

        assertEquals(Failure(FailureReason.NOT_ELIGIBLE), withdrawal.withdraw())
        verify(subscriptionsManager, never()).refreshSubscriptionData()
    }

    @Test
    fun whenWithdrawReturns400ThenNotStripeSubscription() = runTest {
        whenever(subscriptionsService.withdraw()).thenThrow(httpException(400))

        assertEquals(Failure(FailureReason.NOT_STRIPE_SUBSCRIPTION), withdrawal.withdraw())
    }

    @Test
    fun whenWithdrawReturns404ThenNoSubscription() = runTest {
        whenever(subscriptionsService.withdraw()).thenThrow(httpException(404))

        assertEquals(Failure(FailureReason.NO_SUBSCRIPTION), withdrawal.withdraw())
    }

    @Test
    fun whenWithdrawReturns500ThenUnknown() = runTest {
        whenever(subscriptionsService.withdraw()).thenThrow(httpException(500))

        assertEquals(Failure(FailureReason.UNKNOWN), withdrawal.withdraw())
    }

    @Test
    fun whenWithdrawThrowsThenUnknown() = runTest {
        whenever(subscriptionsService.withdraw()).thenThrow(RuntimeException())

        assertEquals(Failure(FailureReason.UNKNOWN), withdrawal.withdraw())
    }

    private fun httpException(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private companion object {
        private const val NOW = 1792065600000L
    }

    private fun eligibleResponse() = WithdrawalEligibilityResponse(
        eligible = true,
        eligibleUntil = 1760950800000,
        refundAmount = "9.99",
        refundCurrency = "GBP",
        reason = null,
    )
}
