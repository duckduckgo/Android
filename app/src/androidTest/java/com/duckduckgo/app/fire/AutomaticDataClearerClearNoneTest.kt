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
import com.duckduckgo.app.settings.clear.ClearWhatOption.CLEAR_NONE
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_OR_15_MINS
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test

class AutomaticDataClearerClearNoneTest {

    private lateinit var testee: AutomaticDataClearer

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockClearAction: ClearDataAction = mock()
    private val mockTimeKeeper: BackgroundTimeKeeper = mock()

    @UiThreadTest
    @Before
    fun setup() {
        whenever(mockSettingsDataStore.automaticallyClearWhatOption).thenReturn(CLEAR_NONE)
        whenever(mockSettingsDataStore.automaticallyClearWhenOption).thenReturn(APP_EXIT_OR_15_MINS)
        testee = AutomaticDataClearer(mockSettingsDataStore, mockClearAction, mockTimeKeeper)
    }

    private suspend fun simulateLifecycle(isFreshAppLaunch: Boolean) {
        testee.isFreshAppLaunch = isFreshAppLaunch
        testee.onAppForegroundedAsync()
    }

    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runBlocking {
        val isFreshAppLaunch = true
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runBlocking {
        val isFreshAppLaunch = true
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runBlocking {
        val isFreshAppLaunch = true
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runBlocking {
        val isFreshAppLaunch = true
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runBlocking {
        val isFreshAppLaunch = false
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runBlocking {
        val isFreshAppLaunch = false
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runBlocking {
        val isFreshAppLaunch = false
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            simulateLifecycle(isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }


    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runBlocking {
        val isFreshAppLaunch = false
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

    private fun configureEnoughTimePassed() {
        whenever(mockTimeKeeper.hasEnoughTimeElapsed(any())).thenReturn(true)
    }

    private fun configureNotEnoughTimePassed() {
        whenever(mockTimeKeeper.hasEnoughTimeElapsed(any())).thenReturn(false)
    }

    private suspend fun verifyTabsNotCleared() {
        verify(mockClearAction, never()).clearTabsAsync(any())
    }
}
