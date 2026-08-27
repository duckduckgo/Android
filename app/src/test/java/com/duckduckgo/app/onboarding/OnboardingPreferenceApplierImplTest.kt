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

import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin
import com.duckduckgo.settings.api.SerpSettingsDataProvider
import com.duckduckgo.settings.api.SerpSettingsFeature
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private val autoconsent: Autoconsent = mock()
    private val serpSettingsDataProvider: SerpSettingsDataProvider = mock()
    private val serpSettingsFeature: SerpSettingsFeature = FakeFeatureToggleFactory.create(SerpSettingsFeature::class.java)
    private val adBlockingPlugin = FakeOnboardingBooleanPreferencePlugin()
    private var contributedPlugins: List<OnboardingBooleanPreferencePlugin> = listOf(adBlockingPlugin)
    private val booleanPreferencePlugins = object : ActivePluginPoint<OnboardingBooleanPreferencePlugin> {
        override suspend fun getPlugins() = contributedPlugins
    }

    private val testee = OnboardingPreferenceApplierImpl(
        navigationHistory = navigationHistory,
        autoconsent = autoconsent,
        serpSettingsDataProvider = serpSettingsDataProvider,
        serpSettingsFeature = serpSettingsFeature,
        booleanPreferencePlugins = booleanPreferencePlugins,
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
    fun whenSerpSettingsStorageEnabledThenSearchAssistIsAvailable() = runTest {
        serpSettingsFeature.storeSerpSettings().setRawStoredState(Toggle.State(enable = true))

        assertTrue(testee.isAvailable(OnboardingPreference.SEARCH_ASSIST))
    }

    @Test
    fun whenSerpSettingsStorageDisabledThenSearchAssistIsNotAvailable() = runTest {
        serpSettingsFeature.storeSerpSettings().setRawStoredState(Toggle.State(enable = false))

        assertFalse(testee.isAvailable(OnboardingPreference.SEARCH_ASSIST))
    }

    @Test
    fun whenSerpSettingsStorageEnabledThenHideAiGeneratedImagesIsAvailable() = runTest {
        serpSettingsFeature.storeSerpSettings().setRawStoredState(Toggle.State(enable = true))

        assertTrue(testee.isAvailable(OnboardingPreference.HIDE_AI_GENERATED_IMAGES))
    }

    @Test
    fun whenSerpSettingsStorageDisabledThenHideAiGeneratedImagesIsNotAvailable() = runTest {
        serpSettingsFeature.storeSerpSettings().setRawStoredState(Toggle.State(enable = false))

        assertFalse(testee.isAvailable(OnboardingPreference.HIDE_AI_GENERATED_IMAGES))
    }

    @Test
    fun whenSearchAssistAlreadyOnThenOnboardingStillSeedsItOff() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kbe")).thenReturn(flowOf("3"))

        assertFalse(testee.isEnabled(OnboardingPreference.SEARCH_ASSIST))
    }

    @Test
    fun whenHideAiGeneratedImagesAlreadyOffThenOnboardingStillSeedsItOn() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kbj")).thenReturn(flowOf("-1"))

        assertTrue(testee.isEnabled(OnboardingPreference.HIDE_AI_GENERATED_IMAGES))
    }

    @Test
    fun whenSearchAssistEnabledThenKbeSetToSometimes() = runTest {
        testee.apply(OnboardingPreference.SEARCH_ASSIST, enabled = true)

        verify(serpSettingsDataProvider).setSetting("kbe", "2")
    }

    @Test
    fun whenSearchAssistDisabledThenKbeSetToNever() = runTest {
        testee.apply(OnboardingPreference.SEARCH_ASSIST, enabled = false)

        verify(serpSettingsDataProvider).setSetting("kbe", "0")
    }

    @Test
    fun whenHideAiGeneratedImagesEnabledThenKbjSetToOn() = runTest {
        testee.apply(OnboardingPreference.HIDE_AI_GENERATED_IMAGES, enabled = true)

        verify(serpSettingsDataProvider).setSetting("kbj", "1")
    }

    @Test
    fun whenHideAiGeneratedImagesDisabledThenKbjSetToOff() = runTest {
        testee.apply(OnboardingPreference.HIDE_AI_GENERATED_IMAGES, enabled = false)

        verify(serpSettingsDataProvider).setSetting("kbj", "-1")
    }

    @Test
    fun whenPreferencesEnumeratedThenBlockAdsComesFirst() {
        assertEquals(
            listOf(
                OnboardingPreference.BLOCK_ADS,
                OnboardingPreference.SEARCH_HISTORY,
                OnboardingPreference.SAFE_SEARCH,
                OnboardingPreference.SEARCH_ASSIST,
                OnboardingPreference.HIDE_AI_GENERATED_IMAGES,
                OnboardingPreference.REJECT_OPTIONAL_COOKIES,
                OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES,
            ),
            OnboardingPreference.entries,
        )
    }

    @Test
    fun whenCookiePreferencesOfferedThenBothAreAvailable() = runTest {
        assertTrue(testee.isAvailable(OnboardingPreference.REJECT_OPTIONAL_COOKIES))
        assertTrue(testee.isAvailable(OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES))
    }

    @Test
    fun whenAutoconsentSettingOnThenRejectOptionalCookiesSeededOn() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)

        assertTrue(testee.isEnabled(OnboardingPreference.REJECT_OPTIONAL_COOKIES))
    }

    @Test
    fun whenAutoconsentSettingOffThenRejectOptionalCookiesSeededOff() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)

        assertFalse(testee.isEnabled(OnboardingPreference.REJECT_OPTIONAL_COOKIES))
    }

    @Test
    fun whenClickAcceptOnThenAcceptNonOptOutCookiesSeededOn() = runTest {
        whenever(autoconsent.isClickAcceptEnabled()).thenReturn(true)

        assertTrue(testee.isEnabled(OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES))
    }

    @Test
    fun whenClickAcceptOffThenAcceptNonOptOutCookiesSeededOff() = runTest {
        whenever(autoconsent.isClickAcceptEnabled()).thenReturn(false)

        assertFalse(testee.isEnabled(OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES))
    }

    @Test
    fun whenRejectOptionalCookiesAppliedThenAutoconsentSettingWritten() = runTest {
        testee.apply(OnboardingPreference.REJECT_OPTIONAL_COOKIES, enabled = true)

        verify(autoconsent).changeSetting(true)
    }

    @Test
    fun whenAcceptNonOptOutCookiesAppliedThenClickAcceptWritten() = runTest {
        testee.apply(OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES, enabled = false)

        verify(autoconsent).changeClickAcceptEnabled(false)
    }

    @Test
    fun whenAdBlockingPluginContributedThenBlockAdsIsAvailable() = runTest {
        assertTrue(testee.isAvailable(OnboardingPreference.BLOCK_ADS))
    }

    @Test
    fun whenAdBlockingPluginMissingThenBlockAdsIsNotAvailable() = runTest {
        contributedPlugins = emptyList()

        assertFalse(testee.isAvailable(OnboardingPreference.BLOCK_ADS))
    }

    @Test
    fun whenBlockAdsOfferedThenItIsSeededOnToSteerTheUser() = runTest {
        assertTrue(testee.isEnabled(OnboardingPreference.BLOCK_ADS))
    }

    @Test
    fun whenBlockAdsAppliedThenThePickReachesTheAdBlockingPlugin() = runTest {
        testee.apply(OnboardingPreference.BLOCK_ADS, enabled = true)

        assertEquals(listOf(true), adBlockingPlugin.applied)
    }

    @Test
    fun whenAdBlockingPluginMissingThenApplyingBlockAdsIsANoOp() = runTest {
        contributedPlugins = emptyList()

        testee.apply(OnboardingPreference.BLOCK_ADS, enabled = true)

        assertEquals(emptyList<Boolean>(), adBlockingPlugin.applied)
    }

    @Test
    fun whenBlockAdsPresentationQueriedThenCopyAndIconComeFromPlugin() = runTest {
        assertEquals(
            OnboardingPreferencePresentation(primaryText = "Block ads", secondaryText = null, iconRes = 42),
            testee.presentation(OnboardingPreference.BLOCK_ADS),
        )
    }

    @Test
    fun whenAdBlockingPluginMissingThenBlockAdsHasNoPresentation() = runTest {
        contributedPlugins = emptyList()

        assertNull(testee.presentation(OnboardingPreference.BLOCK_ADS))
    }

    @Test
    fun whenPreferenceIsNamedByOnboardingThenItHasNoPresentation() = runTest {
        assertNull(testee.presentation(OnboardingPreference.SEARCH_HISTORY))
    }
}

private class FakeOnboardingBooleanPreferencePlugin(
    override val id: OnboardingBooleanPreferencePlugin.Id = OnboardingBooleanPreferencePlugin.Id.AdBlocking,
    override val primaryText: String = "Block ads",
    override val secondaryText: String? = null,
    override val iconRes: Int = 42,
) : OnboardingBooleanPreferencePlugin {

    val applied = mutableListOf<Boolean>()

    override suspend fun apply(enabled: Boolean) {
        applied += enabled
    }
}
