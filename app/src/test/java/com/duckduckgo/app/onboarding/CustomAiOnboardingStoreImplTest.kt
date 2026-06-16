/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.referral.api.AppInstallationReferrerStateListener
import com.duckduckgo.referral.api.ParsedReferrerResult
import dagger.Lazy
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CustomAiOnboardingStoreImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val preferences = InMemorySharedPreferences()
    private val sharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn preferences
    }

    private val enabledToggle: Toggle = mock { on { isEnabled() } doReturn true }
    private val disabledToggle: Toggle = mock { on { isEnabled() } doReturn false }
    private val customDuckAiOnboardingFeature: CustomDuckAiOnboardingFeature = mock()
    private val orchestratorFeature: LinearOnboardingOrchestratorFeature = mock()
    private val brandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles = mock()

    private val resolvedListener = object : AppInstallationReferrerStateListener {
        override fun initialiseReferralRetrieval() {}
        override suspend fun waitForReferrerCode(): ParsedReferrerResult = ParsedReferrerResult.ReferrerNotFound
    }
    private val neverResolvingListener = object : AppInstallationReferrerStateListener {
        override fun initialiseReferralRetrieval() {}
        override suspend fun waitForReferrerCode(): ParsedReferrerResult = awaitCancellation()
    }

    private fun store(listener: AppInstallationReferrerStateListener = resolvedListener) =
        CustomAiOnboardingStoreImpl(
            sharedPreferencesProvider = sharedPreferencesProvider,
            referrerStateListener = Lazy { listener },
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            customDuckAiOnboardingFeature = customDuckAiOnboardingFeature,
            orchestratorFeature = orchestratorFeature,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )

    @Before
    fun setup() {
        whenever(customDuckAiOnboardingFeature.self()).thenReturn(enabledToggle)
        whenever(orchestratorFeature.self()).thenReturn(enabledToggle)
        whenever(brandDesignUpdateToggles.brandDesignUpdate()).thenReturn(enabledToggle)
    }

    @Test
    fun whenReferrerContainsOnboardingAiAndFeaturesEnabledThenEnabled() = runTest {
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        assertTrue(store.isEnabled())
    }

    @Test
    fun whenReferrerAiButFeatureDisabledThenNotEnabled() = runTest {
        whenever(customDuckAiOnboardingFeature.self()).thenReturn(disabledToggle)
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        assertFalse(store.isEnabled())
    }

    @Test
    fun whenReferrerAiButOrchestratorDisabledThenNotEnabled() = runTest {
        whenever(orchestratorFeature.self()).thenReturn(disabledToggle)
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        assertFalse(store.isEnabled())
    }

    @Test
    fun whenReferrerAiButBrandDesignDisabledThenNotEnabled() = runTest {
        whenever(brandDesignUpdateToggles.brandDesignUpdate()).thenReturn(disabledToggle)
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        assertFalse(store.isEnabled())
    }

    @Test
    fun whenReferrerHasNoOnboardingParamThenNotEnabled() = runTest {
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore"))
        assertFalse(store.isEnabled())
    }

    @Test
    fun whenOnboardingValueEmptyOrUnknownThenNotEnabled() = runTest {
        val store = store()
        store.process(mapOf("onboarding" to ""))
        assertFalse(store.isEnabled())
        store.process(mapOf("onboarding" to "somethingelse"))
        assertFalse(store.isEnabled())
    }

    @Test
    fun whenOnboardingValueWrongCaseThenNotEnabled() = runTest {
        val store = store()
        store.process(mapOf("onboarding" to "AI"))
        assertFalse(store.isEnabled())
    }

    @Test
    fun whenReferrerNeverResolvesThenNotEnabledAfterTimeout() = runTest {
        // referrer never resolves -> isEnabled must not hang; returns false (standard) after the bounded wait
        assertFalse(store(neverResolvingListener).isEnabled())
    }

    @Test
    fun whenNotArmedThenConsumeOpenInputReturnsFalse() {
        assertFalse(store().consumeOpenInputOnDuckAiTab())
    }

    @Test
    fun whenArmedThenConsumeOpenInputReturnsTrueOnceAndSelfClears() {
        val store = store()
        store.setOpenInputOnDuckAiTab()

        assertTrue(store.consumeOpenInputOnDuckAiTab())
        assertFalse(store.consumeOpenInputOnDuckAiTab())
    }
}
