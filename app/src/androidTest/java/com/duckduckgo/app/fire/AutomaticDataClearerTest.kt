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
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutomaticDataClearerTest {

    private lateinit var testee: AutomaticDataClearer

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockClearAction: ClearDataAction = mock()
    private val mockTimeKeeper: BackgroundTimeKeeper = mock()
    private val mockWorkManager: WorkManager = mock()
    private val pixel: Pixel = mock()
    private val dataClearerForegroundAppRestartPixel = DataClearerForegroundAppRestartPixel(InstrumentationRegistry.getInstrumentation().targetContext, pixel)

    @UiThreadTest
    @Before
    fun setup() {
        whenever(mockSettingsDataStore.hasBackgroundTimestampRecorded()).thenReturn(true)
        testee = AutomaticDataClearer(mockWorkManager, mockSettingsDataStore, mockClearAction, mockTimeKeeper, dataClearerForegroundAppRestartPixel)
    }

    /* Clear None tests */

    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runBlocking {
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runBlocking {
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runBlocking {
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runBlocking {
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runBlocking {
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runBlocking {
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenDataNotCleared() = runBlocking {
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenDataNotCleared() = runBlocking {
        configureUserOptions(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    /* Clear tabs tests */

    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenShouldClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenShouldClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearThenShouldNotClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    @Test
    fun whenNotAppExitOnlyAndFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearShouldClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsCleared()
        }
    }

    @Test
    fun whenNotAppExitOnlyAndNotFreshAppLaunchAndEnoughTimePassedAndAppUsedSinceLastClearShouldClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyTabsCleared()
        }
    }

    @Test
    fun whenNotAppExitOnlyAndFreshAppLaunchAndNotEnoughTimePassedAndAppUsedSinceLastClearShouldClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsCleared()
        }
    }

    @Test
    fun whenNotAppExitOnlyAndFreshAppLaunchAndNotEnoughTimePassedAndAppNotUsedSinceLastClearShouldNotClearTabs() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_ONLY, ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyTabsNotCleared()
        }
    }

    /* Clear Tabs and Data tests */

    @Test
    fun whenAppExitOnlyFreshAppLaunchAndEnoughTimePassedAppUsedSinceLastClearThenShouldClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyEverythingCleared()
        }
    }

    @Test
    fun whenAppExitOnlyFreshAppLaunchAndEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOnlyFreshAppLaunchAndNotEnoughTimePassedAppUsedSinceLastClearThenShouldClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyEverythingCleared()
        }
    }

    @Test
    fun whenAppExitOnlyFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndEnoughTimePassedAppUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndNotEnoughTimePassedAppUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOnlyNotFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_ONLY)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOrTimerFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndEnoughTimePassedAppUsedSinceLastClearThenShouldClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingCleared()
        }
    }

    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndNotEnoughTimePassedAppUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureNotEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenAppExitOrTimerNotFreshAppLaunchAndNotEnoughTimePassedAppNotUsedSinceLastClearThenShouldNotClear() = runBlocking<Unit> {
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureNotEnoughTimePassed()
        configureAppNotUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndIconJustChangedButAppNotUsedThenShouldNotClear() = runBlocking<Unit> {

        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureAppUsedSinceLastClear()
        configureAppIconJustChanged()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyAppIconFlagReset()
            verifyEverythingNotCleared()
        }
    }

    @Test
    fun whenFreshAppLaunchAndIconJustChangedButAppUsedThenShouldClear() = runBlocking<Unit> {

        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureAppUsedSinceLastClear()
        configureAppIconJustChanged()

        withContext(Dispatchers.Main) {
            testee.onOpen(true)
            assertTrue("isFreshAppLaunch should be true", testee.isFreshAppLaunch)
            verifyAppIconFlagReset()
            verifyEverythingCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndIconNotChangedThenShouldClear() = runBlocking<Unit> {

        configureAppIconNotChanged()
        configureAppNotUsedSinceLastClear()
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingCleared()
        }
    }

    @Test
    fun whenNotFreshAppLaunchAndIconNotChangedAppUsedThenShouldClear() = runBlocking<Unit> {

        configureAppIconNotChanged()
        configureAppUsedSinceLastClear()
        configureUserOptions(ClearWhatOption.CLEAR_TABS_AND_DATA, ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureEnoughTimePassed()
        configureAppUsedSinceLastClear()

        withContext(Dispatchers.Main) {
            testee.onOpen(false)
            assertFalse("isFreshAppLaunch should be false", testee.isFreshAppLaunch)
            verifyEverythingCleared()
        }
    }

    private fun configureUserOptions(whatOption: ClearWhatOption, whenOption: ClearWhenOption) {
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
