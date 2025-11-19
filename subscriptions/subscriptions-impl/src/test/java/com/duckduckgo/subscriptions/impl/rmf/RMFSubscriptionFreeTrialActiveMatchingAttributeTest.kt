package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.subscriptions.api.ActiveOfferType
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RMFSubscriptionFreeTrialActiveMatchingAttributeTest {

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val attribute = RMFSubscriptionFreeTrialActiveMatchingAttribute(subscriptionsManager)

    @Test
    fun evaluateWithWrongAttributeThenNull() = runTest {
        val subscription = createSubscription(activeOffers = listOf(ActiveOfferType.TRIAL))
        whenever(subscriptionsManager.getSubscription()).thenReturn(subscription)

        assertNull(attribute.evaluate(FakeStringMatchingAttribute { "" }))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = false)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = true)))
    }

    @Test
    fun whenRemoteValueIsTrueAndFreeTrialIsActiveThenEvaluateToTrue() = runTest {
        val subscription = createSubscription(activeOffers = listOf(ActiveOfferType.TRIAL))
        whenever(subscriptionsManager.getSubscription()).thenReturn(subscription)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = true))!!)

        assertTrue(result!!)
    }

    @Test
    fun whenRemoteValueIsTrueAndFreeTrialIsNotActiveThenEvaluateToFalse() = runTest {
        val subscription = createSubscription(activeOffers = emptyList())
        whenever(subscriptionsManager.getSubscription()).thenReturn(subscription)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = true))!!)

        assertFalse(result!!)
    }

    @Test
    fun whenRemoteValueIsFalseAndFreeTrialIsNotActiveThenEvaluateToTrue() = runTest {
        val subscription = createSubscription(activeOffers = emptyList())
        whenever(subscriptionsManager.getSubscription()).thenReturn(subscription)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = false))!!)

        assertTrue(result!!)
    }

    @Test
    fun whenRemoteValueIsFalseAndFreeTrialIsActiveThenEvaluateToFalse() = runTest {
        val subscription = createSubscription(activeOffers = listOf(ActiveOfferType.TRIAL))
        whenever(subscriptionsManager.getSubscription()).thenReturn(subscription)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = false))!!)

        assertFalse(result!!)
    }

    @Test
    fun whenRemoteValueIsTrueAndNoSubscriptionThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = true))!!)

        assertFalse(result!!)
    }

    @Test
    fun whenRemoteValueIsFalseAndNoSubscriptionThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = false))!!)

        assertTrue(result!!)
    }

    @Test
    fun whenActiveOffersContainsUnknownTypeThenEvaluateCorrectly() = runTest {
        val subscription = createSubscription(activeOffers = listOf(ActiveOfferType.UNKNOWN))
        whenever(subscriptionsManager.getSubscription()).thenReturn(subscription)

        val resultTrue = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = true))!!)
        val resultFalse = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = false))!!)

        assertFalse(resultTrue!!)
        assertTrue(resultFalse!!)
    }

    @Test
    fun whenActiveOffersContainsMultipleTypesIncludingTrialThenEvaluateToTrue() = runTest {
        val subscription = createSubscription(activeOffers = listOf(ActiveOfferType.UNKNOWN, ActiveOfferType.TRIAL))
        whenever(subscriptionsManager.getSubscription()).thenReturn(subscription)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = true))!!)

        assertTrue(result!!)
    }

    @Test
    fun mapNoMatchingAttributeKeyThenReturnNull() = runTest {
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = null)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = true)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = false)))
    }

    @Test
    fun mapNullValueThenReturnNull() = runTest {
        assertNull(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = null)))
    }

    private fun createSubscription(
        activeOffers: List<ActiveOfferType> = emptyList(),
    ): Subscription {
        return Subscription(
            productId = "test-product",
            startedAt = 1234567890L,
            expiresOrRenewsAt = 1234567890L,
            status = SubscriptionStatus.AUTO_RENEWABLE,
            platform = "google",
            billingPeriod = "Monthly",
            activeOffers = activeOffers,
        )
    }
}
