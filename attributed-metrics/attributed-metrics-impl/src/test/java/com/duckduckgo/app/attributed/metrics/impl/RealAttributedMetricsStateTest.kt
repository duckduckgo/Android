/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.attributed.metrics.impl

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.attributed.metrics.AttributedMetricsConfigFeature
import com.duckduckgo.app.attributed.metrics.FakeAttributedMetricsDateUtils
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDataStore
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealAttributedMetricsStateTest {

    @get:Rule val coroutineTestRule = CoroutineTestRule()

    private val mockDataStore: AttributedMetricsDataStore = mock()
    private val mockConfigFeature: AttributedMetricsConfigFeature = FakeFeatureToggleFactory.create(AttributedMetricsConfigFeature::class.java)
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockLifecycleOwner: LifecycleOwner = mock()
    private lateinit var testDateUtils: AttributedMetricsDateUtils

    private lateinit var testee: RealAttributedMetricsState

    @Before fun setup() {
        initDateUtilsWith(LocalDate.of(2025, 10, 3))
        testee = RealAttributedMetricsState(
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            dataStore = mockDataStore,
            attributedMetricsConfigFeature = mockConfigFeature,
            appBuildConfig = mockAppBuildConfig,
            attributedMetricsDateUtils = testDateUtils,
        )
    }

    @Test fun whenOnAppAtbInitializedAndFeatureDisabledThenDoNothing() = runTest {
        givenAttributedClientFeatureEnabled(false)

        testee.onAppAtbInitialized()

        verify(mockDataStore, never()).setInitializationDate(any())
        verify(mockDataStore, never()).setActive(any())
    }

    @Test fun whenOnAppAtbInitializedAndFeatureEnabledAndReinstallThenSetInactiveState() = runTest {
        givenAttributedClientFeatureEnabled(true)
        whenever(mockDataStore.getInitializationDate()).thenReturn(null)
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)

        testee.onAppAtbInitialized()

        verify(mockDataStore).setInitializationDate("2025-10-03")
        verify(mockDataStore).setActive(false)
    }

    @Test fun whenOnAppAtbInitializedAndFeatureEnabledAndNewInstallThenSetActiveState() = runTest {
        givenAttributedClientFeatureEnabled(true)
        whenever(mockDataStore.getInitializationDate()).thenReturn(null)
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)

        testee.onAppAtbInitialized()

        verify(mockDataStore).setInitializationDate("2025-10-03")
        verify(mockDataStore).setActive(true)
    }

    @Test fun whenOnAppAtbInitializedAndFeatureEnabledAndAlreadyInitializedThenDoNothing() = runTest {
        givenAttributedClientFeatureEnabled(true)
        whenever(mockDataStore.getInitializationDate()).thenReturn("2025-10-03")

        testee.onAppAtbInitialized()

        verify(mockDataStore, never()).setInitializationDate(any())
        verify(mockDataStore, never()).setActive(any())
    }

    @Test fun whenOnPrivacyConfigDownloadedThenUpdateEnabledState() = runTest {
        givenAttributedClientFeatureEnabled(true)

        testee.onPrivacyConfigDownloaded()

        verify(mockDataStore).setEnabled(true)
    }

    @Test fun whenIsActiveAndAllConditionsMetThenReturnTrue() = runTest {
        whenever(mockDataStore.isActive()).thenReturn(true)
        whenever(mockDataStore.isEnabled()).thenReturn(true)
        whenever(mockDataStore.getInitializationDate()).thenReturn("2025-10-03")

        assertTrue(testee.isActive())
    }

    @Test fun whenIsActiveAndClientNotActiveThenReturnFalse() = runTest {
        whenever(mockDataStore.isActive()).thenReturn(false)
        whenever(mockDataStore.isEnabled()).thenReturn(true)
        whenever(mockDataStore.getInitializationDate()).thenReturn("2025-10-03")

        assertFalse(testee.isActive())
    }

    @Test fun whenIsActiveAndNotEnabledThenReturnFalse() = runTest {
        whenever(mockDataStore.isActive()).thenReturn(true)
        whenever(mockDataStore.isEnabled()).thenReturn(false)
        whenever(mockDataStore.getInitializationDate()).thenReturn("2025-10-03")

        assertFalse(testee.isActive())
    }

    @Test fun whenIsActiveAndNoInitDateThenReturnFalse() = runTest {
        whenever(mockDataStore.isActive()).thenReturn(true)
        whenever(mockDataStore.isEnabled()).thenReturn(true)
        whenever(mockDataStore.getInitializationDate()).thenReturn(null)

        assertFalse(testee.isActive())
    }

    @Test fun whenCheckCollectionPeriodAndNoInitDateThenDoNothing() = runTest {
        whenever(mockDataStore.getInitializationDate()).thenReturn(null)

        testee.onCreate(mockLifecycleOwner)

        verify(mockDataStore, never()).setActive(any())
    }

    @Test fun whenCheckCollectionPeriodAndWithinPeriodAndActiveThenKeepActive() = runTest {
        whenever(mockDataStore.getInitializationDate()).thenReturn(testDateUtils.getDateMinusDays(100))
        whenever(mockDataStore.isActive()).thenReturn(true)

        testee.onCreate(mockLifecycleOwner)

        verify(mockDataStore).setActive(true)
    }

    @Test fun whenCheckCollectionPeriodAndWithinPeriodAndNotActiveThenKeepInactive() = runTest {
        whenever(mockDataStore.getInitializationDate()).thenReturn(testDateUtils.getDateMinusDays(100))
        whenever(mockDataStore.isActive()).thenReturn(false)

        testee.onCreate(mockLifecycleOwner)

        verify(mockDataStore).setActive(false)
    }

    @Test fun whenCheckCollectionPeriodAndOutsidePeriodThenSetInactive() = runTest {
        whenever(mockDataStore.getInitializationDate()).thenReturn(testDateUtils.getDateMinusDays(169)) // 6months + 1
        whenever(mockDataStore.isActive()).thenReturn(true)

        testee.onCreate(mockLifecycleOwner)

        verify(mockDataStore).setActive(false)
    }

    private fun givenAttributedClientFeatureEnabled(isEnabled: Boolean) {
        mockConfigFeature.self().setRawStoredState(State(isEnabled))
    }

    private fun initDateUtilsWith(date: LocalDate) {
        testDateUtils = FakeAttributedMetricsDateUtils(date)
    }
}
