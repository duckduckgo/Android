package com.duckduckgo.subscriptions.impl.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.Account
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToActivationScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToEditEmailScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToPortal
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Yearly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.ViewState.Ready
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SubscriptionSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private val privacyProUnifiedFeedback: PrivacyProUnifiedFeedback = mock()

    private lateinit var viewModel: SubscriptionSettingsViewModel

    @Before
    fun before() {
        viewModel = SubscriptionSettingsViewModel(subscriptionsManager, pixelSender, privacyProUnifiedFeedback)
    }

    @Test
    fun whenRemoveFromDeviceThenFinishSignOut() = runTest {
        viewModel.commands().test {
            viewModel.removeFromDevice()
            assertTrue(awaitItem() is FinishSignOut)
        }
    }

    @Test
    fun whenUseUnifiedFeedbackThenViewStateShowFeeedbackTrue() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(true)
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
            ),
        )

        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = null, externalId = "external_id"),
        )

        val flowTest: MutableSharedFlow<SubscriptionStatus> = MutableSharedFlow()
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowTest)

        viewModel.onCreate(mock())
        flowTest.emit(AUTO_RENEWABLE)
        viewModel.viewState.test {
            assertTrue((awaitItem() as Ready).showFeedback)
        }
    }

    @Test
    fun whenSubscriptionThenFormatDateCorrectly() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
            ),
        )

        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = null, externalId = "external_id"),
        )

        val flowTest: MutableSharedFlow<SubscriptionStatus> = MutableSharedFlow()
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowTest)

        viewModel.onCreate(mock())
        flowTest.emit(AUTO_RENEWABLE)
        viewModel.viewState.test {
            assertEquals("December 04, 2023", (awaitItem() as Ready).date)
        }
    }

    @Test
    fun whenSubscriptionMonthlyThenReturnMonthly() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
            ),
        )

        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = null, externalId = "external_id"),
        )

        val flowTest: MutableSharedFlow<SubscriptionStatus> = MutableSharedFlow()
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowTest)

        viewModel.onCreate(mock())
        flowTest.emit(AUTO_RENEWABLE)
        viewModel.viewState.test {
            assertEquals(Monthly, (awaitItem() as Ready).duration)
        }
    }

    @Test
    fun whenSubscriptionYearlyThenReturnYearly() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.YEARLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
            ),
        )

        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = null, externalId = "external_id"),
        )

        val flowTest: MutableSharedFlow<SubscriptionStatus> = MutableSharedFlow()
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowTest)

        viewModel.onCreate(mock())
        flowTest.emit(AUTO_RENEWABLE)
        viewModel.viewState.test {
            assertEquals(Yearly, (awaitItem() as Ready).duration)
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

    @Test
    fun `when OnEditEmail button clicked then send GoToEditEmailScreen command`() = runTest {
        viewModel.commands().test {
            viewModel.onEditEmailButtonClicked()
            assertEquals(GoToEditEmailScreen, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when AddToDevice button clicked then send GoToActivationScreen command`() = runTest {
        viewModel.commands().test {
            viewModel.onAddToDeviceButtonClicked()
            assertEquals(GoToActivationScreen, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRemoveFromDeviceThenPixelIsSent() = runTest {
        viewModel.removeFromDevice()
        verify(pixelSender).reportSubscriptionSettingsRemoveFromDeviceClick()
    }
}
