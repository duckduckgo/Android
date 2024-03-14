package com.duckduckgo.subscriptions.impl.settings.views

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.settings.views.SettingsStateProvider.SettingsState.Empty
import com.duckduckgo.subscriptions.impl.settings.views.SettingsStateProvider.SettingsState.SubscriptionActivationInProgress
import com.duckduckgo.subscriptions.impl.settings.views.SettingsStateProvider.SettingsState.SubscriptionActive
import com.duckduckgo.subscriptions.impl.settings.views.SettingsStateProvider.SettingsState.SubscriptionOfferAvailable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SettingsStateProviderTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()

    private val subject = RealSettingsStateProvider(
        coroutineScope = coroutineTestRule.testScope,
        subscriptionsManager = subscriptionsManager,
    )

    @Test
    fun `when subscription active then returns SubscriptionActive`() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))

        val settingsState = subject.getSettingsState().first()

        assertEquals(SubscriptionActive, settingsState)
    }

    @Test
    fun `when subscription expired and offer available then returns SubscriptionOfferAvailable`() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(SubscriptionStatus.EXPIRED))
        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(mock())

        val settingsState = subject.getSettingsState().first()

        assertEquals(SubscriptionOfferAvailable, settingsState)
    }

    @Test
    fun `when subscription expired and offer not available then returns Empty`() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(SubscriptionStatus.EXPIRED))
        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(null)

        val settingsState = subject.getSettingsState().first()

        assertEquals(Empty, settingsState)
    }

    @Test
    fun `when subscription waiting for activation then returns SubscriptionActivationInProgress`() = runTest {
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowOf(SubscriptionStatus.WAITING))

        val settingsState = subject.getSettingsState().first()

        assertEquals(SubscriptionActivationInProgress, settingsState)
    }
}
