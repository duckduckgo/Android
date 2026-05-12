package com.duckduckgo.subscriptions.impl.feedback

import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.api.SubscriptionUnifiedFeedback.SubscriptionFeedbackSource.DDG_SETTINGS
import com.duckduckgo.subscriptions.api.SubscriptionUnifiedFeedback.SubscriptionFeedbackSource.SUBSCRIPTION_SETTINGS
import com.duckduckgo.subscriptions.api.SubscriptionUnifiedFeedback.SubscriptionFeedbackSource.VPN_EXCLUDED_APPS
import com.duckduckgo.subscriptions.api.SubscriptionUnifiedFeedback.SubscriptionFeedbackSource.VPN_MANAGEMENT
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealSubscriptionUnifiedFeedbackTest {
    @Mock
    private lateinit var subscriptions: Subscriptions
    private lateinit var testee: RealSubscriptionUnifiedFeedback
    private var subscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealSubscriptionUnifiedFeedback(
            subscriptions,
            subscriptionsFeature,
        )
    }

    @Test
    fun whenFeatureNotEnabledThenShouldUseUnifiedFeedbackFalse() = runTest {
        assertFalse(testee.shouldUseUnifiedFeedback(SUBSCRIPTION_SETTINGS))
    }

    @Test
    fun whenFeatureNotEnabledForSettingsThenShouldUseUnifiedFeedbackFalse() = runTest {
        assertFalse(testee.shouldUseUnifiedFeedback(DDG_SETTINGS))
    }

    @Test
    fun whenFeatureEnabledAndSourceIsSubscriptionThenShouldUseUnifiedFeedbackTrue() = runTest {
        subscriptionsFeature.useUnifiedFeedback().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.shouldUseUnifiedFeedback(SUBSCRIPTION_SETTINGS))
    }

    @Test
    fun whenFeatureEnabledAndSourceIsVPNThenShouldUseUnifiedFeedbackTrue() = runTest {
        subscriptionsFeature.useUnifiedFeedback().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.shouldUseUnifiedFeedback(VPN_MANAGEMENT))
    }

    @Test
    fun whenFeatureEnabledAndSourceIsVPNExclusionThenShouldUseUnifiedFeedbackTrue() = runTest {
        subscriptionsFeature.useUnifiedFeedback().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.shouldUseUnifiedFeedback(VPN_EXCLUDED_APPS))
    }

    @Test
    fun whenFeatureEnabledWithActiveSubsAndSourceIsSettingsThenShouldUseUnifiedFeedbackFalse() = runTest {
        subscriptionsFeature.useUnifiedFeedback().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        assertTrue(testee.shouldUseUnifiedFeedback(DDG_SETTINGS))
    }

    @Test
    fun whenFeatureEnabledWithInActiveSubsAndSourceIsSettingsThenShouldUseUnifiedFeedbackFalse() = runTest {
        subscriptionsFeature.useUnifiedFeedback().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(INACTIVE)
        assertFalse(testee.shouldUseUnifiedFeedback(DDG_SETTINGS))
    }

    @Test
    fun whenFeatureEnabledWithExpiredSubsAndSourceIsSettingsThenShouldUseUnifiedFeedbackFalse() = runTest {
        subscriptionsFeature.useUnifiedFeedback().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(EXPIRED)
        assertFalse(testee.shouldUseUnifiedFeedback(DDG_SETTINGS))
    }

    @Test
    fun whenFeatureEnabledWithWaitingSubsAndSourceIsSettingsThenShouldUseUnifiedFeedbackFalse() = runTest {
        subscriptionsFeature.useUnifiedFeedback().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(WAITING)
        assertFalse(testee.shouldUseUnifiedFeedback(DDG_SETTINGS))
    }
}
