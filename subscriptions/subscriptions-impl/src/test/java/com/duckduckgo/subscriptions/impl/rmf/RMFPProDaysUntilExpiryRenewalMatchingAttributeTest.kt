package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import java.util.concurrent.TimeUnit.DAYS
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RMFPProDaysUntilExpiryRenewalMatchingAttributeTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var subscriptionsManager: SubscriptionsManager

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    private lateinit var matcher: RMFPProDaysUntilExpiryRenewalMatchingAttribute

    private val testSubscription = Subscription(
        productId = "productId",
        billingPeriod = "Monthly",
        startedAt = DAYS.toMillis(1),
        expiresOrRenewsAt = DAYS.toMillis(10),
        status = AUTO_RENEWABLE,
        platform = "google",
        activeOffers = listOf(),
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        matcher = RMFPProDaysUntilExpiryRenewalMatchingAttribute(
            subscriptionsManager,
            currentTimeProvider,
        )
    }

    @Test
    fun whenMapKeyIsDaysTillExpiryRenewalWithMinMaxThenReturnMatchingAttribute() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute(min = 20, max = 30)
        val result = matcher.map("pproDaysUntilExpiryOrRenewal", jsonMatchingAttribute)
        assertEquals(PProDaysUntilExpiryRenewalMatchingAttribute(min = 20, max = 30), result)
    }

    @Test
    fun whenMapKeyIsDaysTillExpiryRenewalWithValueThenReturnMatchingAttribute() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = 30)
        val result = matcher.map("pproDaysUntilExpiryOrRenewal", jsonMatchingAttribute)
        assertEquals(PProDaysUntilExpiryRenewalMatchingAttribute(value = 30), result)
    }

    @Test
    fun whenMapKeyIsDaysTillExpiryRenewalWithEmptyValuesTheMapReturnAttribute() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute()
        val result = matcher.map("pproDaysUntilExpiryOrRenewal", jsonMatchingAttribute)
        assertEquals(PProDaysUntilExpiryRenewalMatchingAttribute(), result)
    }

    @Test
    fun whenMapKeyIsNotDaysTillExpiryRenewalTheMapReturnNull() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute(min = 20, max = 30)
        val result = matcher.map("somethingelse", jsonMatchingAttribute)
        assertNull(result)
    }

    @Test
    fun whenMapKeyIsDaysTillExpiryRenewalButValueIsInvalidTheMapReturnNull() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = "20")
        val result = matcher.map("pproDaysUntilExpiryOrRenewal", jsonMatchingAttribute)
        assertNull(result)
    }

    @Test
    fun whenMapKeyIsDaysTillExpiryRenewalButValueAreMixedWithInvalidTheMapReturnNull() = runTest {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf(20), min = 20, max = 24)
        val result = matcher.map("pproDaysUntilExpiryOrRenewal", jsonMatchingAttribute)
        assertNull(result)
    }

    @Test
    fun whenNoPropertiesSetOnMapperThenEvaluateReturnFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription)
        val result = matcher.evaluate(PProDaysUntilExpiryRenewalMatchingAttribute())
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenSubscriptionExpiredThenEvaluateReturnFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = EXPIRED))
        val result = matcher.evaluate(PProDaysUntilExpiryRenewalMatchingAttribute(min = 2, max = 20))
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenSubscriptionIsNullThenReturnEvaluateReturnsFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)
        val result = matcher.evaluate(PProDaysUntilExpiryRenewalMatchingAttribute(value = 10))
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenValueIsEqualToDaysTillExpiryRenewalThenEvaluateReturnTrue() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(DAYS.toMillis(2))
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription)
        val result = matcher.evaluate(PProDaysUntilExpiryRenewalMatchingAttribute(value = 8, max = 13, min = 10))
        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMaxIsGreaterThanDaysTillExpiryRenewalThenEvaluateReturnTrue() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(DAYS.toMillis(2))
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(expiresOrRenewsAt = DAYS.toMillis(10)))
        val result = matcher.evaluate(PProDaysUntilExpiryRenewalMatchingAttribute(max = 15))
        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMaxIsLessThanDaysTillExpiryRenewalThenEvaluateReturnFalse() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(DAYS.toMillis(2))
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(expiresOrRenewsAt = DAYS.toMillis(20)))
        val result = matcher.evaluate(PProDaysUntilExpiryRenewalMatchingAttribute(max = 5))
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMinIsLessThanDaysTillExpiryRenewalThenEvaluateReturnTrue() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(DAYS.toMillis(2))
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(expiresOrRenewsAt = DAYS.toMillis(10)))
        val result = matcher.evaluate(PProDaysUntilExpiryRenewalMatchingAttribute(min = 5))
        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMinIsGreaterThanDaysTillExpiryRenewalThenEvaluateReturnFalse() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(DAYS.toMillis(2))
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(expiresOrRenewsAt = DAYS.toMillis(4)))
        val result = matcher.evaluate(PProDaysUntilExpiryRenewalMatchingAttribute(min = 5))
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenDaysTillExpiryRenewalIsBetweenMinAndMaxThenEvaluateReturnTrue() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(DAYS.toMillis(2))
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(expiresOrRenewsAt = DAYS.toMillis(10)))
        val result = matcher.evaluate(PProDaysUntilExpiryRenewalMatchingAttribute(min = 5, max = 10))
        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenDaysTillExpiryRenewalIsOutOfMinAndMaxThenEvaluateReturnFalse() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(DAYS.toMillis(2))
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(expiresOrRenewsAt = DAYS.toMillis(20)))
        val result = matcher.evaluate(PProDaysUntilExpiryRenewalMatchingAttribute(min = 5, max = 10))
        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }
}
