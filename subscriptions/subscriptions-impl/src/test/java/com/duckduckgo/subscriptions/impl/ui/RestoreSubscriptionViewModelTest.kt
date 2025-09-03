package com.duckduckgo.subscriptions.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.SubscriptionRebrandingFeatureToggle
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.Companion.SUBSCRIPTION_NOT_FOUND_ERROR
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.RecoverSubscriptionResult
import com.duckduckgo.subscriptions.impl.SubscriptionsChecker
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.auth2.AuthClient
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Error
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.FinishAndGoToOnboarding
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.FinishAndGoToSubscriptionSettings
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.RestoreFromEmail
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.SubscriptionNotFound
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Success
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RestoreSubscriptionViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private val subscriptionsChecker: SubscriptionsChecker = mock()
    private val authClient: AuthClient = mock()
    private val mockSubscriptionRebrandingFeatureToggle: SubscriptionRebrandingFeatureToggle = mock()
    private lateinit var viewModel: RestoreSubscriptionViewModel

    @Before
    fun before() {
        viewModel = RestoreSubscriptionViewModel(
            subscriptionsManager = subscriptionsManager,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            pixelSender = pixelSender,
            subscriptionsChecker = subscriptionsChecker,
            authClient = authClient,
            appCoroutineScope = coroutineTestRule.testScope,
            subscriptionRebrandingFeatureToggle = mockSubscriptionRebrandingFeatureToggle,
        )
    }

    @Test
    fun whenRestoreFromEmailThenSendCommand() = runTest {
        viewModel.commands().test {
            viewModel.restoreFromEmail()
            assertTrue(awaitItem() is RestoreFromEmail)
        }
    }

    @Test
    fun whenRestoreFromStoreIfFailureThenReturnError() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            RecoverSubscriptionResult.Failure("error"),
        )
        givenSubscriptionStatus(UNKNOWN)
        viewModel.init()

        viewModel.commands().test {
            viewModel.restoreFromStore()
            val result = awaitItem()
            assertTrue(result is Error)
        }
    }

    @Test
    fun whenRestoreFromStoreIfNoSubscriptionFoundThenReturnNotFound() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            RecoverSubscriptionResult.Failure(SUBSCRIPTION_NOT_FOUND_ERROR),
        )

        givenSubscriptionStatus(UNKNOWN)

        viewModel.init()

        viewModel.commands().test {
            viewModel.restoreFromStore()
            val result = awaitItem()
            assertTrue(result is SubscriptionNotFound)
        }
    }

    @Test
    fun whenRestoreFromStoreThenReturnSuccess() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            RecoverSubscriptionResult.Success(subscriptionActive()),
        )

        viewModel.commands().test {
            viewModel.restoreFromStore()
            val result = awaitItem()
            assertTrue(result is Success)
        }
    }

    @Test
    fun whenRestoreFromStoreClickThenPixelIsSent() = runTest {
        viewModel.restoreFromStore()
        verify(pixelSender).reportActivateSubscriptionRestorePurchaseClick()
    }

    @Test
    fun whenRestoreFromEmailClickThenPixelIsSent() = runTest {
        viewModel.restoreFromEmail()
        verify(pixelSender).reportActivateSubscriptionEnterEmailClick()
    }

    @Test
    fun whenRestoreFromStoreSuccessThenPixelIsSent() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            RecoverSubscriptionResult.Success(subscriptionActive()),
        )

        viewModel.restoreFromStore()
        verify(pixelSender).reportRestoreUsingStoreSuccess()
    }

    @Test
    fun whenRestoreFromStoreFailsBecauseThereIsNoSubscriptionThenPixelIsSent() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            RecoverSubscriptionResult.Failure(SUBSCRIPTION_NOT_FOUND_ERROR),
        )
        givenSubscriptionStatus(UNKNOWN)
        viewModel.init()

        viewModel.restoreFromStore()
        verify(pixelSender).reportRestoreUsingStoreFailureSubscriptionNotFound()
    }

    @Test
    fun whenRestoreFromStoreFailsForOtherReasonThenPixelIsSent() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            RecoverSubscriptionResult.Failure("bad stuff happened"),
        )
        givenSubscriptionStatus(UNKNOWN)
        viewModel.init()

        viewModel.restoreFromStore()
        verify(pixelSender).reportRestoreUsingStoreFailureOther()
    }

    @Test
    fun whenOnSubscriptionRestoredFromEmailAndSubscriptionExpiredThenCommandIsSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(EXPIRED))

        viewModel.init()

        viewModel.commands().test {
            viewModel.onSubscriptionRestoredFromEmail()
            val result = awaitItem()
            assertTrue(result is FinishAndGoToSubscriptionSettings)
        }
    }

    @Test
    fun whenOnSubscriptionRestoredFromEmailAndSubscriptionActiveThenCommandIsSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(AUTO_RENEWABLE))

        viewModel.init()

        viewModel.commands().test {
            viewModel.onSubscriptionRestoredFromEmail()
            val result = awaitItem()
            assertTrue(result is FinishAndGoToOnboarding)
        }
    }

    @Test
    fun whenRestoreFromStoreFailsBecauseOfExpiredSubscriptionThenSignsUserOut() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            RecoverSubscriptionResult.Failure(SUBSCRIPTION_NOT_FOUND_ERROR),
        )
        givenSubscriptionStatus(EXPIRED)
        viewModel.init()

        viewModel.commands().test {
            viewModel.restoreFromStore()
            val result = awaitItem()
            assertTrue(result is SubscriptionNotFound)
            verify(subscriptionsManager).signOut()
        }
    }

    @Test
    fun whenRestoreFromEmailThenJwksCacheIsWarmedUp() = runTest {
        viewModel.restoreFromEmail()
        verify(authClient).getJwks()
    }

    @Test
    fun whenWarmUpJwksFailsThenNoCrashOccurs() = runTest {
        whenever(authClient.getJwks()).thenThrow(RuntimeException("Network error"))

        viewModel.restoreFromEmail()

        viewModel.commands().test {
            assertTrue(awaitItem() is RestoreFromEmail)
        }
        verify(pixelSender).reportActivateSubscriptionEnterEmailClick()
    }

    private fun subscriptionActive(): Subscription {
        return Subscription(
            productId = "productId",
            billingPeriod = "Monthly",
            startedAt = 10000L,
            expiresOrRenewsAt = 10000L,
            status = AUTO_RENEWABLE,
            platform = "google",
            activeOffers = listOf(),
        )
    }

    private fun givenSubscriptionStatus(subscriptionStatus: SubscriptionStatus) = runBlocking {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(subscriptionStatus)
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(subscriptionStatus))
    }
}
