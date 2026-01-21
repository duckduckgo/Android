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

package com.duckduckgo.app.fire

import com.duckduckgo.app.browser.api.WebViewProfileManager
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DataClearingTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: DataClearing

    @Mock
    private lateinit var mockFireDataStore: FireDataStore

    @Mock
    private lateinit var mockClearDataAction: ClearDataAction

    @Mock
    private lateinit var mockSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockTimeKeeper: BackgroundTimeKeeper

    @Mock
    private lateinit var mockWebViewProfileManager: WebViewProfileManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        testee = DataClearing(
            fireDataStore = mockFireDataStore,
            clearDataAction = mockClearDataAction,
            settingsDataStore = mockSettingsDataStore,
            dataClearerTimeKeeper = mockTimeKeeper,
            webViewProfileManager = mockWebViewProfileManager,
        )
    }

    @Test
    fun whenManualClearWithTabsOnly_thenOnlyClearTabs() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithDataOnly_thenClearDataAndSetFlag() = runTest {
        configureManualOptions(setOf(FireClearOption.DATA))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true)

        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithTabsAndData_thenClearBothAndSetFlag() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithDuckAiChatsOnly_thenClearChatsAndSetFlag() = runTest {
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true)

        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithAllOptions_thenClearAllAndSetFlag() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithNoOptionsSelected_thenOnlySetFlag() = runTest {
        configureManualOptions(emptySet())

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true)

        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithNoOptionsSelectedAndShouldRestartProcess_thenOnlySetFlagAndDoNotRestart() = runTest {
        configureManualOptions(emptySet())

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = true)

        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithTabsOnlyAndShouldRestartProcess_thenDoNotRestart() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = true)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithDataAndShouldRestartProcess_andProfileSwitchNotAvailable_thenRestartProcess() = runTest {
        configureManualOptions(setOf(FireClearOption.DATA))
        configureProfileSwitchingUnavailable()

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false)

        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction).killAndRestartProcess(notifyDataCleared = false)
    }

    @Test
    fun whenManualClearWithTabsAndDataAndShouldRestartProcess_andProfileSwitchNotAvailable_thenRestartProcess() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))
        configureProfileSwitchingUnavailable()

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction).killAndRestartProcess(notifyDataCleared = false)
    }

    @Test
    fun whenManualClearWithDuckAiChatsAndShouldRestartProcess_andProfileSwitchNotAvailable_thenRestartProcess() = runTest {
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))
        configureProfileSwitchingUnavailable()

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false)

        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction).killAndRestartProcess(notifyDataCleared = false)
    }

    @Test
    fun whenManualClearWithDataAndShouldRestartProcess_andProfileSwitchAvailable_thenResetTabsWithoutClear() = runTest {
        configureManualOptions(setOf(FireClearOption.DATA))
        configureProfileSwitchingSuccessful()

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false)

        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockWebViewProfileManager).switchToNewProfile()
        verify(mockClearDataAction).resetTabsForProfileSwitch(clearTabs = false)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithTabsAndDataAndShouldRestartProcess_andProfileSwitchAvailable_thenResetTabsWithClear() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))
        configureProfileSwitchingSuccessful()

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockWebViewProfileManager).switchToNewProfile()
        verify(mockClearDataAction).resetTabsForProfileSwitch(clearTabs = true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithDuckAiChatsAndShouldRestartProcess_andProfileSwitchAvailable_thenResetTabsWithoutClear() = runTest {
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))
        configureProfileSwitchingSuccessful()

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false)

        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockWebViewProfileManager).switchToNewProfile()
        verify(mockClearDataAction).resetTabsForProfileSwitch(clearTabs = false)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any())
    }

    @Test
    fun whenManualClearWithDataAndShouldRestartProcess_andProfileSwitchFails_thenFallbackToRestartProcess() = runTest {
        configureManualOptions(setOf(FireClearOption.DATA))
        configureProfileSwitchingFailed()

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false)

        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockWebViewProfileManager).switchToNewProfile()
        verify(mockClearDataAction, never()).resetTabsForProfileSwitch(any())
        verify(mockClearDataAction).killAndRestartProcess(notifyDataCleared = false)
    }

    @Test
    fun whenAutomaticClearWithTabsOnlyAndKillProcessIfNeeded_thenClearTabsAndReturnFalse() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.TABS))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = true)

        assertFalse(result)
        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction, never()).killProcess()
    }

    @Test
    fun whenAutomaticClearWithTabsOnlyAndNoKillProcess_thenClearTabsAndReturnFalse() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.TABS))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = false)

        assertFalse(result)
        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killProcess()
    }

    @Test
    fun whenAutomaticClearWithDataAndKillProcessIfNeeded_thenClearDataAndKillProcess() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = true)

        assertFalse(result)
        verify(mockClearDataAction).clearBrowserDataOnly(false)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction).killProcess()
    }

    @Test
    fun whenAutomaticClearWithDataAndNoKillProcess_thenClearDataAndReturnTrue() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = false)

        assertTrue(result)
        verify(mockClearDataAction).clearBrowserDataOnly(false)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killProcess()
    }

    @Test
    fun whenAutomaticClearWithTabsAndDataAndKillProcessIfNeeded_thenClearBothAndKillProcess() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = true)

        assertFalse(result)
        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(false)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction).killProcess()
    }

    @Test
    fun whenAutomaticClearWithTabsAndDataAndNoKillProcess_thenClearBothAndReturnTrue() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = false)

        assertTrue(result)
        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(false)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killProcess()
    }

    @Test
    fun whenAutomaticClearWithDuckAiChatsAndKillProcessIfNeeded_thenClearChatsAndKillProcess() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DUCKAI_CHATS))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = true)

        assertFalse(result)
        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction).killProcess()
    }

    @Test
    fun whenAutomaticClearWithDuckAiChatsAndNoKillProcess_thenClearChatsAndReturnTrue() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DUCKAI_CHATS))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = false)

        assertTrue(result)
        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killProcess()
    }

    @Test
    fun whenAutomaticClearWithAllOptionsAndKillProcessIfNeeded_thenClearAllAndKillProcess() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = true)

        assertFalse(result)
        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(false)
        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction).killProcess()
    }

    @Test
    fun whenAutomaticClearWithNoOptionsSelectedAndKillProcessIfNeeded_thenOnlySetFlagAndReturnFalse() = runTest {
        configureAutomaticOptions(emptySet())

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = true)

        assertFalse(result)
        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction, never()).killProcess()
    }

    @Test
    fun whenAutomaticClearWithNoOptionsSelectedAndNoKillProcess_thenOnlySetFlagAndReturnFalse() = runTest {
        configureAutomaticOptions(emptySet())

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = false)

        assertFalse(result)
        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killProcess()
    }

    @Test
    fun whenNoAutomaticClearOptionsSelected_thenReturnFalse() = runTest {
        configureAutomaticOptions(emptySet())
        configureTimeKeeper(ClearWhenOption.APP_EXIT_ONLY, enoughTimePassed = true)

        val result = testee.shouldClearDataAutomatically(
            isFreshAppLaunch = true,
            appUsedSinceLastClear = true,
            appIconChanged = false,
        )

        assertFalse(result)
    }

    @Test
    fun whenAppNotUsedSinceLastClear_thenReturnFalse() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))
        configureWhenOption(ClearWhenOption.APP_EXIT_ONLY)

        val result = testee.shouldClearDataAutomatically(
            isFreshAppLaunch = true,
            appUsedSinceLastClear = false,
            appIconChanged = false,
        )

        assertFalse(result)
    }

    @Test
    fun whenFreshAppLaunchAndAppUsedSinceLastClear_thenReturnTrue() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))
        configureWhenOption(ClearWhenOption.APP_EXIT_ONLY)

        val result = testee.shouldClearDataAutomatically(
            isFreshAppLaunch = true,
            appUsedSinceLastClear = true,
            appIconChanged = false,
        )

        assertTrue(result)
    }

    @Test
    fun whenAppIconChanged_thenReturnFalse() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))
        configureWhenOption(ClearWhenOption.APP_EXIT_ONLY)

        val result = testee.shouldClearDataAutomatically(
            isFreshAppLaunch = false,
            appUsedSinceLastClear = true,
            appIconChanged = true,
        )

        assertFalse(result)
    }

    @Test
    fun whenNotFreshAppLaunchAndAppExitOnly_thenReturnFalse() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))
        configureWhenOption(ClearWhenOption.APP_EXIT_ONLY)

        val result = testee.shouldClearDataAutomatically(
            isFreshAppLaunch = false,
            appUsedSinceLastClear = true,
            appIconChanged = false,
        )

        assertFalse(result)
    }

    @Test
    fun whenNotFreshAppLaunchAndNoBackgroundTimestampRecorded_thenReturnFalse() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))
        configureWhenOption(ClearWhenOption.APP_EXIT_OR_15_MINS)
        whenever(mockSettingsDataStore.hasBackgroundTimestampRecorded()).thenReturn(false)

        val result = testee.shouldClearDataAutomatically(
            isFreshAppLaunch = false,
            appUsedSinceLastClear = true,
            appIconChanged = false,
        )

        assertFalse(result)
    }

    @Test
    fun whenNotFreshAppLaunchAndNotEnoughTimeElapsed_thenReturnFalse() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))
        configureWhenOption(ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureTimeKeeper(ClearWhenOption.APP_EXIT_OR_15_MINS, enoughTimePassed = false)

        val result = testee.shouldClearDataAutomatically(
            isFreshAppLaunch = false,
            appUsedSinceLastClear = true,
            appIconChanged = false,
        )

        assertFalse(result)
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimeElapsed_thenReturnTrue() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))
        configureWhenOption(ClearWhenOption.APP_EXIT_OR_15_MINS)
        configureTimeKeeper(ClearWhenOption.APP_EXIT_OR_15_MINS, enoughTimePassed = true)

        val result = testee.shouldClearDataAutomatically(
            isFreshAppLaunch = false,
            appUsedSinceLastClear = true,
            appIconChanged = false,
        )

        assertTrue(result)
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimeElapsedFor5Mins_thenReturnTrue() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))
        configureWhenOption(ClearWhenOption.APP_EXIT_OR_5_MINS)
        configureTimeKeeper(ClearWhenOption.APP_EXIT_OR_5_MINS, enoughTimePassed = true)

        val result = testee.shouldClearDataAutomatically(
            isFreshAppLaunch = false,
            appUsedSinceLastClear = true,
            appIconChanged = false,
        )

        assertTrue(result)
    }

    @Test
    fun whenNotFreshAppLaunchAndEnoughTimeElapsedFor60Mins_thenReturnTrue() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))
        configureWhenOption(ClearWhenOption.APP_EXIT_OR_60_MINS)
        configureTimeKeeper(ClearWhenOption.APP_EXIT_OR_60_MINS, enoughTimePassed = true)

        val result = testee.shouldClearDataAutomatically(
            isFreshAppLaunch = false,
            appUsedSinceLastClear = true,
            appIconChanged = false,
        )

        assertTrue(result)
    }

    @Test
    fun whenAutomaticClearOptionsConfigured_thenIsAutomaticDataClearingOptionSelected() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.DATA))

        val result = testee.isAutomaticDataClearingOptionSelected()

        assertTrue(result)
    }

    @Test
    fun whenNoAutomaticClearOptionsConfigured_thenShouldNotKillProcessOnExit() = runTest {
        configureAutomaticOptions(emptySet())

        val result = testee.isAutomaticDataClearingOptionSelected()

        assertFalse(result)
    }

    @Test
    fun whenAutomaticClearOptionsConfiguredWithTabsOnly_thenIsAutomaticDataClearingOptionSelected() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.TABS))

        val result = testee.isAutomaticDataClearingOptionSelected()

        assertTrue(result)
    }

    @Test
    fun whenAutomaticClearOptionsConfiguredWithMultipleOptions_thenIsAutomaticDataClearingOptionSelected() = runTest {
        configureAutomaticOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        val result = testee.isAutomaticDataClearingOptionSelected()

        assertTrue(result)
    }

    private suspend fun configureManualOptions(options: Set<FireClearOption>) {
        whenever(mockFireDataStore.getManualClearOptions()).thenReturn(options)
    }

    private suspend fun configureAutomaticOptions(options: Set<FireClearOption>) {
        whenever(mockFireDataStore.getAutomaticClearOptions()).thenReturn(options)
    }

    private suspend fun configureWhenOption(option: ClearWhenOption) {
        whenever(mockFireDataStore.getAutomaticallyClearWhenOption()).thenReturn(option)
    }

    private fun configureTimeKeeper(clearWhenOption: ClearWhenOption, enoughTimePassed: Boolean) {
        whenever(mockSettingsDataStore.hasBackgroundTimestampRecorded()).thenReturn(true)
        whenever(mockSettingsDataStore.appBackgroundedTimestamp).thenReturn(12345L)
        whenever(
            mockTimeKeeper.hasEnoughTimeElapsed(
                any(),
                eq(12345L),
                eq(clearWhenOption),
            ),
        ).thenReturn(enoughTimePassed)
    }

    private suspend fun configureProfileSwitchingUnavailable() {
        whenever(mockWebViewProfileManager.isProfileSwitchingAvailable()).thenReturn(false)
    }

    private suspend fun configureProfileSwitchingSuccessful() {
        whenever(mockWebViewProfileManager.isProfileSwitchingAvailable()).thenReturn(true)
        whenever(mockWebViewProfileManager.switchToNewProfile()).thenReturn(true)
    }

    private suspend fun configureProfileSwitchingFailed() {
        whenever(mockWebViewProfileManager.isProfileSwitchingAvailable()).thenReturn(true)
        whenever(mockWebViewProfileManager.switchToNewProfile()).thenReturn(false)
    }
}
