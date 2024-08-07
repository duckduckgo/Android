package com.duckduckgo.subscriptions.impl.feedback

import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.DDG_SETTINGS
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.SUBSCRIPTION_SETTINGS
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_EXCLUDED_APPS
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_MANAGEMENT
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealPrivacyProUnifiedFeedbackTest {
    @Mock
    private lateinit var subscriptions: Subscriptions
    private lateinit var testee: RealPrivacyProUnifiedFeedback
    private var privacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealPrivacyProUnifiedFeedback(
            subscriptions,
            privacyProFeature,
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
    fun whenFeatureEnabledAndSourceIsPproThenShouldUseUnifiedFeedbackTrue() = runTest {
        privacyProFeature.useUnifiedFeedback().setEnabled(Toggle.State(enable = true))
        assertTrue(testee.shouldUseUnifiedFeedback(SUBSCRIPTION_SETTINGS))
    }

    @Test
    fun whenFeatureEnabledAndSourceIsVPNThenShouldUseUnifiedFeedbackTrue() = runTest {
        privacyProFeature.useUnifiedFeedback().setEnabled(Toggle.State(enable = true))
        assertTrue(testee.shouldUseUnifiedFeedback(VPN_MANAGEMENT))
    }

    @Test
    fun whenFeatureEnabledAndSourceIsVPNExclusionThenShouldUseUnifiedFeedbackTrue() = runTest {
        privacyProFeature.useUnifiedFeedback().setEnabled(Toggle.State(enable = true))
        assertTrue(testee.shouldUseUnifiedFeedback(VPN_EXCLUDED_APPS))
    }

    @Test
    fun whenFeatureEnabledWithActiveSubsAndSourceIsSettingsThenShouldUseUnifiedFeedbackFalse() = runTest {
        privacyProFeature.useUnifiedFeedback().setEnabled(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        assertTrue(testee.shouldUseUnifiedFeedback(DDG_SETTINGS))
    }

    @Test
    fun whenFeatureEnabledWithInActiveSubsAndSourceIsSettingsThenShouldUseUnifiedFeedbackFalse() = runTest {
        privacyProFeature.useUnifiedFeedback().setEnabled(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(INACTIVE)
        assertFalse(testee.shouldUseUnifiedFeedback(DDG_SETTINGS))
    }

    @Test
    fun whenFeatureEnabledWithExpiredSubsAndSourceIsSettingsThenShouldUseUnifiedFeedbackFalse() = runTest {
        privacyProFeature.useUnifiedFeedback().setEnabled(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(EXPIRED)
        assertFalse(testee.shouldUseUnifiedFeedback(DDG_SETTINGS))
    }

    @Test
    fun whenFeatureEnabledWithWaitingSubsAndSourceIsSettingsThenShouldUseUnifiedFeedbackFalse() = runTest {
        privacyProFeature.useUnifiedFeedback().setEnabled(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(WAITING)
        assertFalse(testee.shouldUseUnifiedFeedback(DDG_SETTINGS))
    }
}
