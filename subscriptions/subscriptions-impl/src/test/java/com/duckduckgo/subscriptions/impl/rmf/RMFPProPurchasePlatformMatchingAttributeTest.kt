package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RMFPProPurchasePlatformMatchingAttributeTest {
    @Mock
    private lateinit var subscriptionsManager: SubscriptionsManager

    private lateinit var matcher: RMFPProPurchasePlatformMatchingAttribute

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        matcher = RMFPProPurchasePlatformMatchingAttribute(subscriptionsManager)
    }

    @Test
    fun whenKeyIsNotPProPurchasePlatformThenMappersMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("1", "2", "3"))
        val result = matcher.map("somethingelse", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsPProPurchasePlatformThenMapperMapsToPurchasePlatform() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("Android", "iOS"))
        val result = matcher.map("pproPurchasePlatform", jsonMatchingAttribute)

        assertNotNull(result)
        result?.let {
            assertEquals(PProPurchasePlatformMatchingAttribute(listOf("Android", "iOS")), result)
        }
    }

    @Test
    fun whenKeyIsPProPurchasePlatformWithEmptyPlatformsThenMapperMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = emptyList<String>())
        val result = matcher.map("pproPurchasePlatform", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsPProPurchasePlatformWithNullPlatformsThenMapperMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = null)
        val result = matcher.map("pproPurchasePlatform", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenMatchingAttributeHasSubscriptionPlatformTThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "Google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProPurchasePlatformMatchingAttribute(listOf("google", "ios")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMatchingAttributeDoesNotHaveMatchingSubscriptionPlatformTThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "iOS",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProPurchasePlatformMatchingAttribute(listOf("android")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenSubscriptionIsNullTThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)
        val result = matcher.evaluate(PProPurchasePlatformMatchingAttribute(listOf("android")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenSubscriptionPlatformIsEmptyTThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProPurchasePlatformMatchingAttribute(listOf("android")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }
}
