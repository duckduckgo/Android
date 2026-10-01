package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionTier
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.PendingPlan
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

class RMFSubscriptionTierMatchingAttributeTest {
    @Mock
    private lateinit var subscriptionsManager: SubscriptionsManager

    private lateinit var matcher: RMFSubscriptionTierMatchingAttribute
    private val plusSubscription = Subscription(
        productId = SubscriptionsConstants.MONTHLY_PLAN_US,
        billingPeriod = MONTHLY,
        startedAt = 10000L,
        expiresOrRenewsAt = 10000L,
        status = AUTO_RENEWABLE,
        platform = "Google",
        activeOffers = listOf(),
    )
    private val proSubscription = plusSubscription.copy(productId = SubscriptionsConstants.MONTHLY_PRO_PLAN_US)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        matcher = RMFSubscriptionTierMatchingAttribute(subscriptionsManager = subscriptionsManager)
    }

    @Test
    fun whenKeyIsNotPProSubscriptionTierThenMapperMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("plus"))
        val result = matcher.map(key = "somethingelse", jsonMatchingAttribute = jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsPProSubscriptionTierThenMapperMapsToSubscriptionTier() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("plus"))
        val result = matcher.map(key = "pproSubscriptionTier", jsonMatchingAttribute = jsonMatchingAttribute)

        assertEquals(PProSubscriptionTierMatchingAttribute(tiers = listOf("plus")), result)
    }

    @Test
    fun whenKeyIsPProSubscriptionTierWithEmptyTiersThenMapperMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = emptyList<String>())
        val result = matcher.map(key = "pproSubscriptionTier", jsonMatchingAttribute = jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsPProSubscriptionTierWithNullTiersThenMapperMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = null)
        val result = matcher.map(key = "pproSubscriptionTier", jsonMatchingAttribute = jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenKeyIsPProSubscriptionTierWithInvalidValueTypeThenMapperMapsToNull() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = "plus")
        val result = matcher.map(key = "pproSubscriptionTier", jsonMatchingAttribute = jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun whenPlusSubscriptionAndTiersIncludePlusThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(plusSubscription)
        val result = matcher.evaluate(matchingAttribute = PProSubscriptionTierMatchingAttribute(tiers = listOf("plus")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenProSubscriptionAndTiersIncludeOnlyPlusThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(proSubscription)
        val result = matcher.evaluate(matchingAttribute = PProSubscriptionTierMatchingAttribute(tiers = listOf("plus")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenProSubscriptionAndTiersIncludeProThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(proSubscription)
        val result = matcher.evaluate(matchingAttribute = PProSubscriptionTierMatchingAttribute(tiers = listOf("pro")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenPlusSubscriptionAndTiersIncludeOnlyProThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(plusSubscription)
        val result = matcher.evaluate(matchingAttribute = PProSubscriptionTierMatchingAttribute(tiers = listOf("pro")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenPlusSubscriptionAndTiersIncludePlusAndProThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(plusSubscription)
        val result = matcher.evaluate(matchingAttribute = PProSubscriptionTierMatchingAttribute(tiers = listOf("plus", "pro")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenProSubscriptionAndTiersIncludePlusAndProThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(proSubscription)
        val result = matcher.evaluate(matchingAttribute = PProSubscriptionTierMatchingAttribute(tiers = listOf("plus", "pro")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenTierDiffersOnlyInCaseThenEvaluateToTrue() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(plusSubscription)
        val result = matcher.evaluate(matchingAttribute = PProSubscriptionTierMatchingAttribute(tiers = listOf("PLUS")))

        assertNotNull(result)
        result?.let {
            assertTrue(it)
        }
    }

    @Test
    fun whenProSubscriptionHasPendingDowngradeToPlusAndTiersIncludeOnlyPlusThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            proSubscription.copy(
                pendingPlans = listOf(
                    PendingPlan(
                        productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                        billingPeriod = MONTHLY,
                        effectiveAt = 20000L,
                        status = "scheduled",
                        tier = SubscriptionTier.PLUS,
                    ),
                ),
            ),
        )
        val result = matcher.evaluate(matchingAttribute = PProSubscriptionTierMatchingAttribute(tiers = listOf("plus")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenSubscriptionIsNullThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)
        val result = matcher.evaluate(matchingAttribute = PProSubscriptionTierMatchingAttribute(tiers = listOf("plus", "pro")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenSubscriptionTierIsUnknownThenEvaluateToFalse() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(plusSubscription.copy(productId = "unknown-plan-id"))
        val result = matcher.evaluate(matchingAttribute = PProSubscriptionTierMatchingAttribute(tiers = listOf("plus", "pro", "unknown")))

        assertNotNull(result)
        result?.let {
            assertFalse(it)
        }
    }

    @Test
    fun whenMatchingAttributeIsNotPProSubscriptionTierThenEvaluateToNull() = runTest {
        val result = matcher.evaluate(matchingAttribute = FakeStringMatchingAttribute { "" })

        assertNull(result)
    }
}
