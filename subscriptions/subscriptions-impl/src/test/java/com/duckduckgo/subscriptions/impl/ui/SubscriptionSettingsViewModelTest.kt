package com.duckduckgo.subscriptions.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.Subscription
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.AutoRenewable
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToPortal
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Yearly
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SubscriptionSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private lateinit var viewModel: SubscriptionSettingsViewModel

    @Before
    fun before() {
        viewModel = SubscriptionSettingsViewModel(subscriptionsManager, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun whenRemoveFromDeviceThenFinishSignOut() = runTest {
        viewModel.commands().test {
            viewModel.removeFromDevice()
            assertTrue(awaitItem() is FinishSignOut)
        }
    }

    @Test
    fun whenSubscriptionThenFormatDateCorrectly() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription.Success(
                productId = SubscriptionsConstants.MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AutoRenewable,
                platform = "android",
            ),
        )

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertEquals("December 04, 2023", awaitItem().date)
        }
    }

    @Test
    fun whenSubscriptionMonthlyThenReturnMonthly() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription.Success(
                productId = SubscriptionsConstants.MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AutoRenewable,
                platform = "android",
            ),
        )

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertEquals(Monthly, awaitItem().duration)
        }
    }

    @Test
    fun whenSubscriptionYearlyThenReturnYearly() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription.Success(
                productId = SubscriptionsConstants.YEARLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AutoRenewable,
                platform = "android",
            ),
        )

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertEquals(Yearly, awaitItem().duration)
        }
    }

    @Test
    fun whenGoToStripeIfNoUrlThenDoNothing() = runTest {
        whenever(subscriptionsManager.getPortalUrl()).thenReturn(null)

        viewModel.commands().test {
            viewModel.goToStripe()
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGoToStripeIfNoUrlThenDoSendCommandWithUrl() = runTest {
        whenever(subscriptionsManager.getPortalUrl()).thenReturn("example.com")

        viewModel.commands().test {
            viewModel.goToStripe()
            val value = awaitItem() as GoToPortal
            assertEquals("example.com", value.url)
            cancelAndConsumeRemainingEvents()
        }
    }
}
