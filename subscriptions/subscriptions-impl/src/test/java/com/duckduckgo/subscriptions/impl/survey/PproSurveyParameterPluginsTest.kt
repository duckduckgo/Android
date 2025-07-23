package com.duckduckgo.subscriptions.impl.survey

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class PproSurveyParameterPluginTest {
    @Mock
    private lateinit var subscriptionsManager: SubscriptionsManager

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    private val testSubscription = Subscription(
        productId = SubscriptionsConstants.MONTHLY_PLAN_US,
        billingPeriod = MONTHLY,
        startedAt = 1717797600000, // June 07 UTC
        expiresOrRenewsAt = 1719525600000, // June 27 UTC
        status = AUTO_RENEWABLE,
        platform = "android",
        activeOffers = listOf(),
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun whenSubscriptionIsAvailableThenPlatformParamEvaluatesToSubscriptionPlatform() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription)

        val plugin = PproPurchasePlatformSurveyParameterPlugin(subscriptionsManager)

        assertEquals("android", plugin.evaluate("ppro_platform"))
    }

    @Test
    fun whenSubscriptionIsNotAvailableThenPlatformParamEvaluatesToEmpty() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)

        val plugin = PproPurchasePlatformSurveyParameterPlugin(subscriptionsManager)

        assertEquals("", plugin.evaluate("ppro_platform"))
    }

    @Test
    fun whenSubscriptionIsMonthlyThenBillingParamEvaluatesToSubscriptionBilling() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription)

        val plugin = PproBillingParameterPlugin(subscriptionsManager)

        assertEquals("Monthly", plugin.evaluate("ppro_billing"))
    }

    @Test
    fun whenSubscriptionIsYearlyThenBillingParamEvaluatesToSubscriptionBilling() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = YEARLY,
                startedAt = 1717797600000, // June 07 UTC
                expiresOrRenewsAt = 1719525600000, // June 27 UTC
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
            ),
        )

        val plugin = PproBillingParameterPlugin(subscriptionsManager)

        assertEquals("Yearly", plugin.evaluate("ppro_billing"))
    }

    @Test
    fun whenSubscriptionIsNotAvailableThenBillingParamEvaluatesToEmpty() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)

        val plugin = PproBillingParameterPlugin(subscriptionsManager)

        assertEquals("", plugin.evaluate("ppro_billing"))
    }

    @Test
    fun whenSubscriptionIsAvailableThenDaysSincePurchaseParamEvaluatesToData() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1718723702145L) // June 18 UTC

        val plugin = PproDaysSincePurchaseSurveyParameterPlugin(subscriptionsManager, currentTimeProvider)

        assertEquals("10", plugin.evaluate("ppro_days_since_purchase"))
    }

    @Test
    fun whenSubscriptionIsNotAvailableThenDaysSincePurchaseParamEvaluatesToZero() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1718920800000) // June 20 UTC

        val plugin = PproDaysSincePurchaseSurveyParameterPlugin(subscriptionsManager, currentTimeProvider)

        assertEquals("0", plugin.evaluate("ppro_days_since_purchase"))
    }

    @Test
    fun whenSubscriptionIsAvailableThenDaysUntilExpiryParamEvaluatesToData() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1718723702145L) // June 18 UTC

        val plugin = PproDaysUntilExpirySurveyParameterPlugin(subscriptionsManager, currentTimeProvider)

        assertEquals("9", plugin.evaluate("ppro_days_until_exp"))
    }

    @Test
    fun whenSubscriptionIsNotAvailableThenDaysUntilExpiryParamEvaluatesToZero() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)

        val plugin = PproDaysUntilExpirySurveyParameterPlugin(subscriptionsManager, currentTimeProvider)

        assertEquals("0", plugin.evaluate("ppro_days_until_exp"))
    }

    @Test
    fun whenSubscriptionStatusIsAutoRenewableThenStatusParamEvaluatesToAutoRenewable() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription)

        val plugin = PproStatusParameterPlugin(subscriptionsManager)

        assertEquals("auto_renewable", plugin.evaluate("ppro_status"))
    }

    @Test
    fun whenSubscriptionIsInactiveThenStatusParamEvaluatesToSubscriptionData() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = INACTIVE))

        val plugin = PproStatusParameterPlugin(subscriptionsManager)

        assertEquals("inactive", plugin.evaluate("ppro_status"))
    }

    @Test
    fun whenSubscriptionIsNotAutoRenewableThenStatusParamEvaluatesToSubscriptionData() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = NOT_AUTO_RENEWABLE))

        val plugin = PproStatusParameterPlugin(subscriptionsManager)

        assertEquals("not_auto_renewable", plugin.evaluate("ppro_status"))
    }

    @Test
    fun whenSubscriptionIsGracePeriodThenStatusParamEvaluatesToSubscriptionData() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(testSubscription.copy(status = GRACE_PERIOD))

        val plugin = PproStatusParameterPlugin(subscriptionsManager)

        assertEquals("grace_period", plugin.evaluate("ppro_status"))
    }

    @Test
    fun whenSubscriptionIsNotAvailableThenStatusParamEvaluatesToEmpty() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)

        val plugin = PproStatusParameterPlugin(subscriptionsManager)

        assertEquals("", plugin.evaluate("ppro_status"))
    }
}
