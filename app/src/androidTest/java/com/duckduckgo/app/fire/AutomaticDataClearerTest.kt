/*
 * Copyright (c) 2018 DuckDuckGo
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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.fire

import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutomaticDataClearerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    private lateinit var testee: AutomaticDataClearer

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockClearAction: ClearDataAction = mock()
    private val mockTimeKeeper: BackgroundTimeKeeper = mock()
    private val mockWorkManager: WorkManager = mock()
    private val pixel: Pixel = mock()
    private val dataClearerForegroundAppRestartPixel =
        DataClearerForegroundAppRestartPixel(
            InstrumentationRegistry.getInstrumentation().targetContext,
            pixel,
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
        )

    @UiThreadTest
    @Before
    fun setup() {
        whenever(mockSettingsDataStore.hasBackgroundTimestampRecorded()).thenReturn(true)
        testee = AutomaticDataClearer(
            workManager = mockWorkManager,
            settingsDataStore = mockSettingsDataStore,
            clearDataAction = mockClearAction,
            dataClearerTimeKeeper = mockTimeKeeper,
            dataClearerForegroundAppRestartPixel = dataClearerForegroundAppRestartPixel,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    private suspend fun simulateLifecycle(isFreshAppLaunch: Boolean) {
        testee.isFreshAppLaunch = isFreshAppLaunch
        testee.onAppForegroundedAsync()
    }

    /* Clear None tests */

    @UiThreadTest
    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    /* Clear tabs tests */

    @UiThreadTest
    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenShouldClearTabs() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsCleared()
    }

    @UiThreadTest
    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenShouldClearTabs() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsCleared()
    }

    @UiThreadTest
    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenShouldNotClearTabs() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenShouldNotClearTabs() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotAppExitOnlyAndFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearShouldClearTabs() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotAppExitOnlyAndNotFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearShouldClearTabs() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotAppExitOnlyAndFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearShouldClearTabs() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotAppExitOnlyAndFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearShouldNotClearTabs() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyTabsNotCleared()
    }

    /* Clear Tabs and Data tests */

    @UiThreadTest
    @Test
    fun whenAppExitOnlyFreshAppLaunchAndEnoughTimePassedAppUsedSinceLastClearThenShouldClear() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOnlyFreshAppLaunchAndEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOnlyFreshAppLaunchAndNotEnoughTimePassedAppUsedSinceLastClearThenShouldClear() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOnlyFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runTest {
        val isFreshAppLaunch = true
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndEnoughTimePassedAppUsedSinceLastClearThenShouldNotClear() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndNotEnoughTimePassedAppUsedSinceLastClearThenShouldNotClear() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOrTimerFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndEnoughTimePassedAppUsedSinceLastClearThenShouldClear() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndNotEnoughTimePassedAppUsedSinceLastClearThenShouldNotClear() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runTest {
        val isFreshAppLaunch = false
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndIconJustChangedButAppNotUsedThenShouldNotClear() = runTest {
        val isFreshAppLaunch = false

        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureAppUsedSinceLastClear()
        configureAppIconJustChanged()

        simulateLifecycle(isFreshAppLaunch)
        verifyAppIconFlagReset()
        verifyEverythingNotCleared()
    }

    @UiThreadTest
    @Test
    fun whenFreshAppLaunchAndIconJustChangedButAppUsedThenShouldClear() = runTest {
        val isFreshAppLaunch = true

        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureAppUsedSinceLastClear()
        configureAppIconJustChanged()

        simulateLifecycle(isFreshAppLaunch)
        verifyAppIconFlagReset()
        verifyEverythingCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndIconNotChangedThenShouldClear() = runTest {
        val isFreshAppLaunch = false

        configureAppIconNotChanged()
        configureAppNotUsedSinceLastClear()
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingCleared()
    }

    @UiThreadTest
    @Test
    fun whenNotFreshAppLaunchAndIconNotChangedAppUsedThenShouldClear() = runTest {
        val isFreshAppLaunch = false

        configureAppIconNotChanged()
        configureAppUsedSinceLastClear()
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        simulateLifecycle(isFreshAppLaunch)
        verifyEverythingCleared()
    }

    private fun configureUserOptions(
        whatOption: ClearWhatOption,
        whenOption: ClearWhenOption,
    ) {
        whenever(mockSettingsDataStore.automaticallyClearWhenOption).thenReturn(whenOption)
        whenever(mockSettingsDataStore.automaticallyClearWhatOption).thenReturn(whatOption)
    }

    private fun configureAppUsedSinceLastClear() {
        whenever(mockSettingsDataStore.appUsedSinceLastClear).thenReturn(true)
    }

    private fun configureAppNotUsedSinceLastClear() {
        whenever(mockSettingsDataStore.appUsedSinceLastClear).thenReturn(false)
    }

    private fun configureEnoughTimePassed() {
        whenever(mockTimeKeeper.hasEnoughTimeElapsed(any(), any(), any())).thenReturn(true)
    }

    private fun configureNotEnoughTimePassed() {
        whenever(mockTimeKeeper.hasEnoughTimeElapsed(any(), any(), any())).thenReturn(false)
    }

    private fun configureAppIconJustChanged() {
        whenever(mockSettingsDataStore.appIconChanged).thenReturn(true)
    }

    private fun configureAppIconNotChanged() {
        whenever(mockSettingsDataStore.appIconChanged).thenReturn(false)
    }

    private suspend fun verifyTabsCleared() {
        verify(mockClearAction).clearTabsAsync(any())
    }

    private suspend fun verifyTabsNotCleared() {
        verify(mockClearAction, never()).clearTabsAsync(any())
    }

    private suspend fun verifyEverythingCleared() {
        verify(mockClearAction).clearTabsAndAllDataAsync(any(), any())
    }

    private suspend fun verifyEverythingNotCleared() {
        verify(mockClearAction, never()).clearTabsAndAllDataAsync(any(), any())
    }

    private fun verifyAppIconFlagReset() {
        verify(mockSettingsDataStore).appIconChanged = false
    }
}
