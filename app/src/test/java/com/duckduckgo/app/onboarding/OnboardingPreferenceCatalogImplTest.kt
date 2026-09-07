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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin.Preference
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
    private val serpSettingsDataProvider: SerpSettingsDataProvider = mock()
    private val serpSettingsFeature: SerpSettingsFeature = FakeFeatureToggleFactory.create(SerpSettingsFeature::class.java)
    private val adBlockingPlugin = FakeOnboardingBooleanPreferencePlugin()
    private val rejectOptionalCookiesPlugin = FakeOnboardingBooleanPreferencePlugin(
        id = OnboardingBooleanPreferencePlugin.Id.RejectOptionalCookies,
        preference = Preference(
            primaryText = "Reject optional cookies",
            secondaryText = "Maximizes privacy and closes cookie pop-ups",
            iconRes = 43,
        ),
    )
    private val acceptNonOptOutCookiesPlugin = FakeOnboardingBooleanPreferencePlugin(
        id = OnboardingBooleanPreferencePlugin.Id.AcceptNonOptOutCookies,
        preference = Preference(
            primaryText = "Accept some cookies",
            secondaryText = "Hides more pop-ups by accepting cookies that can't be rejected",
            iconRes = 44,
        ),
    )
    private var contributedPlugins: List<OnboardingBooleanPreferencePlugin> =
        listOf(adBlockingPlugin, rejectOptionalCookiesPlugin, acceptNonOptOutCookiesPlugin)
    private var pluginLookups = 0
    private val booleanPreferencePlugins = object : ActivePluginPoint<OnboardingBooleanPreferencePlugin> {
        override suspend fun getPlugins(): List<OnboardingBooleanPreferencePlugin> {
            pluginLookups++
            return contributedPlugins
        }
    }

    private val testee = OnboardingPreferenceCatalogImpl(
        navigationHistory = navigationHistory,
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

    /** A dependent row is only offered alongside its parent, so the pair has to be asked for together. */
    private suspend fun acceptNonOptOutCookiesRow(): Row? = testee.offer(
        listOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES, OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES),
    ).lastOrNull()

    // region availability

    @Test
    fun `when history feature unavailable then search history is not offered`() = runTest {
        whenever(navigationHistory.isHistoryFeatureAvailable()).thenReturn(false)

        assertNull(rowFor(OnboardingPreference.SEARCH_HISTORY))
    }

    @Test
    fun `when history feature available then search history is offered`() = runTest {
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
    fun `when serp settings storage enabled then serp backed preferences are offered`() = runTest {
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
    fun `when serp settings storage disabled then serp backed preferences are not offered`() = runTest {
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
    fun `when cookie preferences offered then both are offered`() = runTest {
        val offered = testee.offer(
            listOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES, OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES),
        )

        assertEquals(
            listOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES, OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES),
            offered.map { it.preference },
        )
    }

    @Test
    fun `when accept non opt out cookies offered then it depends on rejecting optional cookies`() = runTest {
        assertEquals(
            OnboardingPreference.REJECT_OPTIONAL_COOKIES,
            acceptNonOptOutCookiesRow()?.dependsOn,
        )
    }

    @Test
    fun `when reject optional cookies plugin missing then the dependent accept row is dropped too`() = runTest {
        contributedPlugins = listOf(acceptNonOptOutCookiesPlugin)

        assertEquals(
            emptyList<Row>(),
            testee.offer(
                listOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES, OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES),
            ),
        )
    }

    @Test
    fun `when accept non opt out cookies is offered without its parent then it is dropped`() = runTest {
        assertNull(rowFor(OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES))
    }

    @Test
    fun `when accept non opt out cookies plugin missing then only reject is offered`() = runTest {
        contributedPlugins = listOf(rejectOptionalCookiesPlugin)

        assertEquals(
            listOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES),
            testee.offer(
                listOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES, OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES),
            ).map { it.preference },
        )
    }

    @Test
    fun `when preferences are offered then rows follow the requested order`() = runTest {
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
    fun `when search history offered then switch seeds from user setting`() = runTest {
        whenever(navigationHistory.isHistoryFeatureAvailable()).thenReturn(true)
        whenever(navigationHistory.isHistoryUserEnabled()).thenReturn(true)

        assertTrue(rowFor(OnboardingPreference.SEARCH_HISTORY)!!.initiallyEnabled)
    }

    @Test
    fun `when kp not stored then safe search seeds on`() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf(null))

        assertTrue(rowFor(OnboardingPreference.SAFE_SEARCH)!!.initiallyEnabled)
    }

    @Test
    fun `when kp is off then safe search seeds off`() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf("-2"))

        assertFalse(rowFor(OnboardingPreference.SAFE_SEARCH)!!.initiallyEnabled)
    }

    @Test
    fun `when kp is on then safe search seeds on`() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flowOf("-1"))

        assertTrue(rowFor(OnboardingPreference.SAFE_SEARCH)!!.initiallyEnabled)
    }

    @Test
    fun `when settings flow completes empty then safe search seeds on`() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(emptyFlow())

        assertTrue(rowFor(OnboardingPreference.SAFE_SEARCH)!!.initiallyEnabled)
    }

    @Test
    fun `when settings flow never emits then safe search seeds on`() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kp")).thenReturn(flow { awaitCancellation() })

        assertTrue(rowFor(OnboardingPreference.SAFE_SEARCH)!!.initiallyEnabled)
    }

    @Test
    fun `when search assist already on then onboarding still seeds it off`() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kbe")).thenReturn(flowOf("3"))

        assertFalse(rowFor(OnboardingPreference.SEARCH_ASSIST)!!.initiallyEnabled)
    }

    @Test
    fun `when hide ai generated images already off then onboarding still seeds it on`() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("kbj")).thenReturn(flowOf("-1"))

        assertTrue(rowFor(OnboardingPreference.HIDE_AI_GENERATED_IMAGES)!!.initiallyEnabled)
    }

    @Test
    fun `when reject optional cookies offered then onboarding seeds it on`() = runTest {
        assertTrue(rowFor(OnboardingPreference.REJECT_OPTIONAL_COOKIES)!!.initiallyEnabled)
    }

    @Test
    fun `when accept non opt out cookies offered then onboarding seeds it off`() = runTest {
        assertFalse(acceptNonOptOutCookiesRow()!!.initiallyEnabled)
    }

    // endregion

    // region apply

    @Test
    fun `when search history applied then user setting written`() = runTest {
        testee.apply(mapOf(OnboardingPreference.SEARCH_HISTORY to false))

        verify(navigationHistory).setHistoryUserEnabled(false)
    }

    @Test
    fun `when safe search enabled then kp set to on`() = runTest {
        testee.apply(mapOf(OnboardingPreference.SAFE_SEARCH to true))

        verify(serpSettingsDataProvider).setSetting("kp", "-1")
    }

    @Test
    fun `when safe search disabled then kp set to off`() = runTest {
        testee.apply(mapOf(OnboardingPreference.SAFE_SEARCH to false))

        verify(serpSettingsDataProvider).setSetting("kp", "-2")
    }

    @Test
    fun `when search assist enabled then kbe set to sometimes`() = runTest {
        testee.apply(mapOf(OnboardingPreference.SEARCH_ASSIST to true))

        verify(serpSettingsDataProvider).setSetting("kbe", "2")
    }

    @Test
    fun `when search assist disabled then kbe set to never`() = runTest {
        testee.apply(mapOf(OnboardingPreference.SEARCH_ASSIST to false))

        verify(serpSettingsDataProvider).setSetting("kbe", "0")
    }

    @Test
    fun `when hide ai generated images enabled then kbj set to on`() = runTest {
        testee.apply(mapOf(OnboardingPreference.HIDE_AI_GENERATED_IMAGES to true))

        verify(serpSettingsDataProvider).setSetting("kbj", "1")
    }

    @Test
    fun `when hide ai generated images disabled then kbj set to off`() = runTest {
        testee.apply(mapOf(OnboardingPreference.HIDE_AI_GENERATED_IMAGES to false))

        verify(serpSettingsDataProvider).setSetting("kbj", "-1")
    }

    @Test
    fun `when reject optional cookies applied then the pick reaches its plugin`() = runTest {
        testee.apply(mapOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES to true))

        assertEquals(listOf(true), rejectOptionalCookiesPlugin.applied)
    }

    @Test
    fun `when accept non opt out cookies applied then the pick reaches its plugin`() = runTest {
        testee.apply(mapOf(OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES to false))

        assertEquals(listOf(false), acceptNonOptOutCookiesPlugin.applied)
    }

    @Test
    fun `when several selections applied then each reaches its own setting`() = runTest {
        testee.apply(
            mapOf(
                OnboardingPreference.SEARCH_HISTORY to true,
                OnboardingPreference.REJECT_OPTIONAL_COOKIES to false,
            ),
        )

        verify(navigationHistory).setHistoryUserEnabled(true)
        assertEquals(listOf(false), rejectOptionalCookiesPlugin.applied)
    }

    // endregion

    // region plugin-backed preferences

    @Test
    fun `when ad blocking plugin contributed then block ads row carries its copy and icon`() = runTest {
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
    fun `when ad blocking plugin missing then block ads is not offered`() = runTest {
        contributedPlugins = emptyList()

        assertNull(rowFor(OnboardingPreference.BLOCK_ADS))
    }

    @Test
    fun `when block ads applied then the pick reaches the ad blocking plugin`() = runTest {
        testee.apply(mapOf(OnboardingPreference.BLOCK_ADS to true))

        assertEquals(listOf(true), adBlockingPlugin.applied)
    }

    @Test
    fun `when cookie plugins contributed then their rows carry their copy and icons`() = runTest {
        assertEquals(
            listOf(
                Row(
                    preference = OnboardingPreference.REJECT_OPTIONAL_COOKIES,
                    iconRes = 43,
                    primaryText = TextConfig.Literal("Reject optional cookies"),
                    secondaryText = TextConfig.Literal("Maximizes privacy and closes cookie pop-ups"),
                    initiallyEnabled = true,
                ),
                Row(
                    preference = OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES,
                    iconRes = 44,
                    primaryText = TextConfig.Literal("Accept some cookies"),
                    secondaryText = TextConfig.Literal("Hides more pop-ups by accepting cookies that can't be rejected"),
                    initiallyEnabled = false,
                    dependsOn = OnboardingPreference.REJECT_OPTIONAL_COOKIES,
                ),
            ),
            testee.offer(
                listOf(OnboardingPreference.REJECT_OPTIONAL_COOKIES, OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES),
            ),
        )
    }

    @Test
    fun `when ad blocking plugin withholds the preference then block ads is not offered`() = runTest {
        contributedPlugins = listOf(FakeOnboardingBooleanPreferencePlugin(preference = null))

        assertNull(rowFor(OnboardingPreference.BLOCK_ADS))
    }

    @Test
    fun `when ad blocking plugin missing then applying block ads is a no op`() = runTest {
        contributedPlugins = emptyList()

        testee.apply(mapOf(OnboardingPreference.BLOCK_ADS to true))

        assertEquals(emptyList<Boolean>(), adBlockingPlugin.applied)
    }

    // endregion

    // region nothing is evaluated ahead of the step that offers it

    @Test
    fun `when the catalog is built then nothing is evaluated`() {
        assertEquals(0, pluginLookups)
        verifyNoInteractions(navigationHistory, serpSettingsDataProvider)
    }

    @Test
    fun `when one step offers its preferences then no other preference is evaluated`() = runTest {
        whenever(navigationHistory.isHistoryFeatureAvailable()).thenReturn(true)
        whenever(navigationHistory.isHistoryUserEnabled()).thenReturn(false)

        testee.offer(listOf(OnboardingPreference.SEARCH_HISTORY))

        assertEquals(0, pluginLookups)
        verifyNoInteractions(serpSettingsDataProvider)
    }

    // endregion
}

private class FakeOnboardingBooleanPreferencePlugin(
    override val id: OnboardingBooleanPreferencePlugin.Id = OnboardingBooleanPreferencePlugin.Id.AdBlocking,
    private val preference: Preference? = Preference(primaryText = "Block ads", iconRes = 42),
) : OnboardingBooleanPreferencePlugin {

    val applied = mutableListOf<Boolean>()

    override suspend fun getPreference(): Preference? = preference

    override suspend fun apply(enabled: Boolean) {
        applied += enabled
    }
}
