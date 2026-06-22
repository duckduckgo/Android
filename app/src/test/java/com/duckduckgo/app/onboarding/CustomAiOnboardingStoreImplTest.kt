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
    fun `when referrer contains onboarding ai and features enabled then resolves true`() = runTest {
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        assertTrue(store.resolve())
    }

    @Test
    fun `when referrer ai but custom ai feature disabled then resolves false`() = runTest {
        whenever(customDuckAiOnboardingFeature.self()).thenReturn(disabledToggle)
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        assertFalse(store.resolve())
    }

    @Test
    fun `when referrer ai but orchestrator disabled then resolves false`() = runTest {
        whenever(orchestratorFeature.self()).thenReturn(disabledToggle)
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        assertFalse(store.resolve())
    }

    @Test
    fun `when referrer ai but brand design disabled then resolves false`() = runTest {
        whenever(brandDesignUpdateToggles.brandDesignUpdate()).thenReturn(disabledToggle)
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        assertFalse(store.resolve())
    }

    @Test
    fun `when referrer has no onboarding param then resolves false`() = runTest {
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore"))
        assertFalse(store.resolve())
    }

    @Test
    fun `when onboarding value empty or unknown then resolves false`() = runTest {
        val store = store()
        store.process(mapOf("onboarding" to ""))
        assertFalse(store.resolve())
        store.process(mapOf("onboarding" to "somethingelse"))
        assertFalse(store.resolve())
    }

    @Test
    fun `when onboarding value wrong case then resolves false`() = runTest {
        val store = store()
        store.process(mapOf("onboarding" to "AI"))
        assertFalse(store.resolve())
    }

    @Test
    fun `when referrer never resolves then resolves false after timeout`() = runTest {
        // referrer never resolves -> resolve must not hang; returns false after the bounded wait
        assertFalse(store(neverResolvingListener).resolve())
    }

    @Test
    fun `when resolve not called then isEnabled returns false`() = runTest {
        // isEnabled is a pure read of the persisted decision; without resolve there is nothing to read
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        assertFalse(store.isEnabled())
    }

    @Test
    fun `when resolved true then isEnabled returns true`() = runTest {
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        store.resolve()
        assertTrue(store.isEnabled())
    }

    @Test
    fun `when resolved false then isEnabled returns false`() = runTest {
        val store = store()
        store.resolve()
        assertFalse(store.isEnabled())
    }

    @Test
    fun `when referral arrives after resolve then isEnabled stays frozen`() = runTest {
        // Reviewer scenario: the run is chosen once by resolve(). A late-arriving referral signal must
        // NOT flip isEnabled(), or end-of-journey CTAs would show custom-AI copy during the default plan.
        val store = store()
        assertFalse(store.resolve()) // referral not present at decision time -> default plan
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai")) // arrives too late
        assertFalse(store.isEnabled()) // frozen: still the default-plan decision
    }

    @Test
    fun `when precondition flips after resolve then isEnabled stays frozen`() = runTest {
        val store = store()
        store.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
        store.resolve()
        assertTrue(store.isEnabled())

        whenever(customDuckAiOnboardingFeature.self()).thenReturn(disabledToggle) // flips after the decision
        assertTrue(store.isEnabled()) // read does not re-evaluate preconditions
    }

    @Test
    fun `when resolved then decision persists across instances`() = runTest {
        store().also {
            it.process(mapOf("origin" to "funnel_playstore", "onboarding" to "ai"))
            it.resolve()
        }
        // a fresh instance backed by the same storage reads the persisted decision
        assertTrue(store().isEnabled())
    }

    @Test
    fun `when not armed then consume open input returns false`() {
        assertFalse(store().consumeOpenInputOnDuckAiTab())
    }

    @Test
    fun `when armed then consume open input returns true once and self clears`() {
        val store = store()
        store.setOpenInputOnDuckAiTab()

        assertTrue(store.consumeOpenInputOnDuckAiTab())
        assertFalse(store.consumeOpenInputOnDuckAiTab())
    }
}
