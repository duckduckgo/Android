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

package com.duckduckgo.pir.impl.freemium

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.pir.api.freemium.PirFreemiumEntryPoint.HIDDEN
import com.duckduckgo.pir.api.freemium.PirFreemiumEntryPoint.START_FREE_SCAN
import com.duckduckgo.pir.api.freemium.PirFreemiumEntryPoint.VIEW_SCAN_RESULTS
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult.MATCHES_FOUND
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult.NO_MATCHES
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale

class RealPirFreemiumTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pirRemoteFeatures = FakeFeatureToggleFactory.create(PirRemoteFeatures::class.java)
    private val subscriptions: Subscriptions = mock()
    private val dataStore: PirFreemiumDataStore = mock()
    private val appBuildConfig: AppBuildConfig = mock()

    private lateinit var testee: RealPirFreemium

    @Before
    fun setUp() = runTest {
        pirRemoteFeatures.pirBeta().setRawStoredState(State(enable = true))
        pirRemoteFeatures.freemium().setRawStoredState(State(enable = true))
        whenever(subscriptions.isSignedIn()).thenReturn(false)
        whenever(dataStore.firstScanResult).thenReturn(null)
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        testee = RealPirFreemium(
            pirRemoteFeatures = pirRemoteFeatures,
            subscriptions = subscriptions,
            pirFreemiumDataStore = dataStore,
            appBuildConfig = appBuildConfig,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenAllGatesPassAndNoScanCompletedThenStartFreeScan() = runTest {
        assertEquals(START_FREE_SCAN, testee.getSettingsEntryPoint())
    }

    @Test
    fun whenPirRolloutIsOffThenHidden() = runTest {
        pirRemoteFeatures.pirBeta().setRawStoredState(State(enable = false))

        assertEquals(HIDDEN, testee.getSettingsEntryPoint())
    }

    @Test
    fun whenFreemiumFlagIsOffThenHidden() = runTest {
        pirRemoteFeatures.freemium().setRawStoredState(State(enable = false))

        assertEquals(HIDDEN, testee.getSettingsEntryPoint())
    }

    @Test
    fun whenUserIsSignedInThenHidden() = runTest {
        whenever(subscriptions.isSignedIn()).thenReturn(true)

        assertEquals(HIDDEN, testee.getSettingsEntryPoint())
    }

    @Test
    fun whenScanFoundMatchesThenViewScanResults() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(MATCHES_FOUND)

        assertEquals(VIEW_SCAN_RESULTS, testee.getSettingsEntryPoint())
    }

    @Test
    fun whenScanFoundNothingThenStillViewScanResults() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(NO_MATCHES)

        assertEquals(VIEW_SCAN_RESULTS, testee.getSettingsEntryPoint())
    }

    @Test
    fun whenLocaleHasNoCountryCodeThenHidden() = runTest {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale("en"))

        assertEquals(HIDDEN, testee.getSettingsEntryPoint())
    }

    @Test
    fun whenLocaleIsNonUsThenHidden() = runTest {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.GERMANY)

        assertEquals(HIDDEN, testee.getSettingsEntryPoint())
    }

    @Test
    fun whenLocaleIsNonUsButBuildIsInternalThenStartFreeScan() = runTest {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.GERMANY)
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        assertEquals(START_FREE_SCAN, testee.getSettingsEntryPoint())
    }

    @Test
    fun whenSignedInCheckThrowsThenHidden() = runTest {
        whenever(subscriptions.isSignedIn()).thenThrow(RuntimeException("backend unavailable"))

        assertEquals(HIDDEN, testee.getSettingsEntryPoint())
    }

    @Test
    fun whenFirstScanResultReadThrowsThenHidden() = runTest {
        whenever(dataStore.firstScanResult).thenThrow(RuntimeException("corrupted preferences"))

        assertEquals(HIDDEN, testee.getSettingsEntryPoint())
    }
}
