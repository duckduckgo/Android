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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.settings.api.SerpSettingsDataProvider
import com.duckduckgo.settings.api.SerpSettingsFeature
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
import org.mockito.kotlin.whenever

class OnboardingPreferenceApplierImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val navigationHistory: NavigationHistory = mock()
    private val serpSettingsDataProvider: SerpSettingsDataProvider = mock()
    private val serpSettingsFeature: SerpSettingsFeature = FakeFeatureToggleFactory.create(SerpSettingsFeature::class.java)

    private val testee = OnboardingPreferenceApplierImpl(
        navigationHistory = navigationHistory,
        serpSettingsDataProvider = serpSettingsDataProvider,
        serpSettingsFeature = serpSettingsFeature,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Before
    fun setup() {
        serpSettingsFeature.storeSerpSettings().setRawStoredState(Toggle.State(enable = true))
    }

    @Test
    fun whenHistoryFeatureUnavailableThenSearchHistoryIsNotAvailable() = runTest {
        whenever(navigationHistory.isHistoryFeatureAvailable()).thenReturn(false)

        assertFalse(testee.isAvailable(OnboardingPreference.SEARCH_HISTORY))
    }

    @Test
    fun whenHistoryFeatureAvailableThenSearchHistoryIsAvailable() = runTest {
        whenever(navigationHistory.isHistoryFeatureAvailable()).thenReturn(true)

        assertTrue(testee.isAvailable(OnboardingPreference.SEARCH_HISTORY))
    }

    @Test
    fun whenSerpSettingsStorageEnabledThenSafeSearchIsAvailable() = runTest {
        serpSettingsFeature.storeSerpSettings().setRawStoredState(Toggle.State(enable = true))

        assertTrue(testee.isAvailable(OnboardingPreference.SAFE_SEARCH))
    }

    @Test
    fun whenSerpSettingsStorageDisabledThenSafeSearchIsNotAvailable() = runTest {
        serpSettingsFeature.storeSerpSettings().setRawStoredState(Toggle.State(enable = false))

        assertFalse(testee.isAvailable(OnboardingPreference.SAFE_SEARCH))
    }

    @Test
    fun whenSearchHistoryEnabledQueriedThenReadsUserSetting() = runTest {
        whenever(navigationHistory.isHistoryUserEnabled()).thenReturn(true)

        assertTrue(testee.isEnabled(OnboardingPreference.SEARCH_HISTORY))
    }

    @Test
    fun whenKpNotStoredThenSafeSearchDefaultsToEnabled() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf(null))

        assertTrue(testee.isEnabled(OnboardingPreference.SAFE_SEARCH))
    }

    @Test
    fun whenKpIsOffThenSafeSearchIsDisabled() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf("-2"))

        assertFalse(testee.isEnabled(OnboardingPreference.SAFE_SEARCH))
    }

    @Test
    fun whenKpIsOnThenSafeSearchIsEnabled() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf("-1"))

        assertTrue(testee.isEnabled(OnboardingPreference.SAFE_SEARCH))
    }

    @Test
    fun whenSettingsFlowCompletesEmptyThenSafeSearchDefaultsToEnabled() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(emptyFlow())

        assertTrue(testee.isEnabled(OnboardingPreference.SAFE_SEARCH))
    }

    @Test
    fun whenSettingsFlowNeverEmitsThenSafeSearchDefaultsToEnabled() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flow { awaitCancellation() })

        assertTrue(testee.isEnabled(OnboardingPreference.SAFE_SEARCH))
    }

    @Test
    fun whenSearchHistoryAppliedThenUserSettingWritten() = runTest {
        testee.apply(OnboardingPreference.SEARCH_HISTORY, enabled = false)

        verify(navigationHistory).setHistoryUserEnabled(false)
    }

    @Test
    fun whenSafeSearchEnabledThenKpSetToOn() = runTest {
        testee.apply(OnboardingPreference.SAFE_SEARCH, enabled = true)

        verify(serpSettingsDataProvider).setSetting("kp", "-1")
    }

    @Test
    fun whenSafeSearchDisabledThenKpSetToOff() = runTest {
        testee.apply(OnboardingPreference.SAFE_SEARCH, enabled = false)

        verify(serpSettingsDataProvider).setSetting("kp", "-2")
    }

    @Test
    fun whenPreferencesEnumeratedThenSearchHistoryComesFirst() {
        assertEquals(
            listOf(OnboardingPreference.SEARCH_HISTORY, OnboardingPreference.SAFE_SEARCH),
            OnboardingPreference.entries,
        )
    }
}
