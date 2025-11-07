package com.duckduckgo.subscriptions.impl.settings.views

import android.annotation.SuppressLint
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.SubscriptionOffer
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenBuyScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenRestoreScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class ProSettingViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private lateinit var viewModel: ProSettingViewModel
    private val privacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java)

    @Before
    fun before() {
        viewModel = ProSettingViewModel(
            subscriptionsManager,
            pixelSender,
            privacyProFeature,
            coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenOnSettingsThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.onSettings()
            assertTrue(awaitItem() is OpenSettings)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnBuyThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.onBuy()
            assertTrue(awaitItem() is OpenBuyScreen)
            verify(pixelSender).reportAppSettingsGetSubscriptionClick()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnRestoreThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.onRestore()
            assertTrue(awaitItem() is OpenRestoreScreen)
            verify(pixelSender).reportAppSettingsRestorePurchaseClick()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnResumeEmitViewState() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(SubscriptionStatus.EXPIRED))
        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(emptyList())
        whenever(subscriptionsManager.isFreeTrialEligible()).thenReturn(false)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertEquals(SubscriptionStatus.EXPIRED, awaitItem().status)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnRestoreThenPixelSent() = runTest {
        viewModel.commands().test {
            viewModel.onRestore()
            verify(pixelSender).reportAppSettingsRestorePurchaseClick()
            verifyNoMoreInteractions(pixelSender)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun givenFreeTrialEligibleWhenOnCreateThenFreeTrialEligibleViewStateTrue() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(SubscriptionStatus.INACTIVE))
        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(emptyList())
        whenever(subscriptionsManager.isFreeTrialEligible()).thenReturn(true)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertEquals(true, awaitItem().freeTrialEligible)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiPlusEnabledIfSubscriptionPlanHasDuckAiThenDuckAiPlusAvailable() = runTest {
        privacyProFeature.duckAiPlus().setRawStoredState(State(true))
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(listOf(subscriptionOffer.copy(features = setOf(Product.DuckAiPlus.value))))
        whenever(subscriptionsManager.isFreeTrialEligible()).thenReturn(true)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertTrue(awaitItem().duckAiPlusAvailable)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiPlusEnabledIfSubscriptionPlanDoesNotHaveDuckAiThenDuckAiPlusAvailable() = runTest {
        privacyProFeature.duckAiPlus().setRawStoredState(State(true))
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(listOf(subscriptionOffer.copy(features = setOf(Product.NetP.value))))
        whenever(subscriptionsManager.isFreeTrialEligible()).thenReturn(true)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertFalse(awaitItem().duckAiPlusAvailable)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiPlusDisabledIfSubscriptionPlanHasDuckAiThenDuckAiPlusAvailableFalse() = runTest {
        privacyProFeature.duckAiPlus().setRawStoredState(State(false))
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(listOf(subscriptionOffer.copy(features = setOf(Product.DuckAiPlus.value))))
        whenever(subscriptionsManager.isFreeTrialEligible()).thenReturn(true)

        viewModel.onCreate(mock())
        viewModel.viewState.test {
            assertFalse(awaitItem().duckAiPlusAvailable)
            cancelAndConsumeRemainingEvents()
        }
    }

    private val subscriptionOffer = SubscriptionOffer(
        planId = "test",
        offerId = null,
        pricingPhases = emptyList(),
        features = emptySet(),
    )
}
