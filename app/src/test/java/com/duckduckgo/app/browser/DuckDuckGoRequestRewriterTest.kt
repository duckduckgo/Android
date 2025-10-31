/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.utils.AppUrl.ParamKey
import com.duckduckgo.common.utils.AppUrl.ParamValue
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.settings.api.SettingsPageFeature
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi") // fake toggle store
class DuckDuckGoRequestRewriterTest {

    private lateinit var testee: DuckDuckGoRequestRewriter
    private val mockStatisticsStore: StatisticsDataStore = mock()
    private val mockVariantManager: VariantManager = mock()
    private val mockAppReferrerDataStore: AppReferrerDataStore = mock()
    private val duckChat: DuckChat = mock()
    private val settingsPageFeature: SettingsPageFeature = FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private lateinit var builder: Uri.Builder

    @Before
    fun before() {
        whenever(mockVariantManager.getVariantKey()).thenReturn("")
        whenever(mockAppReferrerDataStore.installedFromEuAuction).thenReturn(false)
        whenever(duckChat.isEnabled()).thenReturn(true)

        androidBrowserConfigFeature.hideDuckAiInSerpKillSwitch().setRawStoredState(State(true))

        testee = DuckDuckGoRequestRewriter(
            DuckDuckGoUrlDetectorImpl(),
            mockStatisticsStore,
            mockVariantManager,
            mockAppReferrerDataStore,
            duckChat,
            androidBrowserConfigFeature,
            settingsPageFeature,
        )
        builder = Uri.Builder()
    }

    @Test
    fun whenAddingCustomParamsSourceParameterIsAdded() {
        testee.addCustomQueryParams(builder)
        val uri = builder.build()
        assertTrue(uri.queryParameterNames.contains(ParamKey.SOURCE))
        assertEquals("ddg_android", uri.getQueryParameter(ParamKey.SOURCE))
    }

    @Test
    fun whenAddingCustomParamsAndUserSourcedFromEuAuctionThenEuSourceParameterIsAdded() {
        whenever(mockAppReferrerDataStore.installedFromEuAuction).thenReturn(true)
        testee.addCustomQueryParams(builder)
        val uri = builder.build()
        assertTrue(uri.queryParameterNames.contains(ParamKey.SOURCE))
        assertEquals("ddg_androideu", uri.getQueryParameter(ParamKey.SOURCE))
    }

    @Test
    fun whenAddingCustomParamsIfStoreContainsAtbIsAdded() {
        whenever(mockStatisticsStore.atb).thenReturn(Atb("v105-2ma"))
        testee.addCustomQueryParams(builder)
        val uri = builder.build()
        assertTrue(uri.queryParameterNames.contains(ParamKey.ATB))
        assertEquals("v105-2ma", uri.getQueryParameter(ParamKey.ATB))
    }

    @Test
    fun whenAddingCustomParamsIfIsStoreMissingAtbThenAtbIsNotAdded() {
        whenever(mockStatisticsStore.atb).thenReturn(null)

        testee.addCustomQueryParams(builder)
        val uri = builder.build()
        assertFalse(uri.queryParameterNames.contains(ParamKey.ATB))
    }

    @Test
    fun whenSerpRemovalFeatureIsActiveThenHideParamIsAddedToSerpUrl() {
        testee.addCustomQueryParams(builder)

        val uri = builder.build()
        assertTrue(uri.queryParameterNames.contains(ParamKey.HIDE_SERP))
    }

    @Test
    fun whenDuckAiIsDisabledThenHideSerpDuckChat() {
        whenever(duckChat.isEnabled()).thenReturn(false)
        androidBrowserConfigFeature.hideDuckAiInSerpKillSwitch().setRawStoredState(State(true))
        testee.addCustomQueryParams(builder)

        val uri = builder.build()
        assertTrue(uri.queryParameterNames.contains(ParamKey.HIDE_DUCK_AI))
        assertEquals(ParamValue.HIDE_DUCK_AI, uri.getQueryParameter(ParamKey.HIDE_DUCK_AI))
    }

    @Test
    fun whenDuckAiIsDisabledAndKillSwitchedThenDoNotHideSerpDuckChat() {
        whenever(duckChat.isEnabled()).thenReturn(false)
        androidBrowserConfigFeature.hideDuckAiInSerpKillSwitch().setRawStoredState(State(false))
        testee.addCustomQueryParams(builder)

        val uri = builder.build()
        assertFalse(uri.queryParameterNames.contains(ParamKey.HIDE_DUCK_AI))
    }

    @Test
    fun whenDuckAiIsEnabledThenDoNotHideSerpDuckChat() {
        whenever(duckChat.isEnabled()).thenReturn(true)
        androidBrowserConfigFeature.hideDuckAiInSerpKillSwitch().setRawStoredState(State(true))
        testee.addCustomQueryParams(builder)

        val uri = builder.build()
        assertFalse(uri.queryParameterNames.contains(ParamKey.HIDE_DUCK_AI))
    }

    @Test
    fun whenDuckAiIsEnabledAndKillSwitchedThenDoNotHideSerpDuckChat() {
        whenever(duckChat.isEnabled()).thenReturn(true)
        androidBrowserConfigFeature.hideDuckAiInSerpKillSwitch().setRawStoredState(State(false))
        testee.addCustomQueryParams(builder)

        val uri = builder.build()
        assertFalse(uri.queryParameterNames.contains(ParamKey.HIDE_DUCK_AI))
    }

    @Test
    fun whenSerpSettingsSyncIsEnabledThenDoNotHideDuckAi() {
        settingsPageFeature.serpSettingsSync().setRawStoredState(State(true))
        whenever(duckChat.isEnabled()).thenReturn(false)
        androidBrowserConfigFeature.hideDuckAiInSerpKillSwitch().setRawStoredState(State(true))

        testee.addCustomQueryParams(builder)

        val uri = builder.build()
        assertFalse(uri.queryParameterNames.contains(ParamKey.HIDE_DUCK_AI))
    }

    @Test
    fun whenShouldRewriteRequestAndUrlIsSerpQueryThenReturnTrue() {
        val uri = "http://duckduckgo.com/?q=weather".toUri()
        assertTrue(testee.shouldRewriteRequest(uri))
    }

    @Test
    fun whenShouldRewriteRequestAndUrlIsSerpQueryWithSourceAndAtbThenReturnFalse() {
        val uri = "http://duckduckgo.com/?q=weather&atb=test&t=test".toUri()
        assertFalse(testee.shouldRewriteRequest(uri))
    }

    @Test
    fun whenShouldRewriteRequestAndUrlIsADuckDuckGoStaticUrlThenReturnTrue() {
        val uri = "http://duckduckgo.com/settings".toUri()
        assertTrue(testee.shouldRewriteRequest(uri))

        val uri2 = "http://duckduckgo.com/params".toUri()
        assertTrue(testee.shouldRewriteRequest(uri2))
    }

    @Test
    fun whenShouldRewriteRequestAndUrlIsDuckDuckGoEmailThenReturnFalse() {
        val uri = "http://duckduckgo.com/email".toUri()
        assertFalse(testee.shouldRewriteRequest(uri))
    }
}
