package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RMFPProBillingPeriodMatchingAttributeTest {
    @Mock
    private lateinit var subscriptionsManager: SubscriptionsManager

    private lateinit var matcher: RMFPProBillingPeriodMatchingAttribute

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        matcher = RMFPProBillingPeriodMatchingAttribute(subscriptionsManager)
    }

    @Test
    fun whenKeyIsNotBillingPeriodThenMappersMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = "Yearly")
        val result = matcher.map("somethingelse", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsBillingPeriodWithInvalidValueTypeThenMappersMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("Yearly"))
        val result = matcher.map("privacyProBillingPeriod", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsBillingPeriodWithEmptyValueThenMappersMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = "")
        val result = matcher.map("privacyProBillingPeriod", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsBillingPeriodWithNullValueThenMappersMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = null)
        val result = matcher.map("pproBillingPeriod", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsBillingPeriodValidThenMappersMapsToAttribute() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = "Yearly")
        val result = matcher.map("pproBillingPeriod", jsonMatchingAttribute)

        assertEquals(PProBillingPeriodMatchingAttribute(YEARLY), result)
    }

    @Test
    fun whenMatchingAttributeHasAnnualValueAndSubscriptionIsAnnualThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.YEARLY_PLAN_US,
                billingPeriod = YEARLY,
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "Google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProBillingPeriodMatchingAttribute("Yearly"))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasMonthlyValueAndSubscriptionIsMonthlyThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.YEARLY_PLAN_US,
                billingPeriod = MONTHLY,
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "Google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProBillingPeriodMatchingAttribute("Monthly"))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenMatchingAttributeHasMonthlyValueButSubscriptionIsAnnualThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.YEARLY_PLAN_US,
                billingPeriod = YEARLY,
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "Google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProBillingPeriodMatchingAttribute("monthly"))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenSubscriptionIsNullThenAnnualEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)
        val result = matcher.evaluate(PProBillingPeriodMatchingAttribute("yearly"))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeIsUnsupportedValueThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.YEARLY_PLAN_US,
                billingPeriod = MONTHLY,
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "Google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProBillingPeriodMatchingAttribute("quarterly"))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatcherHasEmptyValueThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.YEARLY_PLAN_US,
                billingPeriod = MONTHLY,
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "Google",
                activeOffers = listOf(),
            ),
        )
        val result = matcher.evaluate(PProBillingPeriodMatchingAttribute(value = ""))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }
}
