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

package com.duckduckgo.adblocking.impl.segmentskip

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RealSegmentSkipUserPreferencesTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("segment_skip_user_preferences_test") },
        )

    private val feature = FakeFeatureToggleFactory.create(SegmentSkipFeature::class.java)
    private val testee = RealSegmentSkipUserPreferences(testDataStore, coroutineRule.testScope, feature)

    @Test
    fun whenDefaultEnabledOrLegacyModeThenConsentIsAbsent() = runTest {
        feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        assertEquals(false, testee.hasConsent())
        testDataStore.edit { it[stringPreferencesKey("segment_skip_mode")] = "ALWAYS_SKIP" }
        assertEquals(false, testee.consentFlow().first())
        testee.setMode(SegmentSkipMode.ALWAYS_ASK, noticeShown = true)
        assertEquals(false, testee.consentFlow().first())
    }

    @Test
    fun whenEnabledFromOffAfterNoticeThenConsentPersistsUntilOff() = runTest {
        testee.setMode(SegmentSkipMode.OFF)
        testee.setMode(SegmentSkipMode.ALWAYS_SKIP, noticeShown = true)
        assertEquals(true, testee.consentFlow().first())
        val recreated = RealSegmentSkipUserPreferences(testDataStore, coroutineRule.testScope, feature)
        assertEquals(true, recreated.consentFlow().first())
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        assertEquals(true, testee.hasConsent())
        testee.setMode(SegmentSkipMode.OFF)
        assertEquals(false, testee.hasConsent())
        assertEquals(false, testee.consentFlow().first())
        testee.setMode(SegmentSkipMode.ALWAYS_ASK)
        assertEquals(false, testee.consentFlow().first())
    }

    @Test
    fun whenTurnedOffThenOnAgainConsentIsRestoredOnlyAfterNotice() = runTest {
        testee.setMode(SegmentSkipMode.OFF)
        testee.setMode(SegmentSkipMode.ALWAYS_ASK, noticeShown = true)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        assertEquals(true, testee.hasConsent())
        testee.setMode(SegmentSkipMode.OFF)
        assertEquals(false, testee.hasConsent())
        testee.setMode(SegmentSkipMode.ALWAYS_SKIP, noticeShown = true)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        assertEquals(true, testee.hasConsent())
    }

    @Test
    fun whenMvpEnabledAndNoNoticeThenReenablingDoesNotGrantConsent() = runTest {
        feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        testee.setMode(SegmentSkipMode.OFF)
        testee.setMode(SegmentSkipMode.ALWAYS_SKIP, noticeShown = false)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        assertEquals(false, testee.consentFlow().first())
        assertEquals(false, testee.hasConsent())
    }

    @Test
    fun whenMvpEnabledThenExistingConsentStillAllowsTelemetry() = runTest {
        testee.setMode(SegmentSkipMode.OFF)
        testee.setMode(SegmentSkipMode.ALWAYS_ASK, noticeShown = true)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        assertEquals(true, testee.hasConsent())
        feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        assertEquals(true, testee.hasConsent())
    }

    @Test
    fun whenReadFailsThenModeIsOffAndNewCollectionCanRecover() = runTest {
        val failingStore: DataStore<Preferences> = mock()
        var fails = true
        whenever(failingStore.data).thenReturn(
            flow {
                if (fails) throw IOException()
                emit(preferencesOf(stringPreferencesKey("segment_skip_mode") to "ALWAYS_SKIP"))
            },
        )
        val testFeature: SegmentSkipFeature = mock()
        val toggle: Toggle = mock()
        whenever(testFeature.mvpEnabled()).thenReturn(toggle)
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(toggle.enabled()).thenReturn(flowOf(true))
        val preferences = RealSegmentSkipUserPreferences(failingStore, coroutineRule.testScope, testFeature)

        assertEquals(SegmentSkipMode.OFF, preferences.cachedMode())
        fails = false
        assertEquals(SegmentSkipMode.ALWAYS_SKIP, preferences.modeFlow().first())
        assertEquals(SegmentSkipMode.OFF, preferences.cachedMode())
    }

    @Test
    fun whenNothingStoredThenDefaultDependsOnMvpFlag() = runTest {
        for (mvp in listOf(false, true)) {
            for (softLaunch in listOf(false, true)) {
                feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = mvp))
                feature.softLaunchEnabled().setRawStoredState(Toggle.State(remoteEnableState = softLaunch))
                val expected = if (mvp) SegmentSkipMode.ALWAYS_ASK else SegmentSkipMode.OFF
                assertEquals(expected, testee.modeFlow().first())
            }
        }
    }

    @Test
    fun whenMvpEnabledAndSoftLaunchDisabledThenInitialCacheAlwaysAsks() {
        feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        feature.softLaunchEnabled().setRawStoredState(Toggle.State(remoteEnableState = false))

        val preferences = RealSegmentSkipUserPreferences(testDataStore, coroutineRule.testScope, feature)

        assertEquals(SegmentSkipMode.ALWAYS_ASK, preferences.cachedMode())
    }

    @Test
    fun whenFlagsChangeThenSavedModeIsPreserved() = runTest {
        testee.setMode(SegmentSkipMode.ALWAYS_SKIP)
        feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        feature.softLaunchEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        assertEquals(SegmentSkipMode.ALWAYS_SKIP, testee.modeFlow().first())
        feature.softLaunchEnabled().setRawStoredState(Toggle.State(remoteEnableState = false))
        assertEquals(SegmentSkipMode.ALWAYS_SKIP, testee.modeFlow().first())
    }

    @Test
    fun whenModeSetThenFlowAndCacheReportIt() = runTest {
        testee.setMode(SegmentSkipMode.OFF)

        assertEquals(SegmentSkipMode.OFF, testee.modeFlow().first())
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        assertEquals(SegmentSkipMode.OFF, testee.cachedMode())
    }

    @Test
    fun whenConcurrentSavesSelectSameModeThenOnlyOneChangesIt() = runTest {
        val first = async { testee.setMode(SegmentSkipMode.ALWAYS_SKIP) }
        val second = async { testee.setMode(SegmentSkipMode.ALWAYS_SKIP) }
        assertEquals(listOf(false, true), listOf(first.await(), second.await()).sorted())
    }

    @Test
    fun whenSavingDefaultThenChangeReflectsMvpFlag() = runTest {
        for (mvp in listOf(false, true)) {
            testDataStore.edit { it.clear() }
            feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = mvp))
            val default = if (mvp) SegmentSkipMode.ALWAYS_ASK else SegmentSkipMode.OFF
            assertEquals(false, testee.setMode(default))
        }
    }

    @Test
    fun whenDefaultIsSavedThenLaterDefaultChangeDoesNotReplaceIt() = runTest {
        feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        feature.softLaunchEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        assertEquals(false, testee.setMode(SegmentSkipMode.ALWAYS_ASK))
        feature.softLaunchEnabled().setRawStoredState(Toggle.State(remoteEnableState = false))
        assertEquals(SegmentSkipMode.ALWAYS_ASK, testee.modeFlow().first())
    }

    @Test
    fun whenExistingModeStoredThenItIsRestored() = runTest {
        testDataStore.edit { it[stringPreferencesKey("segment_skip_mode")] = "ALWAYS_SKIP" }

        assertEquals(SegmentSkipMode.ALWAYS_SKIP, testee.modeFlow().first())
    }

    @Test
    fun whenStoredValueIsUnknownThenModeIsOff() = runTest {
        testDataStore.edit { it[stringPreferencesKey("segment_skip_mode")] = "SOMETHING_ELSE" }

        assertEquals(SegmentSkipMode.OFF, testee.modeFlow().first())
    }
}
