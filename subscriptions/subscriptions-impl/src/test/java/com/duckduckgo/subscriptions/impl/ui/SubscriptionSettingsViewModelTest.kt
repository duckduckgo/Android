package com.duckduckgo.subscriptions.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.*
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.Account
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToAddEmailScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToEditEmailScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToPortal
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Yearly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.ViewState.Ready
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private val feature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java)
    private lateinit var viewModel: SubscriptionSettingsViewModel

    @Before
    fun before() {
        viewModel = SubscriptionSettingsViewModel(subscriptionsManager, pixelSender, feature)
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
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                entitlements = emptyList(),
            ),
        )

        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = null, externalId = "external_id"),
        )

        val flowTest: MutableSharedFlow<SubscriptionStatus> = MutableSharedFlow()
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowTest)
        feature.useUnifiedFeedback().setEnabled(Toggle.State(enable = true))

        viewModel.onCreate(mock())
        flowTest.emit(AUTO_RENEWABLE)
        viewModel.viewState.test {
            assertTrue((awaitItem() as Ready).showFeedback)
        }
    }

    @Test
    fun whenSubscriptionThenFormatDateCorrectly() = runTest {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                entitlements = emptyList(),
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
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                entitlements = emptyList(),
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
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.YEARLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                entitlements = emptyList(),
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
    fun whenOnEmailButtonClickedAndEmailNotPresentThenSendGoToAddEmailScreenCommand() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(AUTO_RENEWABLE))

        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                entitlements = emptyList(),
            ),
        )

        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = null, externalId = "external_id"),
        )

        viewModel.onCreate(mock())

        viewModel.commands().test {
            viewModel.onEmailButtonClicked()
            assertEquals(GoToAddEmailScreen, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEmailButtonClickedAndEmailNotPresentThenSendGoToEditEmailScreenCommand() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(AUTO_RENEWABLE))

        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                entitlements = emptyList(),
            ),
        )

        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = "test@example.com", externalId = "external_id"),
        )

        viewModel.onCreate(mock())

        viewModel.commands().test {
            viewModel.onEmailButtonClicked()
            assertEquals(GoToEditEmailScreen, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEmailButtonClickedThenPixelIsSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(AUTO_RENEWABLE))

        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                entitlements = emptyList(),
            ),
        )

        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = "test@example.com", externalId = "external_id"),
        )

        viewModel.onCreate(mock())

        viewModel.commands().test {
            viewModel.onEmailButtonClicked()
            verify(pixelSender).reportAddDeviceEnterEmailClick()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRemoveFromDeviceThenPixelIsSent() = runTest {
        viewModel.removeFromDevice()
        verify(pixelSender).reportSubscriptionSettingsRemoveFromDeviceClick()
    }
}
