package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
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
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class RMFPProDaysSinceSubscribedMatchingAttributeTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var subscriptionsManager: SubscriptionsManager

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    private lateinit var matcher: RMFPProDaysSinceSubscribedMatchingAttribute

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        matcher = RMFPProDaysSinceSubscribedMatchingAttribute(
            subscriptionsManager,
            currentTimeProvider,
        )
    }

    @Test
    fun whenMapKeyIsPProDaysSinceSubscribedThenReturnMatchingAttribute() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute(min = 20, max = 30)
        val result = matcher.map("pproDaysSinceSubscribed", jsonMatchingAttribute)
        assertEquals(PProDaysSinceSubscribedMatchingAttribute(min = 20, max = 30), result)
    }

    @Test
    fun whenMapKeyIsNotPProDaysSinceSubscribedTheMapReturnNull() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute(min = 20, max = 30)
        val result = matcher.map("somethingelse", jsonMatchingAttribute)
        assertNull(result)
    }

    @Test
    fun whenMapKeyIsPProDaysSinceSubscribedButValueIsInvalidTheMapReturnNull() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = "20")
        val result = matcher.map("pproDaysSinceSubscribed", jsonMatchingAttribute)
        assertNull(result)
    }

    @Test
    fun whenMapKeyIsPProDaysSinceSubscribedWithEmptyValuesTheMapReturnAttribute() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute()
        val result = matcher.map("pproDaysSinceSubscribed", jsonMatchingAttribute)
        assertEquals(PProDaysSinceSubscribedMatchingAttribute(), result)
    }

    @Test
    fun whenMapKeyIsPProDaysSinceSubscribedButValueAreMixedWithInvalidTheMapReturnNull() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf(20), min = 20, max = 24)
        val result = matcher.map("pproDaysSinceSubscribed", jsonMatchingAttribute)
        assertNull(result)
    }

    @Test
    fun whenNoPropertiesSetOnMapperThenEvaluateReturnFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProDaysSinceSubscribedMatchingAttribute())
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenSubscriptionIsNullThenReturnEvaluateReturnsFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)
        val result = matcher.evaluate(PProDaysSinceSubscribedMatchingAttribute(value = 10))
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenValueIsEqualToSubscribeDaysThenEvaluateReturnTrue() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(16))
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = TimeUnit.DAYS.toMillis(1),
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProDaysSinceSubscribedMatchingAttribute(value = 15, max = 13, min = 10))
        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMaxIsGreaterThanSubscribeDaysThenEvaluateReturnTrue() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(14))
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = TimeUnit.DAYS.toMillis(1),
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProDaysSinceSubscribedMatchingAttribute(max = 15))
        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMaxIsLessThanSubscribeDaysThenEvaluateReturnFalse() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(18))
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = TimeUnit.DAYS.toMillis(1),
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProDaysSinceSubscribedMatchingAttribute(max = 15))
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMinIsLessThanSubscribeDaysThenEvaluateReturnTrue() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(14))
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = TimeUnit.DAYS.toMillis(1),
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProDaysSinceSubscribedMatchingAttribute(min = 5))
        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMinIsGreaterThanSubscribeDaysThenEvaluateReturnFalse() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(4))
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = TimeUnit.DAYS.toMillis(1),
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProDaysSinceSubscribedMatchingAttribute(min = 5))
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenSubscribeDaysIsBetweenMinAndMaxThenEvaluateReturnTrue() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(8))
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = TimeUnit.DAYS.toMillis(1),
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProDaysSinceSubscribedMatchingAttribute(min = 5, max = 10))
        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenSubscribeDaysIsOutOfMinAndMaxThenEvaluateReturnFalse() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(18))
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = TimeUnit.DAYS.toMillis(1),
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProDaysSinceSubscribedMatchingAttribute(min = 5, max = 10))
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }
}
