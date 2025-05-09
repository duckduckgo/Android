package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RMFPProSubscriptionStatusMatchingAttributeTest {
    @Mock
    private lateinit var subscriptionsManager: SubscriptionsManager

    private lateinit var matcher: RMFPProSubscriptionStatusMatchingAttribute
    private val testSubscription = Subscription(
        productId = SubscriptionsConstants.YEARLY_PLAN_US,
        billingPeriod = MONTHLY,
        startedAt = 10000L,
        expiresOrRenewsAt = 10000L,
        status = AUTO_RENEWABLE,
        platform = "Google",
        activeOffers = listOf(),
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        matcher = RMFPProSubscriptionStatusMatchingAttribute(subscriptionsManager)
    }

    @Test
    fun whenKeyIsNotStatusThenMappersMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("active"))
        val result = matcher.map("somethingelse", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsStatuseWithInvalidValueTypeThenMappersMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = "active")
        val result = matcher.map("pproSubscriptionStatus", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsStatusWithNullThenMappersMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = null)
        val result = matcher.map("pproSubscriptionStatus", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsStatusWithEmptyValueThenMappersMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = emptyList<String>())
        val result = matcher.map("pproSubscriptionStatus", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsStatusValidThenMappersMapsToAtttribute() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("active"))
        val result = matcher.map("pproSubscriptionStatus", jsonMatchingAttribute)

        assertEquals(PProSubscriptionStatusMatchingAttribute(listOf("active")), result)
    }

    @Test
    fun whenMatchingAttributeHasActiveValueAndSubscriptionIsAutoRenewableThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription)
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("active")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasActiveValueAndSubscriptionIsNotAutoRenewableThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = NOT_AUTO_RENEWABLE))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("active")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasActiveValueAndSubscriptionIsGracePeriodhenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = GRACE_PERIOD))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("active")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasExpiringValueAndSubscriptionIsNotAutoRenewableThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = NOT_AUTO_RENEWABLE))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expiring")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasExpiringValueAndSubscriptionIsAutoRenewableThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription)
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expiring")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasExpiredValueAndSubscriptionIsGracePeriodThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = GRACE_PERIOD))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expired")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasExpiredValueAndSubscriptionIsExpiredThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = EXPIRED))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expired")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasExpiredValueAndSubscriptionIsInactiveThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = INACTIVE))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expired")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasExpiredValueAndSubscriptionIsUnknownThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = UNKNOWN))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expired")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasExpiredValueAndSubscriptionIsWaitingThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = WAITING))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expired")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasActiveValueAndSubscriptionIsWaitingThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = WAITING))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("active")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasActiveValueAndSubscriptionNoSubscriptionThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("active")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasExpiringValueAndSubscriptionNoSubscriptionThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expiring")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasExpiredValueAndSubscriptionNoSubscriptionThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expired")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasInvalidValueThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription)
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("invalid")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasMultipleValuesAndMatchesOneThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = EXPIRED))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expiring", "expired")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasMultipleValuesAndDoesNotMatchThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = AUTO_RENEWABLE))
        val result = matcher.evaluate(PProSubscriptionStatusMatchingAttribute(listOf("expiring", "expired")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }
}
