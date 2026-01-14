package com.duckduckgo.subscriptions.impl.ui

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.SubscriptionTier
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.Account
import com.duckduckgo.subscriptions.impl.repository.PendingPlan
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToActivationScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToEditEmailScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToPortal
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.ShowSwitchPlanDialog
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Yearly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SwitchPlanType
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.ViewState.Ready
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SubscriptionSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private val privacyProUnifiedFeedback: PrivacyProUnifiedFeedback = mock()
    private val privacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java, FakeToggleStore())

    private lateinit var viewModel: SubscriptionSettingsViewModel

    @Before
    fun before() {
        viewModel = SubscriptionSettingsViewModel(
            subscriptionsManager,
            pixelSender,
            privacyProUnifiedFeedback,
            privacyProFeature,
        )
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
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)
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
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)
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
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)
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
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)
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

    @Test
    fun whenSwitchPlanAvailableThenViewStateIncludesIt() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(true)
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
            assertTrue((awaitItem() as Ready).switchPlanAvailable)
        }
    }

    @Test
    fun whenSwitchPlanNotAvailableThenViewStateReflectsIt() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)
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
            assertFalse((awaitItem() as Ready).switchPlanAvailable)
        }
    }

    @Test
    fun whenOnSwitchPlanClickedWithMonthlyThenEmitUpgradeCommand() = runTest {
        viewModel.commands().test {
            viewModel.onSwitchPlanClicked(Monthly)

            val command = awaitItem()
            assertTrue(command is ShowSwitchPlanDialog)
            assertEquals(SwitchPlanType.UPGRADE_TO_YEARLY, (command as ShowSwitchPlanDialog).switchType)
        }
    }

    @Test
    fun whenOnSwitchPlanClickedWithYearlyThenEmitDowngradeCommand() = runTest {
        viewModel.commands().test {
            viewModel.onSwitchPlanClicked(Yearly)

            val command = awaitItem()
            assertTrue(command is ShowSwitchPlanDialog)
            assertEquals(SwitchPlanType.DOWNGRADE_TO_MONTHLY, (command as ShowSwitchPlanDialog).switchType)
        }
    }

    @Test
    fun whenOnSwitchPlanSuccessThenRefreshSubscriptionData() = runTest {
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(true)
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = listOf(),
            ),
        )
        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = "test@example.com", externalId = "external_id"),
        )

        val flowTest: MutableSharedFlow<SubscriptionStatus> = MutableSharedFlow()
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowTest)

        viewModel.onCreate(mock())
        flowTest.emit(AUTO_RENEWABLE)

        viewModel.viewState.test {
            val initialState = awaitItem() as Ready
            assertEquals(Monthly, initialState.duration)

            // Simulate plan switch success - subscription changed to yearly
            whenever(subscriptionsManager.getSubscription()).thenReturn(
                Subscription(
                    productId = SubscriptionsConstants.YEARLY_PLAN_US,
                    billingPeriod = "Yearly",
                    startedAt = 1234,
                    expiresOrRenewsAt = 1701694623000,
                    status = AUTO_RENEWABLE,
                    platform = "google",
                    activeOffers = listOf(),
                ),
            )

            viewModel.onSwitchPlanSuccess()

            val updatedState = awaitItem() as Ready
            assertEquals(Yearly, updatedState.duration)
        }
    }

    @Test
    fun whenProTierEnabledThenViewStateReflectsIt() = runTest {
        privacyProFeature.allowProTierPurchase().setRawStoredState(Toggle.State(enable = true))
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)
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
            assertTrue((awaitItem() as Ready).isProTierEnabled)
        }
    }

    @Test
    fun whenProTierDisabledThenViewStateReflectsIt() = runTest {
        privacyProFeature.allowProTierPurchase().setRawStoredState(Toggle.State(enable = false))
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)
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
            assertFalse((awaitItem() as Ready).isProTierEnabled)
        }
    }

    @Test
    fun whenSubscriptionIsPlusTierThenViewStateReflectsIt() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)
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
            assertEquals(SubscriptionTier.PLUS, (awaitItem() as Ready).subscriptionTier)
        }
    }

    @Test
    fun whenSubscriptionIsProTierThenViewStateReflectsIt() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PRO_PLAN_US,
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
            assertEquals(SubscriptionTier.PRO, (awaitItem() as Ready).subscriptionTier)
        }
    }

    @Test
    fun whenSubscriptionHasPendingPlanThenViewStateIncludesPendingPlan() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)

        val pendingPlan = PendingPlan(
            productId = "ddg-privacy-pro-yearly-renews-us",
            billingPeriod = "yearly",
            effectiveAt = 1701694623000,
            status = "scheduled",
            tier = SubscriptionTier.PLUS,
        )

        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PRO_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
                pendingPlans = listOf(pendingPlan),
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
            val state = awaitItem() as Ready
            assertEquals(pendingPlan, state.pendingPlan)
            assertEquals("December 04, 2023", state.pendingEffectiveDate)
            assertEquals(SubscriptionTier.PLUS, state.effectiveTier)
        }
    }

    @Test
    fun whenPendingPlanIsTierDowngradeThenIsPendingDowngradeIsTrue() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)

        val pendingPlan = PendingPlan(
            productId = "ddg-privacy-pro-yearly-renews-us",
            billingPeriod = "yearly",
            effectiveAt = 1701694623000,
            status = "scheduled",
            tier = SubscriptionTier.PLUS,
        )

        // Current subscription is PRO, pending is PLUS - this is a downgrade
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PRO_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
                pendingPlans = listOf(pendingPlan),
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
            val state = awaitItem() as Ready
            assertEquals(true, state.isPendingDowngrade)
            assertEquals("Plus Yearly", state.pendingPlanDisplayName)
        }
    }

    @Test
    fun whenPendingPlanIsTierUpgradeThenIsPendingDowngradeIsFalse() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)

        val pendingPlan = PendingPlan(
            productId = SubscriptionsConstants.MONTHLY_PRO_PLAN_US,
            billingPeriod = "yearly",
            effectiveAt = 1701694623000,
            status = "scheduled",
            tier = SubscriptionTier.PRO,
        )

        // Current subscription is PLUS, pending is PRO - this is an upgrade
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
                pendingPlans = listOf(pendingPlan),
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
            val state = awaitItem() as Ready
            assertEquals(false, state.isPendingDowngrade)
            assertEquals("Pro Yearly", state.pendingPlanDisplayName)
        }
    }

    @Test
    fun whenPendingPlanIsBillingPeriodDowngradeThenIsPendingDowngradeIsTrue() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)

        val pendingPlan = PendingPlan(
            productId = "ddg-privacy-pro-monthly-renews-us",
            billingPeriod = "monthly",
            effectiveAt = 1701694623000,
            status = "scheduled",
            tier = SubscriptionTier.PLUS,
        )

        // Current subscription is Yearly PLUS, pending is Monthly PLUS - this is a downgrade
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.YEARLY_PLAN_US,
                billingPeriod = "Yearly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
                pendingPlans = listOf(pendingPlan),
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
            val state = awaitItem() as Ready
            assertEquals(true, state.isPendingDowngrade)
            assertEquals("Plus Monthly", state.pendingPlanDisplayName)
        }
    }

    @Test
    fun whenPendingPlanIsBillingPeriodUpgradeThenIsPendingDowngradeIsFalse() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)

        val pendingPlan = PendingPlan(
            productId = SubscriptionsConstants.YEARLY_PLAN_US,
            billingPeriod = "yearly",
            effectiveAt = 1701694623000,
            status = "scheduled",
            tier = SubscriptionTier.PLUS,
        )

        // Current subscription is Monthly PLUS, pending is Yearly PLUS - this is an upgrade
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
                pendingPlans = listOf(pendingPlan),
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
            val state = awaitItem() as Ready
            assertEquals(false, state.isPendingDowngrade)
            assertEquals("Plus Yearly", state.pendingPlanDisplayName)
        }
    }

    @Test
    fun whenNoPendingPlanThenIsPendingDowngradeIsNull() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)

        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
                pendingPlans = emptyList(),
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
            val state = awaitItem() as Ready
            assertEquals(null, state.isPendingDowngrade)
            assertEquals(null, state.pendingPlanDisplayName)
        }
    }

    @Test
    fun whenSubscriptionHasNoPendingPlanThenViewStateHasNullPendingPlan() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
                pendingPlans = emptyList(),
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
            val state = awaitItem() as Ready
            assertEquals(null, state.pendingPlan)
            assertEquals(null, state.pendingEffectiveDate)
            assertEquals(SubscriptionTier.PLUS, state.effectiveTier)
        }
    }

    @Test
    fun whenSubscriptionHasMultiplePendingPlansThenViewStateUsesFirst() = runTest {
        whenever(privacyProUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.isSwitchPlanAvailable()).thenReturn(false)

        val firstPendingPlan = PendingPlan(
            productId = SubscriptionsConstants.YEARLY_PLAN_US,
            billingPeriod = "yearly",
            effectiveAt = 1701694623000,
            status = "scheduled",
            tier = SubscriptionTier.PLUS,
        )
        val secondPendingPlan = PendingPlan(
            productId = SubscriptionsConstants.MONTHLY_PLAN_US,
            billingPeriod = "monthly",
            effectiveAt = 1702000000000,
            status = "pending",
            tier = SubscriptionTier.PRO,
        )

        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1701694623000,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = listOf(),
                pendingPlans = listOf(firstPendingPlan, secondPendingPlan),
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
            val state = awaitItem() as Ready
            // Should use the first pending plan
            assertEquals(firstPendingPlan, state.pendingPlan)
            assertEquals(SubscriptionTier.PLUS, state.effectiveTier)
        }
    }
}
