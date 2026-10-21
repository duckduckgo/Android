package com.duckduckgo.subscriptions.impl.ui

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.ActiveOfferType
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionUnifiedFeedback
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionTier
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingProgress
import com.duckduckgo.subscriptions.impl.onboarding.experiment.SubscriptionOnboardingExperiments
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.Account
import com.duckduckgo.subscriptions.impl.repository.PendingPlan
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStore
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToActivationScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToEditEmailScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToPortal
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.ShowWithdrawalDialog
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.ShowWithdrawalError
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Yearly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.ViewState.Ready
import com.duckduckgo.subscriptions.impl.withdrawal.SubscriptionWithdrawal
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalEligibility
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SubscriptionSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private val subscriptionUnifiedFeedback: SubscriptionUnifiedFeedback = mock()
    private val subscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java, FakeToggleStore())
    private val onboardingProgress: SubscriptionOnboardingProgress = mock()
    private val onboardingStore: SubscriptionOnboardingStore = mock()
    private val subscriptionOnboardingExperiments: SubscriptionOnboardingExperiments = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val subscriptionWithdrawal: SubscriptionWithdrawal = mock()

    private lateinit var viewModel: SubscriptionSettingsViewModel

    @Before
    fun before() {
        runBlocking { whenever(subscriptionOnboardingExperiments.isTreatment()).thenReturn(false) }
        viewModel = SubscriptionSettingsViewModel(
            subscriptionsManager,
            pixelSender,
            subscriptionUnifiedFeedback,
            subscriptionsFeature,
            onboardingProgress,
            onboardingStore,
            subscriptionOnboardingExperiments,
            currentTimeProvider,
            subscriptionWithdrawal,
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
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(true)
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
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
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
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
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
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
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
    fun whenProTierEnabledThenViewStateReflectsIt() = runTest {
        subscriptionsFeature.allowProTierPurchase().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
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
        subscriptionsFeature.allowProTierPurchase().setRawStoredState(Toggle.State(enable = false))
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
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
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
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
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
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
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)

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
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)

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
            assertEquals(R.string.planNamePlusYearly, state.pendingPlanDisplayNameResId)
        }
    }

    @Test
    fun whenPendingPlanIsTierUpgradeThenIsPendingDowngradeIsFalse() = runTest {
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)

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
            assertEquals(R.string.planNameProYearly, state.pendingPlanDisplayNameResId)
        }
    }

    @Test
    fun whenPendingPlanIsBillingPeriodDowngradeThenIsPendingDowngradeIsTrue() = runTest {
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)

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
            assertEquals(R.string.planNamePlusMonthly, state.pendingPlanDisplayNameResId)
        }
    }

    @Test
    fun whenPendingPlanIsBillingPeriodUpgradeThenIsPendingDowngradeIsFalse() = runTest {
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)

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
            assertEquals(R.string.planNamePlusYearly, state.pendingPlanDisplayNameResId)
        }
    }

    @Test
    fun whenNoPendingPlanThenIsPendingDowngradeIsNull() = runTest {
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)

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
            assertEquals(null, state.pendingPlanDisplayNameResId)
        }
    }

    @Test
    fun whenSubscriptionHasNoPendingPlanThenViewStateHasNullPendingPlan() = runTest {
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
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
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)

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

    @Test
    fun whenOnboardingFeatureDisabledThenNoOnboardingEntryPoint() = runTest {
        stubReadySubscription()

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertEquals(null, (awaitItem() as Ready).onboardingEntryPoint)
        }
    }

    @Test
    fun whenOnboardingIncompleteAndWithinPurchaseWindowThenCardShownWithPercentage() = runTest {
        whenever(subscriptionOnboardingExperiments.isTreatment()).thenReturn(true)
        stubReadySubscription(startedAt = 1_000L)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1_000L)
        whenever(onboardingProgress.completionPercentage()).thenReturn(50)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertEquals(50, (awaitItem() as Ready).onboardingEntryPoint?.percentage)
        }
    }

    @Test
    fun whenOnboardingIncompleteAndPastPurchaseWindowThenNoCard() = runTest {
        whenever(subscriptionOnboardingExperiments.isTreatment()).thenReturn(true)
        stubReadySubscription(startedAt = 0L)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(FIFTEEN_DAYS_MILLIS)
        whenever(onboardingProgress.completionPercentage()).thenReturn(50)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertEquals(null, (awaitItem() as Ready).onboardingEntryPoint)
        }
    }

    @Test
    fun whenOnboardingCompleteAndUnderViewCapThenCardShown() = runTest {
        whenever(subscriptionOnboardingExperiments.isTreatment()).thenReturn(true)
        stubReadySubscription()
        whenever(onboardingProgress.completionPercentage()).thenReturn(100)
        whenever(onboardingStore.completedEntryPointViews()).thenReturn(0)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertEquals(100, (awaitItem() as Ready).onboardingEntryPoint?.percentage)
        }
    }

    @Test
    fun whenOnboardingCompleteAndViewCapReachedThenNoCard() = runTest {
        whenever(subscriptionOnboardingExperiments.isTreatment()).thenReturn(true)
        stubReadySubscription()
        whenever(onboardingProgress.completionPercentage()).thenReturn(100)
        whenever(onboardingStore.completedEntryPointViews()).thenReturn(2)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertEquals(null, (awaitItem() as Ready).onboardingEntryPoint)
        }
    }

    @Test
    fun whenOnboardingCompleteAndOneViewUsedUnderCapThenCardShown() = runTest {
        whenever(subscriptionOnboardingExperiments.isTreatment()).thenReturn(true)
        stubReadySubscription()
        whenever(onboardingProgress.completionPercentage()).thenReturn(100)
        // One view used, still under the cap → keep showing (even across app launches).
        whenever(onboardingStore.completedEntryPointViews()).thenReturn(1)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertEquals(100, (awaitItem() as Ready).onboardingEntryPoint?.percentage)
        }
    }

    @Test
    fun whenCompletedCardShownThenViewIsCountedOnceAcrossResumeAndStatusEmission() = runTest {
        whenever(subscriptionOnboardingExperiments.isTreatment()).thenReturn(true)
        stubReadySubscription()
        whenever(onboardingProgress.completionPercentage()).thenReturn(100)

        var views = 1
        whenever(onboardingStore.completedEntryPointViews()).thenAnswer { views }
        whenever(onboardingStore.incrementCompletedEntryPointViews()).thenAnswer {
            views++
            Unit
        }

        val status = MutableSharedFlow<SubscriptionStatus>()
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(status)

        viewModel.onCreate(mock())
        viewModel.onResume(mock())
        assertEquals(100, (viewModel.viewState.value as Ready).onboardingEntryPoint?.percentage)

        status.emit(AUTO_RENEWABLE)

        assertEquals(100, (viewModel.viewState.value as Ready).onboardingEntryPoint?.percentage)
        assertEquals(2, views)
    }

    @Test
    fun whenSubscriptionInactiveThenNoOnboardingEntryPoint() = runTest {
        whenever(subscriptionOnboardingExperiments.isTreatment()).thenReturn(true)
        stubReadySubscription(status = EXPIRED)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertEquals(null, (awaitItem() as Ready).onboardingEntryPoint)
        }
    }

    @Test
    fun whenContinueSetupClickedThenLaunchOnboarding() = runTest {
        viewModel.commands().test {
            viewModel.onContinueSetupClicked()
            assertTrue(awaitItem() is SubscriptionSettingsViewModel.Command.LaunchOnboarding)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenStripeSubscriptionEligibleForWithdrawalThenShowWithdrawalOption() = runTest {
        subscriptionsFeature.subscriptionWithdrawal().setRawStoredState(Toggle.State(remoteEnableState = true))
        whenever(subscriptionWithdrawal.getEligibility()).thenReturn(ELIGIBILITY)
        stubReadySubscription(platform = "stripe")

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            val withdrawal = (expectMostRecentItem() as Ready).withdrawal
            assertNotNull(withdrawal)
            assertEquals("October 15, 2026", withdrawal!!.deadline)
            assertEquals(R.string.planTierNamePlus, withdrawal.tierNameResId)
        }
    }

    @Test
    fun whenStripeSubscriptionNotEligibleForWithdrawalThenHideWithdrawalOption() = runTest {
        subscriptionsFeature.subscriptionWithdrawal().setRawStoredState(Toggle.State(remoteEnableState = true))
        whenever(subscriptionWithdrawal.getEligibility()).thenReturn(null)
        stubReadySubscription(platform = "stripe")

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertNull((expectMostRecentItem() as Ready).withdrawal)
        }
    }

    @Test
    fun whenWithdrawalFlagDisabledThenDoNotCheckEligibility() = runTest {
        subscriptionsFeature.subscriptionWithdrawal().setRawStoredState(Toggle.State(remoteEnableState = false))
        stubReadySubscription(platform = "stripe")

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertNull((expectMostRecentItem() as Ready).withdrawal)
        }
        verify(subscriptionWithdrawal, never()).getEligibility()
    }

    @Test
    fun whenNotStripeSubscriptionThenDoNotCheckEligibility() = runTest {
        subscriptionsFeature.subscriptionWithdrawal().setRawStoredState(Toggle.State(remoteEnableState = true))
        stubReadySubscription(platform = "android")

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertNull((expectMostRecentItem() as Ready).withdrawal)
        }
        verify(subscriptionWithdrawal, never()).getEligibility()
    }

    @Test
    fun whenStripeFreeTrialThenDoNotCheckEligibility() = runTest {
        subscriptionsFeature.subscriptionWithdrawal().setRawStoredState(Toggle.State(remoteEnableState = true))
        stubReadySubscription(platform = "stripe", activeOffers = listOf(ActiveOfferType.TRIAL))

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertNull((expectMostRecentItem() as Ready).withdrawal)
        }
        verify(subscriptionWithdrawal, never()).getEligibility()
    }

    @Test
    fun whenStripeSubscriptionExpiredThenDoNotCheckEligibility() = runTest {
        subscriptionsFeature.subscriptionWithdrawal().setRawStoredState(Toggle.State(remoteEnableState = true))
        stubReadySubscription(platform = "stripe", status = EXPIRED)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertNull((expectMostRecentItem() as Ready).withdrawal)
        }
        verify(subscriptionWithdrawal, never()).getEligibility()
    }

    @Test
    fun whenWithdrawClickedThenShowWithdrawalDialog() = runTest {
        subscriptionsFeature.subscriptionWithdrawal().setRawStoredState(Toggle.State(remoteEnableState = true))
        whenever(subscriptionWithdrawal.getEligibility()).thenReturn(ELIGIBILITY)
        stubReadySubscription(platform = "stripe")
        viewModel.onCreate(mock())
        viewModel.viewState.test { expectMostRecentItem() }

        viewModel.commands().test {
            viewModel.onWithdrawClicked()
            val command = awaitItem() as ShowWithdrawalDialog
            assertEquals(R.string.planTierNamePlus, command.tierNameResId)
            assertTrue(command.refundAmount.contains("9.99"))
        }
    }

    @Test
    fun whenWithdrawClickedWithoutWithdrawalOptionThenNoCommand() = runTest {
        stubReadySubscription(platform = "stripe")
        viewModel.onCreate(mock())
        viewModel.viewState.test { expectMostRecentItem() }

        viewModel.commands().test {
            viewModel.onWithdrawClicked()
            expectNoEvents()
        }
    }

    @Test
    fun whenWithdrawalConfirmedAndSucceedsThenHideWithdrawalOption() = runTest {
        subscriptionsFeature.subscriptionWithdrawal().setRawStoredState(Toggle.State(remoteEnableState = true))
        whenever(subscriptionWithdrawal.getEligibility()).thenReturn(ELIGIBILITY)
        whenever(subscriptionWithdrawal.withdraw()).thenReturn(WithdrawalResult.Success(BigDecimal("9.99"), "EUR"))
        stubReadySubscription(platform = "stripe")
        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertNotNull((expectMostRecentItem() as Ready).withdrawal)
            viewModel.onWithdrawalConfirmed()
            assertNull((expectMostRecentItem() as Ready).withdrawal)
        }
        verify(subscriptionWithdrawal).withdraw()
    }

    @Test
    fun whenWithdrawalConfirmedAndFailsThenShowWithdrawalError() = runTest {
        whenever(subscriptionWithdrawal.withdraw()).thenReturn(WithdrawalResult.Failure(WithdrawalResult.FailureReason.UNKNOWN))

        viewModel.commands().test {
            viewModel.onWithdrawalConfirmed()
            assertEquals(ShowWithdrawalError, awaitItem())
        }
    }

    private suspend fun stubReadySubscription(
        startedAt: Long = 1234,
        status: SubscriptionStatus = AUTO_RENEWABLE,
        platform: String = "android",
        activeOffers: List<ActiveOfferType> = listOf(),
    ) {
        whenever(subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(any())).thenReturn(false)
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = startedAt,
                expiresOrRenewsAt = 1701694623000,
                status = status,
                platform = platform,
                activeOffers = activeOffers,
            ),
        )
        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = null, externalId = "external_id"),
        )
        val flowTest: MutableSharedFlow<SubscriptionStatus> = MutableSharedFlow(replay = 1)
        flowTest.tryEmit(status)
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowTest)
    }

    private companion object {
        private const val FIFTEEN_DAYS_MILLIS = 15L * 24 * 60 * 60 * 1000

        // Midday UTC so the formatted date is the same in any test machine timezone
        private val ELIGIBILITY = WithdrawalEligibility(
            eligibleUntil = 1792065600000,
            refundAmount = BigDecimal("9.99"),
            refundCurrency = "EUR",
        )
    }
}
