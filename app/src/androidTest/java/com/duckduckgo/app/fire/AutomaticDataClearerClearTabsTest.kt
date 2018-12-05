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
import com.duckduckgo.app.settings.clear.ClearWhatOption.CLEAR_TABS_ONLY
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_ONLY
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_OR_15_MINS
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test

@Suppress("RemoveExplicitTypeArguments")
class AutomaticDataClearerClearTabsTest {

    private lateinit var testee: AutomaticDataClearer

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockClearAction: ClearDataAction = mock()
    private val mockTimeKeeper: BackgroundTimeKeeper = mock()

    @UiThreadTest
    @Before
    fun setup() {
        testee = AutomaticDataClearer(mockSettingsDataStore, mockClearAction, mockTimeKeeper)
    }

    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenShouldClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenShouldClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotAppExitOnlyAndFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearShouldClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsCleared()
        }
    }

    @Test
    fun whenNotAppExitOnlyAndNotFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearShouldClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = false
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsCleared()
        }
    }

    @Test
    fun whenNotAppExitOnlyAndFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearShouldClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsCleared()
        }
    }

    @Test
    fun whenNotAppExitOnlyAndFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearShouldNotClearTabs() = runBlocking<Unit> {
        val isFreshAppLaunch = true
        configureUserOptions(CLEAR_TABS_ONLY, APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
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
        whenever(mockTimeKeeper.hasEnoughTimeElapsed(any())).thenReturn(true)
    }

    private fun configureNotEnoughTimePassed() {
        whenever(mockTimeKeeper.hasEnoughTimeElapsed(any())).thenReturn(false)
    }

    private suspend fun verifyTabsCleared() {
        verify(mockClearAction).clearTabsAsync(any())
    }

    private suspend fun verifyTabsNotCleared() {
        verify(mockClearAction, never()).clearTabsAsync(any())
    }

    private suspend fun simulateLifecycle(isFreshAppLaunch: Boolean) {
        testee.isFreshAppLaunch = isFreshAppLaunch
        testee.onAppForegroundedAsync()
    }
}
