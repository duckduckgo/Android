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

@file:SuppressLint("NoImplImportsInAppModule")

package com.duckduckgo.app.fire

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.fire.promo.FireTabsPromos
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.store.TabVisitedSitesRepository
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.global.view.ClearDataResult
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabAtomicOperations
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingTrigger
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import com.duckduckgo.history.api.NavigationHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
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
    private lateinit var mockDuckAiFeatureState: DuckAiFeatureState

    @Mock
    private lateinit var mockDataClearingWideEvent: DataClearingWideEvent

    @Mock
    private lateinit var mockTabVisitedSitesRepository: TabVisitedSitesRepository

    @Mock
    private lateinit var mockNavigationHistory: NavigationHistory

    @Mock
    private lateinit var mockDuckChat: DuckChat

    /**
     * The production [TabRepository] (TabDataRepository) also implements [TabAtomicOperations], and
     * [DataClearing] casts the [BrowserModeDataProvider] result to [TabAtomicOperations] for tab
     * replacement. The mock must satisfy both interfaces, so it is typed against this combined type.
     */
    private interface TabRepositoryWithAtomicOps : TabRepository, TabAtomicOperations

    @Mock
    private lateinit var mockTabRepository: TabRepositoryWithAtomicOps

    // Tab replacement is verified on the same combined mock returned by the provider.
    private val mockTabOperations: TabAtomicOperations get() = mockTabRepository

    @Mock
    private lateinit var mockFireModeAvailability: FireModeAvailability

    @Mock
    private lateinit var mockTabRepositoryProvider: BrowserModeDataProvider<TabRepository>

    @Mock
    private lateinit var mockContextualDataStore: DuckChatContextualDataStore

    @Mock
    private lateinit var mockShowOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore

    @Mock
    private lateinit var mockDataClearingTrigger: DataClearingTrigger

    @Mock
    private lateinit var mockFireTabsPromos: FireTabsPromos

    private val showClearDuckAIChatHistoryFlow = MutableStateFlow(true)
    private val showOnAppLaunchOptionFlow = MutableStateFlow<ShowOnAppLaunchOption>(ShowOnAppLaunchOption.LastOpenedTab)

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        showClearDuckAIChatHistoryFlow.value = true
        showOnAppLaunchOptionFlow.value = ShowOnAppLaunchOption.LastOpenedTab
        whenever(mockDuckAiFeatureState.showClearDuckAIChatHistory).thenReturn(showClearDuckAIChatHistoryFlow)
        whenever(mockShowOnAppLaunchOptionDataStore.optionFlow).thenReturn(showOnAppLaunchOptionFlow)
        // Default: Fire mode unavailable, so existing Regular-only expectations stay unchanged unless a
        // test explicitly enables it.
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        whenever(mockTabRepositoryProvider.forMode(any())).thenReturn(mockTabRepository)
        runBlocking {
            whenever(mockClearDataAction.clearDataForSpecificDomains(any())).thenReturn(ClearDataResult.Success)
            whenever(mockFireDataStore.getManualClearOptions()).thenReturn(emptySet())
            whenever(mockTabRepository.getTabs()).thenReturn(emptyList())
        }
        testee = DataClearing(
            fireDataStore = mockFireDataStore,
            clearDataAction = mockClearDataAction,
            settingsDataStore = mockSettingsDataStore,
            dataClearerTimeKeeper = mockTimeKeeper,
            duckAiFeatureState = mockDuckAiFeatureState,
            dataClearingWideEvent = mockDataClearingWideEvent,
            tabVisitedSitesRepository = mockTabVisitedSitesRepository,
            navigationHistory = mockNavigationHistory,
            tabRepositoryProvider = mockTabRepositoryProvider,
            fireModeAvailability = mockFireModeAvailability,
            duckChat = mockDuckChat,
            contextualDataStore = mockContextualDataStore,
            showOnAppLaunchOptionDataStore = mockShowOnAppLaunchOptionDataStore,
            dataClearingTrigger = mockDataClearingTrigger,
            fireTabsPromos = mockFireTabsPromos,
        )
    }

    @Test
    fun whenManualClearWithTabsOnly_thenOnlyClearTabs() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun whenManualClearWithDataOnly_thenClearDataAndSetFlag() = runTest {
        configureManualOptions(setOf(FireClearOption.DATA))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun whenManualClearWithTabsAndData_thenClearBothAndSetFlag() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun whenManualClearWithDuckAiChatsOnly_thenClearChatsAndSetFlag() = runTest {
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun `manual clear with DuckAi chats dispatches DuckChats AllForMode REGULAR via trigger`() = runTest {
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockDataClearingTrigger).clearData(eq(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.REGULAR))))
        verify(mockTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun whenManualClearWithAllOptions_thenClearAllAndSetFlag() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun whenManualClearWithNoOptionsSelected_thenOnlySetFlag() = runTest {
        configureManualOptions(emptySet())

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun whenManualClearWithNoOptionsSelectedAndShouldRestartProcess_thenOnlySetFlagAndDoNotRestart() = runTest {
        configureManualOptions(emptySet())

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun whenManualClearWithTabsOnlyAndShouldRestartProcess_thenDoNotRestart() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun whenManualClearWithDataAndShouldRestartProcess_thenRestartProcess() = runTest {
        configureManualOptions(setOf(FireClearOption.DATA))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockDataClearingWideEvent).finishSuccess()
        verify(mockClearDataAction).killAndRestartProcess(notifyDataCleared = false)
    }

    @Test
    fun whenManualBurnRestartsProcess_thenUserBurnedRecordedBeforeRestart() = runTest {
        configureManualOptions(setOf(FireClearOption.DATA))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false, browserMode = BrowserMode.REGULAR)

        // The burn must be recorded before the process is killed, otherwise the NTP promo trigger is lost.
        inOrder(mockFireTabsPromos, mockClearDataAction) {
            verify(mockFireTabsPromos).onUserBurned()
            verify(mockClearDataAction).killAndRestartProcess(notifyDataCleared = false)
        }
    }

    @Test
    fun whenManualClearWithTabsAndDataAndShouldRestartProcess_thenRestartProcess() = runTest {
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockDataClearingWideEvent).finishSuccess()
        verify(mockClearDataAction).killAndRestartProcess(notifyDataCleared = false)
    }

    @Test
    fun whenManualClearWithDuckAiChatsAndShouldRestartProcess_thenRestartProcess() = runTest {
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockDataClearingWideEvent).finishSuccess()
        verify(mockClearDataAction).killAndRestartProcess(notifyDataCleared = false)
    }

    @Test
    fun whenManualClearWithDuckAiChatsButFeatureFlagDisabled_thenDoNotClearChats() = runTest {
        showClearDuckAIChatHistoryFlow.value = false
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun whenManualClearWithAllOptionsButFeatureFlagDisabled_thenClearAllExceptDuckAiChats() = runTest {
        showClearDuckAIChatHistoryFlow.value = false
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
    }

    @Test
    fun whenManualClearWithDuckAiChatsOnlyButFeatureFlagDisabledAndShouldRestartProcess_thenDoNotRestart() = runTest {
        showClearDuckAIChatHistoryFlow.value = false
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
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
    fun whenAutomaticClearWithDuckAiChatsButFeatureFlagDisabled_thenDoNotClearChats() = runTest {
        showClearDuckAIChatHistoryFlow.value = false
        configureAutomaticOptions(setOf(FireClearOption.DUCKAI_CHATS))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = false)

        assertFalse(result)
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
        verify(mockClearDataAction, never()).killProcess()
    }

    @Test
    fun whenAutomaticClearWithDuckAiChatsButFeatureFlagDisabledAndKillProcessIfNeeded_thenDoNotKillProcess() = runTest {
        showClearDuckAIChatHistoryFlow.value = false
        configureAutomaticOptions(setOf(FireClearOption.DUCKAI_CHATS))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = true)

        assertFalse(result)
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(mockClearDataAction, never()).killProcess()
    }

    @Test
    fun whenAutomaticClearWithAllOptionsButFeatureFlagDisabled_thenClearAllExceptDuckAiChats() = runTest {
        showClearDuckAIChatHistoryFlow.value = false
        configureAutomaticOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        val result = testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = false)

        assertTrue(result)
        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(false)
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
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

    // --- clearSingleTabData tests ---

    @Test
    fun whenClearSingleTabData_thenClearSiteDataWithVisitedDomains() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(setOf("example.com", "test.com"))

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearDataForSpecificDomains(eq(setOf("example.com", "test.com")))
    }

    @Test
    fun whenClearSingleTabDataWithNoVisitedSites_thenStillCallClearDataForSpecificDomains() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearDataForSpecificDomains(eq(emptySet()))
    }

    @Test
    fun whenClearSingleTabData_thenRemoveHistoryForTab() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockNavigationHistory).removeHistoryForTab("tab1")
    }

    @Test
    fun whenClearSingleTabData_thenVisitedSitesClearedByReplaceTab() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab(eq("tab1"), anyOrNull())
    }

    @Test
    fun whenClearSingleTabData_thenReplaceTab() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab(eq("tab1"), anyOrNull())
    }

    @Test
    fun whenClearSingleTabDataSucceeds_thenReturnSuccess() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())

        val result = testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        assertEquals(ClearDataResult.Success, result)
    }

    @Test
    fun whenClearSingleTabDataFeatureNotSupported_thenReturnFeatureNotSupported() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockClearDataAction.clearDataForSpecificDomains(any())).thenReturn(ClearDataResult.FeatureNotSupported)

        val result = testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        assertEquals(ClearDataResult.FeatureNotSupported, result)
    }

    @Test
    fun whenClearSingleTabDataError_thenReturnError() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        val exception = RuntimeException("test error")
        whenever(mockClearDataAction.clearDataForSpecificDomains(any())).thenReturn(ClearDataResult.Error(exception))

        val result = testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        assertEquals(ClearDataResult.Error(exception), result)
    }

    @Test
    fun whenClearSingleTabDataFeatureNotSupported_thenStillReplaceTab() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockClearDataAction.clearDataForSpecificDomains(any())).thenReturn(ClearDataResult.FeatureNotSupported)

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab(eq("tab1"), anyOrNull())
    }

    @Test
    fun whenClearSingleTabDataError_thenStillReplaceTab() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockClearDataAction.clearDataForSpecificDomains(any())).thenReturn(ClearDataResult.Error(RuntimeException("test")))

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab(eq("tab1"), anyOrNull())
    }

    @Test
    fun whenClearSingleTabDataWithDuckAiChatTab_thenDeleteChat() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(setOf("duck.ai"))
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://duck.ai/chat?chatID=abc-123", position = 0))

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(
            mockDataClearingTrigger,
        ).clearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai/chat?chatID=abc-123"), BrowserMode.REGULAR)))
    }

    @Test
    fun whenClearSingleTabDataWithNonDuckAiTab_thenDeleteChatStillCalled() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(setOf("example.com"))
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://example.com", position = 0))

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockDataClearingTrigger).clearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://example.com"), BrowserMode.REGULAR)))
    }

    @Test
    fun whenClearSingleTabDataWithDuckAiTab_thenAlwaysDeleteChatRegardlessOfManualOptions() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(setOf("duck.ai"))
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://duck.ai/chat?chatID=abc-123", position = 0))
        configureManualOptions(emptySet())

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(
            mockDataClearingTrigger,
        ).clearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai/chat?chatID=abc-123"), BrowserMode.REGULAR)))
    }

    @Test
    fun whenClearSingleTabDataWithDuckAiTab_thenAlwaysDeleteChatRegardlessOfFeatureFlag() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(setOf("duck.ai"))
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://duck.ai/chat?chatID=abc-123", position = 0))
        showClearDuckAIChatHistoryFlow.value = false

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(
            mockDataClearingTrigger,
        ).clearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai/chat?chatID=abc-123"), BrowserMode.REGULAR)))
    }

    @Test
    fun whenClearSingleTabDataWithNullTabUrl_thenDoNotDeleteChat() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(null)

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockDataClearingTrigger, never()).clearData(any())
    }

    // --- clearContextualChatDataIfNeeded tests ---

    @Test
    fun whenClearSingleTabDataWithContextualChatAndDuckAiChatsEnabled_thenDeleteContextualChat() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(null)
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))
        whenever(mockContextualDataStore.getTabChatUrl("tab1")).thenReturn("https://duck.ai/chat?chatID=contextual-123")

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(
            mockDataClearingTrigger,
        ).clearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai/chat?chatID=contextual-123"), BrowserMode.REGULAR)))
        verify(mockContextualDataStore).clearTabChatUrl("tab1")
    }

    @Test
    fun whenClearSingleTabDataWithContextualChatAndDuckAiChatsDisabled_thenDoNotDeleteContextualChat() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(null)
        configureManualOptions(emptySet())

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockContextualDataStore, never()).getTabChatUrl(any())
        verify(mockContextualDataStore, never()).clearTabChatUrl(any())
    }

    @Test
    fun whenClearSingleTabDataWithNoContextualChatUrlAndDuckAiChatsEnabled_thenStillClearStoreEntries() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(null)
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))
        whenever(mockContextualDataStore.getTabChatUrl("tab1")).thenReturn(null)

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockContextualDataStore).clearTabChatUrl("tab1")
    }

    @Test
    fun whenClearSingleTabDataWithDuckAiTabAndContextualChat_thenDeleteBothChats() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(setOf("duck.ai"))
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://duck.ai/chat?chatID=tab-chat", position = 0))
        configureManualOptions(setOf(FireClearOption.DUCKAI_CHATS))
        whenever(mockContextualDataStore.getTabChatUrl("tab1")).thenReturn("https://duck.ai/chat?chatID=contextual-456")

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(
            mockDataClearingTrigger,
        ).clearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai/chat?chatID=tab-chat"), BrowserMode.REGULAR)))
        verify(
            mockDataClearingTrigger,
        ).clearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai/chat?chatID=contextual-456"), BrowserMode.REGULAR)))
    }

    // --- getNewTabUrl tests (via clearSingleTabData) ---

    @Test
    fun whenClearSingleTabDataWithDuckChatUrl_thenReplaceTabWithNewDuckChatUrl() = runTest {
        val duckChatUrl = "https://duck.ai/chat?chatID=abc-123"
        val freshDuckChatUrl = "https://duck.ai/chat?dprompt=&autoPrompt=false"
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = duckChatUrl, position = 0))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)
        whenever(mockDuckChat.getDuckChatUrl("", autoPrompt = false)).thenReturn(freshDuckChatUrl)

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab("tab1", freshDuckChatUrl)
    }

    @Test
    fun whenClearSingleTabDataWithNonDuckChatUrlAndLastOpenedTab_thenReplaceTabWithNullUrl() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://example.com", position = 0))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(false)
        showOnAppLaunchOptionFlow.value = ShowOnAppLaunchOption.LastOpenedTab

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab(eq("tab1"), isNull())
    }

    @Test
    fun whenClearSingleTabDataWithNonDuckChatUrlAndNewTabPage_thenReplaceTabWithNullUrl() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://example.com", position = 0))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(false)
        showOnAppLaunchOptionFlow.value = ShowOnAppLaunchOption.NewTabPage

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab(eq("tab1"), isNull())
    }

    @Test
    fun whenClearSingleTabDataWithNonDuckChatUrlAndSpecificPage_thenReplaceTabWithSpecificPageUrl() = runTest {
        val specificUrl = "https://example.org/"
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://example.com", position = 0))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(false)
        showOnAppLaunchOptionFlow.value = ShowOnAppLaunchOption.SpecificPage(specificUrl)

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab("tab1", specificUrl)
    }

    @Test
    fun whenClearSingleTabDataWithNullUrlAndLastOpenedTab_thenReplaceTabWithNullUrl() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(null)
        showOnAppLaunchOptionFlow.value = ShowOnAppLaunchOption.LastOpenedTab

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab(eq("tab1"), isNull())
    }

    @Test
    fun whenClearSingleTabDataWithNullUrlAndSpecificPage_thenReplaceTabWithSpecificPageUrl() = runTest {
        val specificUrl = "https://example.org/"
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(null)
        showOnAppLaunchOptionFlow.value = ShowOnAppLaunchOption.SpecificPage(specificUrl)

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab("tab1", specificUrl)
    }

    @Test
    fun whenClearSingleTabDataWithDuckChatUrlAndSpecificPage_thenDuckChatUrlTakesPrecedence() = runTest {
        val duckChatUrl = "https://duck.ai/chat?chatID=xyz"
        val freshDuckChatUrl = "https://duck.ai/chat?dprompt=&autoPrompt=false"
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = duckChatUrl, position = 0))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)
        whenever(mockDuckChat.getDuckChatUrl("", autoPrompt = false)).thenReturn(freshDuckChatUrl)
        showOnAppLaunchOptionFlow.value = ShowOnAppLaunchOption.SpecificPage("https://example.org/")

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockTabOperations).replaceTabWithNewTab("tab1", freshDuckChatUrl)
    }

    // --- clearSingleTabData with replaceCurrentTab = false (Hatch origin) ---

    @Test
    fun whenClearSingleTabDataWithoutReplace_thenDeleteTabAndDoNotReplace() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://example.com", position = 0))

        testee.clearSingleTabData("tab1", replaceCurrentTab = false, browserMode = BrowserMode.REGULAR)

        verify(mockTabRepository).deleteTabs(listOf("tab1"))
        verify(mockTabOperations, never()).replaceTabWithNewTab(any(), anyOrNull())
    }

    @Test
    fun whenClearSingleTabDataWithoutReplaceForDuckAiTab_thenDoNotReplaceWithDuckAiUrl() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(setOf("duck.ai"))
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://duck.ai/chat?chatID=abc-123", position = 0))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)

        testee.clearSingleTabData("tab1", replaceCurrentTab = false, browserMode = BrowserMode.REGULAR)

        verify(mockTabRepository).deleteTabs(listOf("tab1"))
        verify(mockTabOperations, never()).replaceTabWithNewTab(any(), anyOrNull())
    }

    @Test
    fun whenClearSingleTabDataWithoutReplace_thenStillClearDataAndRemoveHistory() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(setOf("example.com"))
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://example.com", position = 0))

        testee.clearSingleTabData("tab1", replaceCurrentTab = false, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearDataForSpecificDomains(eq(setOf("example.com")))
        verify(mockNavigationHistory).removeHistoryForTab("tab1")
    }

    // --- clearSelectedDuckAiChats ---

    @Test
    fun `clearSelectedDuckAiChats with urls dispatches Selected via trigger`() = runTest {
        val urls = setOf("https://duck.ai?chatID=a", "https://duck.ai?chatID=b")

        testee.clearSelectedDuckAiChats(urls, BrowserMode.REGULAR)

        verify(mockDataClearingTrigger).clearData(eq(setOf(ClearableData.DuckChats.SelectedForMode(urls, BrowserMode.REGULAR))))
    }

    @Test
    fun `clearSelectedDuckAiChats does not wipe DuckAi web storage`() = runTest {
        testee.clearSelectedDuckAiChats(setOf("https://duck.ai?chatID=a"), BrowserMode.REGULAR)

        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
    }

    @Test
    fun `clearSelectedDuckAiChats does not touch tabs or browser data`() = runTest {
        testee.clearSelectedDuckAiChats(setOf("https://duck.ai?chatID=a"), BrowserMode.REGULAR)

        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun `clearSelectedDuckAiChats does not touch fire button orchestration flags`() = runTest {
        testee.clearSelectedDuckAiChats(setOf("https://duck.ai?chatID=a"), BrowserMode.REGULAR)

        verify(mockClearDataAction, never()).setAppUsedSinceLastClearFlag(any())
        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun `clearSelectedDuckAiChats with empty set is a no-op`() = runTest {
        testee.clearSelectedDuckAiChats(emptySet(), BrowserMode.REGULAR)

        verify(mockDataClearingTrigger, never()).clearData(any())
    }

    @Test
    fun `clearSelectedDuckAiChats with feature flag off is a no-op`() = runTest {
        showClearDuckAIChatHistoryFlow.value = false

        testee.clearSelectedDuckAiChats(setOf("https://duck.ai?chatID=a"), BrowserMode.REGULAR)

        verify(mockDataClearingTrigger, never()).clearData(any())
    }

    // --- Mode-aware granular clear contract: Regular vs Fire + Fire-availability gating ---

    @Test
    fun `regular burn with fire available clears regular legacy and dispatches the full fire set`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(true)
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        // Legacy Regular path runs based on the selected options.
        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        // The full Fire set is always dispatched when Fire is available, regardless of which options
        // were selected (note DUCKAI_CHATS was not selected, yet the Fire chat type is dispatched).
        verify(mockDataClearingTrigger).clearData(
            eq(
                setOf(
                    ClearableData.Tabs.AllForMode(BrowserMode.FIRE),
                    ClearableData.BrowserData.AllForMode(BrowserMode.FIRE),
                    ClearableData.DuckChats.AllForMode(BrowserMode.FIRE),
                ),
            ),
        )
    }

    @Test
    fun `regular burn with all options dispatches both the regular chat set and the full fire set`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(true)
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        verify(mockClearDataAction).clearDuckAiChatsOnly()
        // Regular chats go through the legacy path + AllForMode(REGULAR) dispatch.
        verify(mockDataClearingTrigger).clearData(eq(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.REGULAR))))
        // Fire-scoped data is dispatched as the full set.
        verify(mockDataClearingTrigger).clearData(
            eq(
                setOf(
                    ClearableData.Tabs.AllForMode(BrowserMode.FIRE),
                    ClearableData.BrowserData.AllForMode(BrowserMode.FIRE),
                    ClearableData.DuckChats.AllForMode(BrowserMode.FIRE),
                ),
            ),
        )
    }

    @Test
    fun `fire burn does not run the legacy regular path and only dispatches the fire set`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(true)
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.FIRE)

        // Legacy Regular path is skipped entirely — Regular data is untouched.
        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        // No Regular-scoped chat dispatch.
        verify(mockDataClearingTrigger, never()).clearData(eq(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.REGULAR))))
        // Only the Fire set is dispatched.
        verify(mockDataClearingTrigger).clearData(
            eq(
                setOf(
                    ClearableData.Tabs.AllForMode(BrowserMode.FIRE),
                    ClearableData.BrowserData.AllForMode(BrowserMode.FIRE),
                    ClearableData.DuckChats.AllForMode(BrowserMode.FIRE),
                ),
            ),
        )
    }

    @Test
    fun `regular burn with fire unavailable does not dispatch any fire set`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.REGULAR)

        // Legacy Regular path still runs.
        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(true)
        // No Fire dispatch at all (Tabs+BrowserData would have been the FIRE set).
        verify(mockDataClearingTrigger, never()).clearData(any())
    }

    @Test
    fun `fire burn with fire unavailable is a no-op for both paths`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.FIRE)

        // Regular path skipped (Fire burn) and Fire path gated off (unavailable).
        verify(mockClearDataAction, never()).clearTabsOnly()
        verify(mockClearDataAction, never()).clearBrowserDataOnly(any())
        verify(mockClearDataAction, never()).clearDuckAiChatsOnly()
        verify(mockDataClearingTrigger, never()).clearData(any())
    }

    @Test
    fun `fire burn dispatches the full fire set even when only a subset of options is selected`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(true)
        // Only TABS selected, yet a Fire burn always wipes the whole Fire profile.
        configureManualOptions(setOf(FireClearOption.TABS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.FIRE)

        verify(mockDataClearingTrigger).clearData(
            eq(
                setOf(
                    ClearableData.Tabs.AllForMode(BrowserMode.FIRE),
                    ClearableData.BrowserData.AllForMode(BrowserMode.FIRE),
                    ClearableData.DuckChats.AllForMode(BrowserMode.FIRE),
                ),
            ),
        )
    }

    @Test
    fun `fire burn restarts the process when requested regardless of the selected options`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(true)
        // TABS alone never triggers a restart in a Regular burn, but a Fire burn always restarts.
        configureManualOptions(setOf(FireClearOption.TABS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, wasAppUsedSinceLastClear = false, browserMode = BrowserMode.FIRE)

        verify(mockDataClearingWideEvent).finishSuccess()
        verify(mockClearDataAction).killAndRestartProcess(notifyDataCleared = false)
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(false)
    }

    @Test
    fun `fire burn does not restart the process when not requested`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(true)
        configureManualOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingManualFireOptions(shouldRestartIfRequired = false, wasAppUsedSinceLastClear = true, browserMode = BrowserMode.FIRE)

        verify(mockClearDataAction, never()).killAndRestartProcess(any(), any(), any())
        verify(mockClearDataAction).setAppUsedSinceLastClearFlag(true)
    }

    @Test
    fun `automatic clear runs the regular legacy path and dispatches the full fire set when available`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(true)
        configureAutomaticOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = false)

        // Automatic callers run the Regular legacy path based on the selected options...
        verify(mockClearDataAction).clearTabsOnly()
        verify(mockClearDataAction).clearBrowserDataOnly(false)
        // ...and always dispatch the full Fire set when Fire mode is available, regardless of which
        // options were selected (note DUCKAI_CHATS was not selected, yet the Fire chat type is dispatched).
        verify(mockDataClearingTrigger).clearData(
            eq(
                setOf(
                    ClearableData.Tabs.AllForMode(BrowserMode.FIRE),
                    ClearableData.BrowserData.AllForMode(BrowserMode.FIRE),
                    ClearableData.DuckChats.AllForMode(BrowserMode.FIRE),
                ),
            ),
        )
    }

    @Test
    fun `fire single tab clear dispatches site-data plugin and returns success`() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(setOf("example.com"))
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://example.com", position = 0))

        val result = testee.clearSingleTabData("tab1", browserMode = BrowserMode.FIRE)

        // Fire per-site clear is delegated to the site-data plugin via the trigger.
        verify(mockDataClearingTrigger).clearData(setOf(ClearableData.BrowserData.SingleForMode("tab1", BrowserMode.FIRE)))
        // Regular per-site path is not used in Fire mode.
        verify(mockClearDataAction, never()).clearDataForSpecificDomains(any())
        assertEquals(ClearDataResult.Success, result)
    }

    @Test
    fun `fire single tab clear resolves tab repository for fire mode and replaces tab`() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://example.com", position = 0))

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.FIRE)

        verify(mockTabRepositoryProvider).forMode(BrowserMode.FIRE)
        verify(mockTabOperations).replaceTabWithNewTab(eq("tab1"), anyOrNull())
    }

    @Test
    fun `fire single tab clear dispatches chat clear scoped to fire mode`() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(setOf("duck.ai"))
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://duck.ai/chat?chatID=abc", position = 0))

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.FIRE)

        verify(
            mockDataClearingTrigger,
        ).clearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai/chat?chatID=abc"), BrowserMode.FIRE)))
    }

    @Test
    fun `clearSelectedDuckAiChats in fire mode dispatches Selected scoped to fire`() = runTest {
        val urls = setOf("https://duck.ai?chatID=a")

        testee.clearSelectedDuckAiChats(urls, BrowserMode.FIRE)

        verify(mockDataClearingTrigger).clearData(eq(setOf(ClearableData.DuckChats.SelectedForMode(urls, BrowserMode.FIRE))))
    }

    // ---------------------------------------------------------------------------------
    // G1 gap tests — flag-off safety-net: none of these paths should ever dispatch
    // fire-scoped data when FireModeAvailability.isAvailable() returns false.
    // ---------------------------------------------------------------------------------

    @Test
    fun `automatic clear with flag off never dispatches fire-scoped data`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        configureAutomaticOptions(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        testee.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = false)

        verify(mockDataClearingTrigger, never()).clearData(argThat { hasFireScopedType() })
    }

    @Test
    fun `single tab clear with flag off never dispatches fire-scoped data`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://example.com", position = 0))

        testee.clearSingleTabData("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockDataClearingTrigger, never()).clearData(argThat { hasFireScopedType() })
    }

    @Test
    fun `clearSelectedDuckAiChats with flag off never dispatches fire-scoped data`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        val urls = setOf("https://duck.ai?chatID=x")

        testee.clearSelectedDuckAiChats(urls, BrowserMode.REGULAR)

        verify(mockDataClearingTrigger, never()).clearData(argThat { hasFireScopedType() })
    }

    @Test
    fun `clearTabContextualChat with flag off never dispatches fire-scoped data`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        whenever(mockContextualDataStore.getTabChatUrl("tab1")).thenReturn("https://duck.ai?chatID=x")

        testee.clearTabContextualChat("tab1", browserMode = BrowserMode.REGULAR)

        verify(mockDataClearingTrigger, never()).clearData(argThat { hasFireScopedType() })
    }

    // --- FireTabsPromos recording ---

    @Test
    fun whenManualBurnInRegularModeThenUserBurnedRecorded() = runTest {
        testee.clearDataUsingManualFireOptions(browserMode = BrowserMode.REGULAR)
        verify(mockFireTabsPromos).onUserBurned()
    }

    @Test
    fun whenManualBurnInFireModeThenUserBurnedNotRecorded() = runTest {
        testee.clearDataUsingManualFireOptions(browserMode = BrowserMode.FIRE)
        verify(mockFireTabsPromos, never()).onUserBurned()
    }

    @Test
    fun whenSingleTabBurnInRegularModeThenUserBurnedRecorded() = runTest {
        whenever(mockClearDataAction.clearDataForSpecificDomains(any())).thenReturn(ClearDataResult.Success)
        testee.clearSingleTabData(tabId = "tab-1", replaceCurrentTab = false, browserMode = BrowserMode.REGULAR)
        verify(mockFireTabsPromos).onUserBurned()
    }

    @Test
    fun whenSingleTabBurnInFireModeThenUserBurnedNotRecorded() = runTest {
        whenever(mockTabVisitedSitesRepository.getVisitedSites("tab-1")).thenReturn(emptySet())
        whenever(mockTabRepository.getTab("tab-1")).thenReturn(null)
        testee.clearSingleTabData(tabId = "tab-1", replaceCurrentTab = false, browserMode = BrowserMode.FIRE)
        verify(mockFireTabsPromos, never()).onUserBurned()
    }

    private fun Set<ClearableData>.hasFireScopedType() = any { t ->
        (t is ClearableData.BrowserData.AllForMode && t.mode == BrowserMode.FIRE) ||
            (t is ClearableData.BrowserData.SingleForMode && t.mode == BrowserMode.FIRE) ||
            (t is ClearableData.Tabs.AllForMode && t.mode == BrowserMode.FIRE) ||
            (t is ClearableData.Tabs.SingleForMode && t.mode == BrowserMode.FIRE) ||
            (t is ClearableData.DuckChats.AllForMode && t.mode == BrowserMode.FIRE) ||
            (t is ClearableData.DuckChats.SelectedForMode && t.mode == BrowserMode.FIRE)
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
}
