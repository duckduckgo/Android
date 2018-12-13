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

package com.duckduckgo.app.fire

import androidx.test.annotation.UiThreadTest
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhatOption.CLEAR_TABS_AND_DATA
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_ONLY
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_OR_5_MINS
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test

@Suppress("RemoveExplicitTypeArguments")
class AutomaticDataClearerTabsAndDataTest {

    private lateinit var testee: AutomaticDataClearer

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockClearAction: ClearDataAction = mock()
    private val mockTimeKeeper: BackgroundTimeKeeper = mock()


    @UiThreadTest
    @Before
    fun setup() {
        whenever(mockSettingsDataStore.hasBackgroundTimestampRecorded()).thenReturn(true)
        testee = AutomaticDataClearer(mockSettingsDataStore, mockClearAction, mockTimeKeeper)
    }

    @Test
    fun whenAppExitOnlyFreshAppLaunchAndEnoughTimePassedAppUsedSinceLastClearThenShouldClear() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingCleared()
        }
    }

    @Test
    fun whenAppExitOnlyFreshAppLaunchAndEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOnlyFreshAppLaunchAndNotEnoughTimePassedAppUsedSinceLastClearThenShouldClear() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingCleared()
        }
    }

    @Test
    fun whenAppExitOnlyFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndEnoughTimePassedAppUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndNotEnoughTimePassedAppUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOrTimerFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_OR_5_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndEnoughTimePassedAppUsedSinceLastClearThenShouldClear() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_OR_5_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingCleared()
        }
    }

    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_OR_5_MINS)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndNotEnoughTimePassedAppUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_OR_5_MINS)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_AND_DATA, APP_EXIT_OR_5_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    private fun configureAppUsedSinceLastClear() {
        whenever(mockSettingsDataStore.appUsedSinceLastClear).thenReturn(true)
    }

    private fun configureAppNotUsedSinceLastClear() {
        whenever(mockSettingsDataStore.appUsedSinceLastClear).thenReturn(false)
    }

    private fun configureUserOptions(whatOption: ClearWhatOption, whenOption: ClearWhenOption) {
        whenever(mockSettingsDataStore.automaticallyClearWhenOption).thenReturn(whenOption)
        whenever(mockSettingsDataStore.automaticallyClearWhatOption).thenReturn(whatOption)
    }

    private fun configureEnoughTimePassed() {
        whenever(mockTimeKeeper.hasEnoughTimeElapsed(any(), any(), any())).thenReturn(true)
    }

    private fun configureNotEnoughTimePassed() {
        whenever(mockTimeKeeper.hasEnoughTimeElapsed(any(), any(), any())).thenReturn(false)
    }

    private suspend fun verifyEverythingCleared() {
        verify(mockClearAction).clearTabsAndAllDataAsync(any(), any())
    }

    private suspend fun verifyEverythingNotCleared() {
        verify(mockClearAction, never()).clearTabsAndAllDataAsync(any(), any())
    }

    private suspend fun simulateLifecycle(isFreshAppLaunch: Boolean) {
        testee.isFreshAppLaunch = isFreshAppLaunch
        testee.onAppForegroundedAsync()
    }
}
