package com.duckduckgo.subscriptions.impl.withdrawal

import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.ActiveOfferType
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.duckduckgo.subscriptions.impl.services.WithdrawResponse
import com.duckduckgo.subscriptions.impl.services.WithdrawalEligibilityResponse
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult.Failure
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult.FailureReason
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult.Success
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
    private val subscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java, FakeToggleStore()).apply {
        subscriptionWithdrawal().setRawStoredState(Toggle.State(remoteEnableState = true))
    }
    private val withdrawal = RealSubscriptionWithdrawal(subscriptionsService, subscriptionsManager, subscriptionsFeature)

    @Test
    fun whenActivePaidStripeSubscriptionThenWithdrawable() {
        assertTrue(withdrawal.isWithdrawable(subscription()))
    }

    @Test
    fun whenWithdrawalFlagDisabledThenNotWithdrawable() {
        subscriptionsFeature.subscriptionWithdrawal().setRawStoredState(Toggle.State(remoteEnableState = false))

        assertFalse(withdrawal.isWithdrawable(subscription()))
    }

    @Test
    fun whenNotStripeSubscriptionThenNotWithdrawable() {
        assertFalse(withdrawal.isWithdrawable(subscription(platform = "google")))
    }

    @Test
    fun whenStripeFreeTrialThenNotWithdrawable() {
        assertFalse(withdrawal.isWithdrawable(subscription(activeOffers = listOf(ActiveOfferType.TRIAL))))
    }

    @Test
    fun whenStripeSubscriptionExpiredThenNotWithdrawable() {
        assertFalse(withdrawal.isWithdrawable(subscription(status = EXPIRED)))
    }

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
    fun whenWithdrawSucceedsThenReturnRefund() = runTest {
        whenever(subscriptionsService.withdraw()).thenReturn(WithdrawResponse(refundedAmount = "9.99", currency = "gbp"))

        val result = withdrawal.withdraw()

        assertEquals(Success(REFUND), result)
    }

    @Test
    fun whenWithdrawSucceedsWithMalformedAmountThenReturnSuccessWithoutRefund() = runTest {
        whenever(subscriptionsService.withdraw()).thenReturn(WithdrawResponse(refundedAmount = "9,99", currency = "gbp"))

        assertEquals(Success(refund = null), withdrawal.withdraw())
    }

    @Test
    fun whenWithdrawSucceedsWithoutCurrencyThenReturnSuccessWithoutRefund() = runTest {
        whenever(subscriptionsService.withdraw()).thenReturn(WithdrawResponse(refundedAmount = "9.99", currency = null))

        assertEquals(Success(refund = null), withdrawal.withdraw())
    }

    @Test
    fun whenWithdrawResponseCannotBeDecodedThenReturnSuccessWithoutRefund() = runTest {
        whenever(subscriptionsService.withdraw()).thenAnswer { throw JsonDataException("Expected a string but was BEGIN_OBJECT") }

        assertEquals(Success(refund = null), withdrawal.withdraw())
        verify(subscriptionsManager).signOut()
    }

    @Test
    fun whenWithdrawSucceedsThenSignOut() = runTest {
        whenever(subscriptionsService.withdraw()).thenReturn(WithdrawResponse(refundedAmount = "9.99", currency = "gbp"))

        withdrawal.withdraw()

        verify(subscriptionsManager).signOut()
    }

    @Test
    fun whenWithdrawFailsThenDoNotSignOut() = runTest {
        whenever(subscriptionsService.withdraw()).thenThrow(httpException(500))

        withdrawal.withdraw()

        verify(subscriptionsManager, never()).signOut()
    }

    @Test
    fun whenWithdrawReturns409ThenNotEligible() = runTest {
        whenever(subscriptionsService.withdraw()).thenThrow(httpException(409))

        assertEquals(Failure(FailureReason.NOT_ELIGIBLE), withdrawal.withdraw())
        verify(subscriptionsManager, never()).signOut()
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

    private fun subscription(
        platform: String = "stripe",
        status: SubscriptionStatus = AUTO_RENEWABLE,
        activeOffers: List<ActiveOfferType> = emptyList(),
    ) = Subscription(
        productId = SubscriptionsConstants.MONTHLY_PLAN_ROW,
        billingPeriod = "Monthly",
        startedAt = 1760950800000,
        expiresOrRenewsAt = 1763629200000,
        status = status,
        platform = platform,
        activeOffers = activeOffers,
    )

    private fun httpException(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private companion object {
        private val REFUND = Refund(amount = BigDecimal("9.99"), currency = "GBP")
    }

    private fun eligibleResponse() = WithdrawalEligibilityResponse(
        eligible = true,
        eligibleUntil = 1760950800000,
        refundAmount = "9.99",
        refundCurrency = "GBP",
        reason = null,
    )
}
