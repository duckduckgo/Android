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

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig.PreferenceSelector.Row
import com.duckduckgo.app.onboarding.ui.page.configdriven.TextConfig
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import com.duckduckgo.mobile.android.R as CommonR

class OnboardingPreferenceCatalogImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val navigationHistory: NavigationHistory = mock()
    private val autoconsent: Autoconsent = mock()
    private val serpSettingsDataProvider: SerpSettingsDataProvider = mock()
    private val serpSettingsFeature: SerpSettingsFeature = FakeFeatureToggleFactory.create(SerpSettingsFeature::class.java)
    private val adBlockingPlugin = FakeOnboardingBooleanPreferencePlugin()
    private var contributedPlugins: List<OnboardingBooleanPreferencePlugin> = listOf(adBlockingPlugin)
    private var pluginLookups = 0
    private val booleanPreferencePlugins = object : ActivePluginPoint<OnboardingBooleanPreferencePlugin> {
        override suspend fun getPlugins(): List<OnboardingBooleanPreferencePlugin> {
            pluginLookups++
            return contributedPlugins
        }
    }

    private val testee = OnboardingPreferenceCatalogImpl(
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

    private suspend fun rowFor(preference: OnboardingPreference): Row? = testee.offer(listOf(preference)).singleOrNull()

    // region availability

    @Test
    fun whenHistoryFeatureUnavailableThenSearchHistoryIsNotOffered() = runTest {
        whenever(navigationHistory.isHistoryFeatureAvailable()).thenReturn(false)

        assertNull(rowFor(OnboardingPreference.SEARCH_HISTORY))
    }

    @Test
    fun whenHistoryFeatureAvailableThenSearchHistoryIsOffered() = runTest {
        whenever(navigationHistory.isHistoryFeatureAvailable()).thenReturn(true)
        whenever(navigationHistory.isHistoryUserEnabled()).thenReturn(false)

        assertEquals(
            Row(
                preference = OnboardingPreference.SEARCH_HISTORY,
                iconRes = CommonR.drawable.history_color_24,
                primaryText = TextConfig.Resource(R.string.searchPathPreferenceHistoryPrimary),
                secondaryText = TextConfig.Resource(R.string.searchPathPreferenceHistorySecondary),
                initiallyEnabled = false,
            ),
            rowFor(OnboardingPreference.SEARCH_HISTORY),
        )
    }

    @Test
    fun whenSerpSettingsStorageEnabledThenSerpBackedPreferencesAreOffered() = runTest {
        serpSettingsFeature.storeSerpSettings().setRawStoredState(Toggle.State(enable = true))
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf(null))

        val offered = testee.offer(
            listOf(
                OnboardingPreference.SAFE_SEARCH,
                OnboardingPreference.SEARCH_ASSIST,
                OnboardingPreference.HIDE_AI_GENERATED_IMAGES,
            ),
        )

        assertEquals(
            listOf(
                OnboardingPreference.SAFE_SEARCH,
                OnboardingPreference.SEARCH_ASSIST,
                OnboardingPreference.HIDE_AI_GENERATED_IMAGES,
            ),
            offered.map { it.preference },
        )
    }

    @Test
    fun whenSerpSettingsStorageDisabledThenSerpBackedPreferencesAreNotOffered() = runTest {
        serpSettingsFeature.storeSerpSettings().setRawStoredState(Toggle.State(enable = false))

        assertEquals(
            emptyList<Row>(),
            testee.offer(
                listOf(
                    OnboardingPreference.SAFE_SEARCH,
                    OnboardingPreference.SEARCH_ASSIST,
                    OnboardingPreference.HIDE_AI_GENERATED_IMAGES,
                ),
            ),
        )
    }

    @Test
    fun whenCookiePreferencesOfferedThenBothAreOffered() = runTest {
        val offered = testee.offer(
            listOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES, OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES),
        )

        assertEquals(
            listOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES, OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES),
            offered.map { it.preference },
        )
    }

    @Test
    fun whenAcceptNonOptOutCookiesOfferedThenItDependsOnRejectingOptionalCookies() = runTest {
        assertEquals(
            OnboardingPreference.REJECT_OPTIONAL_COOKIES,
            rowFor(OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES)?.dependsOn,
        )
    }

    @Test
    fun whenPreferencesAreOfferedThenRowsFollowTheRequestedOrder() = runTest {
        whenever(navigationHistory.isHistoryFeatureAvailable()).thenReturn(true)
        whenever(navigationHistory.isHistoryUserEnabled()).thenReturn(false)
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf(null))

        val offered = testee.offer(listOf(OnboardingPreference.SAFE_SEARCH, OnboardingPreference.SEARCH_HISTORY))

        assertEquals(
            listOf(OnboardingPreference.SAFE_SEARCH, OnboardingPreference.SEARCH_HISTORY),
            offered.map { it.preference },
        )
    }

    // endregion

    // region seeds

    @Test
    fun whenSearchHistoryOfferedThenSwitchSeedsFromUserSetting() = runTest {
        whenever(navigationHistory.isHistoryFeatureAvailable()).thenReturn(true)
        whenever(navigationHistory.isHistoryUserEnabled()).thenReturn(true)

        assertTrue(rowFor(OnboardingPreference.SEARCH_HISTORY)!!.initiallyEnabled)
    }

    @Test
    fun whenKpNotStoredThenSafeSearchSeedsOn() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf(null))

        assertTrue(rowFor(OnboardingPreference.SAFE_SEARCH)!!.initiallyEnabled)
    }

    @Test
    fun whenKpIsOffThenSafeSearchSeedsOff() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf("-2"))

        assertFalse(rowFor(OnboardingPreference.SAFE_SEARCH)!!.initiallyEnabled)
    }

    @Test
    fun whenKpIsOnThenSafeSearchSeedsOn() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf("-1"))

        assertTrue(rowFor(OnboardingPreference.SAFE_SEARCH)!!.initiallyEnabled)
    }

    @Test
    fun whenSettingsFlowCompletesEmptyThenSafeSearchSeedsOn() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(emptyFlow())

        assertTrue(rowFor(OnboardingPreference.SAFE_SEARCH)!!.initiallyEnabled)
    }

    @Test
    fun whenSettingsFlowNeverEmitsThenSafeSearchSeedsOn() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flow { awaitCancellation() })

        assertTrue(rowFor(OnboardingPreference.SAFE_SEARCH)!!.initiallyEnabled)
    }

    @Test
    fun whenSearchAssistAlreadyOnThenOnboardingStillSeedsItOff() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kbe")).thenReturn(flowOf("3"))

        assertFalse(rowFor(OnboardingPreference.SEARCH_ASSIST)!!.initiallyEnabled)
    }

    @Test
    fun whenHideAiGeneratedImagesAlreadyOffThenOnboardingStillSeedsItOn() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kbj")).thenReturn(flowOf("-1"))

        assertTrue(rowFor(OnboardingPreference.HIDE_AI_GENERATED_IMAGES)!!.initiallyEnabled)
    }

    @Test
    fun whenAutoconsentSettingOnThenRejectOptionalCookiesSeededOn() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)

        assertTrue(rowFor(OnboardingPreference.REJECT_OPTIONAL_COOKIES)!!.initiallyEnabled)
    }

    @Test
    fun whenAutoconsentSettingOffThenRejectOptionalCookiesSeededOff() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)

        assertFalse(rowFor(OnboardingPreference.REJECT_OPTIONAL_COOKIES)!!.initiallyEnabled)
    }

    @Test
    fun whenClickAcceptOnThenAcceptNonOptOutCookiesSeededOn() = runTest {
        whenever(autoconsent.isClickAcceptEnabled()).thenReturn(true)

        assertTrue(rowFor(OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES)!!.initiallyEnabled)
    }

    @Test
    fun whenClickAcceptOffThenAcceptNonOptOutCookiesSeededOff() = runTest {
        whenever(autoconsent.isClickAcceptEnabled()).thenReturn(false)

        assertFalse(rowFor(OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES)!!.initiallyEnabled)
    }

    // endregion

    // region apply

    @Test
    fun whenSearchHistoryAppliedThenUserSettingWritten() = runTest {
        testee.apply(mapOf(OnboardingPreference.SEARCH_HISTORY to false))

        verify(navigationHistory).setHistoryUserEnabled(false)
    }

    @Test
    fun whenSafeSearchEnabledThenKpSetToOn() = runTest {
        testee.apply(mapOf(OnboardingPreference.SAFE_SEARCH to true))

        verify(serpSettingsDataProvider).setSetting("kp", "-1")
    }

    @Test
    fun whenSafeSearchDisabledThenKpSetToOff() = runTest {
        testee.apply(mapOf(OnboardingPreference.SAFE_SEARCH to false))

        verify(serpSettingsDataProvider).setSetting("kp", "-2")
    }

    @Test
    fun whenSearchAssistEnabledThenKbeSetToSometimes() = runTest {
        testee.apply(mapOf(OnboardingPreference.SEARCH_ASSIST to true))

        verify(serpSettingsDataProvider).setSetting("kbe", "2")
    }

    @Test
    fun whenSearchAssistDisabledThenKbeSetToNever() = runTest {
        testee.apply(mapOf(OnboardingPreference.SEARCH_ASSIST to false))

        verify(serpSettingsDataProvider).setSetting("kbe", "0")
    }

    @Test
    fun whenHideAiGeneratedImagesEnabledThenKbjSetToOn() = runTest {
        testee.apply(mapOf(OnboardingPreference.HIDE_AI_GENERATED_IMAGES to true))

        verify(serpSettingsDataProvider).setSetting("kbj", "1")
    }

    @Test
    fun whenHideAiGeneratedImagesDisabledThenKbjSetToOff() = runTest {
        testee.apply(mapOf(OnboardingPreference.HIDE_AI_GENERATED_IMAGES to false))

        verify(serpSettingsDataProvider).setSetting("kbj", "-1")
    }

    @Test
    fun whenRejectOptionalCookiesAppliedThenAutoconsentSettingWritten() = runTest {
        testee.apply(mapOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES to true))

        verify(autoconsent).changeSetting(true)
    }

    @Test
    fun whenAcceptNonOptOutCookiesAppliedThenClickAcceptWritten() = runTest {
        testee.apply(mapOf(OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES to false))

        verify(autoconsent).changeClickAcceptEnabled(false)
    }

    @Test
    fun whenSeveralSelectionsAppliedThenEachReachesItsOwnSetting() = runTest {
        testee.apply(
            mapOf(
                OnboardingPreference.SEARCH_HISTORY to true,
                OnboardingPreference.REJECT_OPTIONAL_COOKIES to false,
            ),
        )

        verify(navigationHistory).setHistoryUserEnabled(true)
        verify(autoconsent).changeSetting(false)
    }

    // endregion

    // region plugin-backed preferences

    @Test
    fun whenAdBlockingPluginContributedThenBlockAdsRowCarriesItsCopyAndIcon() = runTest {
        assertEquals(
            Row(
                preference = OnboardingPreference.BLOCK_ADS,
                iconRes = 42,
                primaryText = TextConfig.Literal("Block ads"),
                secondaryText = null,
                initiallyEnabled = true,
            ),
            rowFor(OnboardingPreference.BLOCK_ADS),
        )
    }

    @Test
    fun whenAdBlockingPluginMissingThenBlockAdsIsNotOffered() = runTest {
        contributedPlugins = emptyList()

        assertNull(rowFor(OnboardingPreference.BLOCK_ADS))
    }

    @Test
    fun whenBlockAdsAppliedThenThePickReachesTheAdBlockingPlugin() = runTest {
        testee.apply(mapOf(OnboardingPreference.BLOCK_ADS to true))

        assertEquals(listOf(true), adBlockingPlugin.applied)
    }

    @Test
    fun whenAdBlockingPluginMissingThenApplyingBlockAdsIsANoOp() = runTest {
        contributedPlugins = emptyList()

        testee.apply(mapOf(OnboardingPreference.BLOCK_ADS to true))

        assertEquals(emptyList<Boolean>(), adBlockingPlugin.applied)
    }

    // endregion

    // region nothing is evaluated ahead of the step that offers it

    @Test
    fun whenTheCatalogIsBuiltThenNothingIsEvaluated() {
        assertEquals(0, pluginLookups)
        verifyNoInteractions(navigationHistory, autoconsent, serpSettingsDataProvider)
    }

    @Test
    fun whenOneStepOffersItsPreferencesThenNoOtherPreferenceIsEvaluated() = runTest {
        whenever(navigationHistory.isHistoryFeatureAvailable()).thenReturn(true)
        whenever(navigationHistory.isHistoryUserEnabled()).thenReturn(false)

        testee.offer(listOf(OnboardingPreference.SEARCH_HISTORY))

        assertEquals(0, pluginLookups)
        verifyNoInteractions(autoconsent, serpSettingsDataProvider)
    }

    // endregion

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
