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

package com.duckduckgo.browser.ui.newtab.hatch

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.newtabpage.api.EscapeHatchTarget
import com.duckduckgo.newtabpage.api.EscapeHatchTargetResolver
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

@RunWith(AndroidJUnit4::class)
class NewTabReturnHatchViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockTabRepository: TabRepository = mock()
    private val mockDuckChat: DuckChat = mock()
    private val mockDuckChatInputModeState: DuckChatInputModeState = mock()
    private val inputModeCapabilityFlow = MutableStateFlow(NativeInputState.InputMode.SEARCH_AND_DUCK_AI)
    private val mockDuckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val mockNtpAfterIdleManager: NtpAfterIdleManager = mock()
    private val mockPixel: Pixel = mock()
    private val tabsFlow = MutableStateFlow<List<TabEntity>>(emptyList())
    private val mockResolver: EscapeHatchTargetResolver = mock()
    private val fireTabsFlow = MutableStateFlow<List<TabEntity>>(emptyList())
    private val mockFireTabRepository: TabRepository = mock()
    private val tabRepositoryProvider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository =
            if (mode == BrowserMode.FIRE) mockFireTabRepository else mockTabRepository
    }

    // Starts false so each test controls the idle-return rising edge that captures the snapshot.
    private val afterIdleReturnFlow = MutableStateFlow(false)
    private val nativeInputEnabledFlow = MutableStateFlow(true)
    private val returnToLastTabEnabledFlow = MutableStateFlow(true)

    private lateinit var testee: NewTabReturnHatchViewModel

    @Before
    fun setup() {
        whenever(mockTabRepository.flowTabs).thenReturn(tabsFlow)
        whenever(mockFireTabRepository.flowTabs).thenReturn(fireTabsFlow)
        whenever(mockNtpAfterIdleManager.isAfterIdleReturn).thenReturn(afterIdleReturnFlow)
        whenever(mockNtpAfterIdleManager.returnToLastTabEnabled).thenReturn(returnToLastTabEnabledFlow)
        whenever(mockDuckChat.observeNativeInputFieldUserSettingEnabled()).thenReturn(nativeInputEnabledFlow)
        whenever(mockDuckChatInputModeState.inputModeCapability).thenReturn(inputModeCapabilityFlow)

        testee = NewTabReturnHatchViewModel(
            currentTabRepository = mockTabRepository,
            tabRepositoryProvider = tabRepositoryProvider,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            duckChat = mockDuckChat,
            duckChatInputModeState = mockDuckChatInputModeState,
            duckDuckGoUrlDetector = mockDuckDuckGoUrlDetector,
            ntpAfterIdleManager = mockNtpAfterIdleManager,
            escapeHatchTargetResolver = mockResolver,
            pixel = mockPixel,
        )
    }

    // Simulates returning from idle: the last-accessed tab is present in the repository (so the
    // hatch can show it), the one-time resolver read is stubbed, and the rising edge triggers
    // the snapshot capture.
    private suspend fun returnFromIdleWith(tab: TabEntity?) {
        tabsFlow.value = listOfNotNull(tab)
        whenever(mockResolver.resolve()).thenReturn(tab?.let { EscapeHatchTarget(it.tabId, BrowserMode.REGULAR) })
        afterIdleReturnFlow.value = false
        afterIdleReturnFlow.value = true
    }

    @Test
    fun whenReturnFromIdleWithTabThenViewStateShowsTab() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        testee.viewState.test {
            returnFromIdleWith(tab)

            val state = expectMostRecentItem()
            assertTrue(state.shouldShow)
            assertEquals("Example", state.tabTitle)
            assertEquals("https://example.com", state.url)
            assertEquals("tab1", state.tabId)
            assertEquals("tab1", state.currentTabId)
        }
    }

    @Test
    fun whenReturnFromIdleWithNoTabThenViewStateHidesHatch() = runTest {
        testee.viewState.test {
            returnFromIdleWith(null)

            assertFalse(expectMostRecentItem().shouldShow)
        }
    }

    @Test
    fun whenReturnToLastTabDisabledThenHatchHiddenEvenWithTab() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")
        returnToLastTabEnabledFlow.value = false

        testee.viewState.test {
            returnFromIdleWith(tab)

            assertFalse(expectMostRecentItem().shouldShow)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun whenDontShowThisPressedThenReturnToLastTabDisabledAndPixelsFired() = runTest {
        testee.onDontShowThisPressed()
        advanceUntilIdle()

        verify(mockNtpAfterIdleManager).setReturnToLastTabEnabled(false)
        verify(mockPixel).fire(NewTabReturnHatchPixelName.HIDDEN_FROM_MENU, type = Count)
        verify(mockPixel).fire(NewTabReturnHatchPixelName.HIDDEN_FROM_MENU_DAILY, type = Daily())
    }

    @Test
    fun whenInitialStateThenTabIdIsEmptyAndHatchHidden() = runTest {
        testee.viewState.test {
            val state = expectMostRecentItem()
            assertEquals("", state.tabId)
            assertFalse(state.shouldShow)
        }
    }

    @Test
    fun whenSnapshotCapturedThenHatchDoesNotChangeWhenLastAccessedTabChanges() = runTest {
        val tab1 = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")
        val tab2 = TabEntity(tabId = "tab2", url = "https://other.com", title = "Other")

        testee.viewState.test {
            returnFromIdleWith(tab1)
            assertEquals("tab1", expectMostRecentItem().tabId)

            // A new last-accessed tab without a fresh idle-return must NOT re-emit / switch tabs.
            whenever(mockResolver.resolve()).thenReturn(EscapeHatchTarget(tab2.tabId, BrowserMode.REGULAR))
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun whenFreshIdleReturnThenRecapturesSnapshot() = runTest {
        val tab1 = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")
        val tab2 = TabEntity(tabId = "tab2", url = "https://other.com", title = "Other")

        testee.viewState.test {
            returnFromIdleWith(tab1)
            assertEquals("tab1", expectMostRecentItem().tabId)

            tabsFlow.value = listOf(tab2)
            whenever(mockResolver.resolve()).thenReturn(EscapeHatchTarget(tab2.tabId, BrowserMode.REGULAR))
            afterIdleReturnFlow.value = false
            afterIdleReturnFlow.value = true
            assertEquals("tab2", expectMostRecentItem().tabId)
        }
    }

    @Test
    fun whenNotAfterIdleReturnThenViewStateHidesHatch() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertTrue(expectMostRecentItem().shouldShow)

            afterIdleReturnFlow.value = false
            assertFalse(expectMostRecentItem().shouldShow)
        }
    }

    @Test
    fun whenLastAccessedTabHasNullTitleAndUrlThenViewStateUsesEmptyStrings() = runTest {
        val tab = TabEntity(tabId = "tab1", url = null, title = null)

        testee.viewState.test {
            returnFromIdleWith(tab)

            val state = expectMostRecentItem()
            assertTrue(state.shouldShow)
            assertEquals("", state.tabTitle)
            assertEquals("", state.url)
            assertEquals("tab1", state.tabId)
        }
    }

    @Test
    fun whenLastAccessedTabIsDuckChatUrlThenIsDuckChatIsTrue() = runTest {
        val url = "https://duck.ai/chat"
        val tab = TabEntity(tabId = "tab1", url = url, title = "Duck.ai")
        whenever(mockDuckChat.isDuckChatUrl(Uri.parse(url))).thenReturn(true)

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertTrue(expectMostRecentItem().isDuckChat)
        }
    }

    @Test
    fun whenLastAccessedTabIsRegularUrlThenIsDuckChatIsFalse() = runTest {
        val url = "https://example.com"
        val tab = TabEntity(tabId = "tab1", url = url, title = "Example")
        whenever(mockDuckChat.isDuckChatUrl(Uri.parse(url))).thenReturn(false)

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertFalse(expectMostRecentItem().isDuckChat)
        }
    }

    @Test
    fun whenLastAccessedTabHasNullUrlThenIsDuckChatIsFalse() = runTest {
        val tab = TabEntity(tabId = "tab1", url = null, title = null)

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertFalse(expectMostRecentItem().isDuckChat)
        }
    }

    @Test
    fun whenLastAccessedTabIsSerpUrlThenIsSerpIsTrue() = runTest {
        val url = "https://duckduckgo.com/?q=test"
        val tab = TabEntity(tabId = "tab1", url = url, title = "test at DuckDuckGo")
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)).thenReturn(true)

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertTrue(expectMostRecentItem().isSerp)
        }
    }

    @Test
    fun whenLastAccessedTabIsRegularUrlThenIsSerpIsFalse() = runTest {
        val url = "https://example.com"
        val tab = TabEntity(tabId = "tab1", url = url, title = "Example")
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)).thenReturn(false)

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertFalse(expectMostRecentItem().isSerp)
        }
    }

    @Test
    fun whenOnHatchPressedThenFiresReturnTabCountAndDailyPixels() = runTest {
        testee.onHatchPressed()

        verify(mockPixel).fire(NewTabReturnHatchPixelName.OPTION_SELECTED_RETURN_TAB, type = Count)
        verify(mockPixel).fire(NewTabReturnHatchPixelName.OPTION_SELECTED_RETURN_TAB_DAILY, type = Daily())
    }

    @Test
    fun whenNativeInputEnabledThenShowTabsButtonIsTrue() = runTest {
        nativeInputEnabledFlow.value = true
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertTrue(expectMostRecentItem().showTabsButton)
        }
    }

    @Test
    fun whenNativeInputDisabledThenShowTabsButtonIsFalse() = runTest {
        nativeInputEnabledFlow.value = false
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertFalse(expectMostRecentItem().showTabsButton)
        }
    }

    @Test
    fun whenNativeInputEnabledButSearchOnlyThenShowTabsButtonIsFalse() = runTest {
        nativeInputEnabledFlow.value = true
        inputModeCapabilityFlow.value = NativeInputState.InputMode.SEARCH_ONLY
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertFalse(expectMostRecentItem().showTabsButton)
        }
    }

    @Test
    fun whenTabsCountChangesThenViewStateCountStaysLive() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")
        tabsFlow.value = listOf(tab)

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertEquals(1, expectMostRecentItem().tabs)

            tabsFlow.value = listOf(tab, TabEntity(tabId = "tab2", url = "https://other.com", title = "Other"))
            assertEquals(2, expectMostRecentItem().tabs)
        }
    }

    @Test
    fun whenOnTabManagerPressedThenLaunchTabSwitcherCommandEmitted() = runTest {
        testee.commands.test {
            testee.onTabManagerPressed()

            assertEquals(NewTabReturnHatchViewModel.Command.LaunchTabSwitcher, awaitItem())
        }
    }

    @Test
    fun whenCloseTabThenMarksTabDeletableAndHidesHatch() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertTrue(expectMostRecentItem().shouldShow)

            testee.closeTab()
            advanceUntilIdle()

            assertFalse(expectMostRecentItem().shouldShow)
        }

        verify(mockTabRepository).markDeletable(listOf("tab1"))
        verify(mockTabRepository, never()).purgeDeletableTabs()
        verify(mockTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun whenCloseTabThenShowTabClosedSnackbarCommandEmittedWithTabId() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        testee.viewState.test {
            returnFromIdleWith(tab)
            expectMostRecentItem()
        }

        testee.commands.test {
            testee.closeTab()

            assertEquals(NewTabReturnHatchViewModel.Command.ShowTabClosedSnackbar("tab1"), awaitItem())
        }
    }

    @Test
    fun whenCloseTabWithEmptyCurrentTabIdThenNoSnackbarAndDoesNotMarkDeletable() = runTest {
        testee.viewState.test {
            assertFalse(expectMostRecentItem().shouldShow)

            testee.commands.test {
                testee.closeTab()
                expectNoEvents()
            }
        }

        advanceUntilIdle()
        verify(mockTabRepository, never()).markDeletable(any<List<String>>())
    }

    @Test
    fun whenOnUndoCloseTabThenUndoesDeletableAndReshowsSameTab() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertTrue(expectMostRecentItem().shouldShow)

            testee.closeTab()
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().shouldShow)

            testee.onUndoCloseTab("tab1")
            advanceUntilIdle()
            val restored = expectMostRecentItem()
            assertTrue(restored.shouldShow)
            assertEquals("tab1", restored.tabId)
        }

        verify(mockTabRepository).undoDeletable(listOf("tab1"))
        verify(mockTabRepository, never()).purgeDeletableTabs()
    }

    @Test
    fun whenOnTabClosedSnackbarDismissedThenPurgesDeletableTabs() = runTest {
        testee.onTabClosedSnackbarDismissed("tab1")
        advanceUntilIdle()

        verify(mockTabRepository).purgeDeletableTabs()
        verify(mockTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun whenSnackbarDismissedThenHatchStaysHiddenUntilFreshIdleReturn() = runTest {
        val tab1 = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        testee.viewState.test {
            returnFromIdleWith(tab1)
            assertTrue(expectMostRecentItem().shouldShow)

            testee.closeTab()
            advanceUntilIdle()
            testee.onTabClosedSnackbarDismissed("tab1")
            advanceUntilIdle()

            assertFalse(expectMostRecentItem().shouldShow)
        }
    }

    @Test
    fun whenCloseTabThenFiresCloseTabCountAndDailyPixels() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        testee.viewState.test {
            returnFromIdleWith(tab)
            expectMostRecentItem()
        }

        testee.closeTab()

        verify(mockPixel).fire(NewTabReturnHatchPixelName.OPTION_SELECTED_CLOSE_TAB, type = Count)
        verify(mockPixel).fire(NewTabReturnHatchPixelName.OPTION_SELECTED_CLOSE_TAB_DAILY, type = Daily())
    }

    @Test
    fun whenBurnTabPressedAndTabRemovedFromRepositoryThenHatchHides() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")
        tabsFlow.value = listOf(tab)

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertTrue(expectMostRecentItem().shouldShow)

            testee.onBurnTabPressed()
            tabsFlow.value = emptyList()

            // After the FireDialog burns the tab it leaves flowTabs, and visibility tracks
            // membership, so the hatch hides.
            assertFalse(expectMostRecentItem().shouldShow)
        }
    }

    @Test
    fun whenBurnTabPressedButTabStillInRepositoryThenHatchStaysVisible() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")
        tabsFlow.value = listOf(tab)

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertTrue(expectMostRecentItem().shouldShow)

            testee.onBurnTabPressed()
            advanceUntilIdle()

            // The tab is still in flowTabs (FireDialog not confirmed), so the hatch stays visible.
            expectNoEvents()
        }
    }

    @Test
    fun whenSnapshotTabRemovedFromRepositoryThenHatchHides() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")
        tabsFlow.value = listOf(tab)

        testee.viewState.test {
            returnFromIdleWith(tab)
            assertTrue(expectMostRecentItem().shouldShow)

            // The snapshot tab leaves the repository without this instance closing or burning it
            // (e.g. a second live hatch instance burned it, or an external close). Visibility must
            // track flowTabs membership, so the hatch hides the moment its tab is gone.
            tabsFlow.value = emptyList()

            assertFalse(expectMostRecentItem().shouldShow)
        }
    }

    @Test
    fun whenOnBurnTabPressedThenFiresBurnTabCountAndDailyPixels() = runTest {
        testee.onBurnTabPressed()

        verify(mockPixel).fire(NewTabReturnHatchPixelName.OPTION_SELECTED_BURN_TAB, type = Count)
        verify(mockPixel).fire(NewTabReturnHatchPixelName.OPTION_SELECTED_BURN_TAB_DAILY, type = Daily())
    }

    @Test
    fun whenOnAfterInactivityPressedThenFiresAfterInactivityCountAndDailyPixels() = runTest {
        testee.onAfterInactivityPressed()

        verify(mockPixel).fire(NewTabReturnHatchPixelName.OPTION_SELECTED_AFTER_INACTIVITY, type = Count)
        verify(mockPixel).fire(NewTabReturnHatchPixelName.OPTION_SELECTED_AFTER_INACTIVITY_DAILY, type = Daily())
    }

    @Test
    fun whenOnTabManagerPressedThenFiresTabSwitcherCountAndDailyPixels() = runTest {
        testee.onTabManagerPressed()

        verify(mockPixel).fire(NewTabReturnHatchPixelName.OPTION_SELECTED_TAB_SWITCHER, type = Count)
        verify(mockPixel).fire(NewTabReturnHatchPixelName.OPTION_SELECTED_TAB_SWITCHER_DAILY, type = Daily())
    }

    @Test
    fun whenOnTabManagerPressedThenNotifiesNtpAfterIdleManager() = runTest {
        testee.onTabManagerPressed()

        verify(mockNtpAfterIdleManager).onTabSwitcherSelected()
    }

    @Test
    fun whenFireTargetThenViewStateModeIsFireAndExistenceTracksFireRepo() = runTest {
        val fireTab = TabEntity(tabId = "f1", url = "https://secret.com", title = "Secret")
        fireTabsFlow.value = listOf(fireTab)
        whenever(mockResolver.resolve()).thenReturn(EscapeHatchTarget("f1", BrowserMode.FIRE))

        testee.viewState.test {
            afterIdleReturnFlow.value = false
            afterIdleReturnFlow.value = true

            val state = expectMostRecentItem()
            assertTrue(state.shouldShow)
            assertEquals(BrowserMode.FIRE, state.mode)
            assertEquals("f1", state.tabId)
        }
    }

    @Test
    fun whenFireTargetLeavesFireRepoThenHatchHides() = runTest {
        val fireTab = TabEntity(tabId = "f1", url = "https://secret.com", title = "Secret")
        fireTabsFlow.value = listOf(fireTab)
        whenever(mockResolver.resolve()).thenReturn(EscapeHatchTarget("f1", BrowserMode.FIRE))

        testee.viewState.test {
            afterIdleReturnFlow.value = false
            afterIdleReturnFlow.value = true
            assertTrue(expectMostRecentItem().shouldShow)

            fireTabsFlow.value = emptyList()
            assertFalse(expectMostRecentItem().shouldShow)
        }
    }

    @Test
    fun whenFireTargetThenTitleAndUrlAreNotReadFromFireTab() = runTest {
        val fireTab = TabEntity(tabId = "f1", url = "https://secret.com", title = "Secret")
        fireTabsFlow.value = listOf(fireTab)
        whenever(mockResolver.resolve()).thenReturn(EscapeHatchTarget("f1", BrowserMode.FIRE))

        testee.viewState.test {
            afterIdleReturnFlow.value = false
            afterIdleReturnFlow.value = true

            val state = expectMostRecentItem()
            assertEquals("", state.tabTitle)
            assertEquals("", state.url)
        }
    }

    @Test
    fun whenFireTargetClosedThenMarksDeletableOnFireRepoNotRegularRepo() = runTest {
        val fireTab = TabEntity(tabId = "f1", url = "https://secret.com", title = "Secret")
        fireTabsFlow.value = listOf(fireTab)
        whenever(mockResolver.resolve()).thenReturn(EscapeHatchTarget("f1", BrowserMode.FIRE))

        testee.viewState.test {
            afterIdleReturnFlow.value = false
            afterIdleReturnFlow.value = true
            assertTrue(expectMostRecentItem().shouldShow)

            testee.closeTab()
            advanceUntilIdle()
            // Consume the hide state emitted when pendingClose flips to true
            assertFalse(expectMostRecentItem().shouldShow)
        }

        verify(mockFireTabRepository).markDeletable(listOf("f1"))
        verify(mockTabRepository, never()).markDeletable(listOf("f1"))
    }

    @Test
    fun whenFireTargetClosedThenSnapshotClearedBeforeCommitThenPurgesFireRepoNotRegular() = runTest {
        val fireTab = TabEntity(tabId = "f1", url = "https://secret.com", title = "Secret")
        fireTabsFlow.value = listOf(fireTab)
        whenever(mockResolver.resolve()).thenReturn(EscapeHatchTarget("f1", BrowserMode.FIRE))

        testee.viewState.test {
            afterIdleReturnFlow.value = false
            afterIdleReturnFlow.value = true
            assertTrue(expectMostRecentItem().shouldShow)

            testee.closeTab()
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().shouldShow)

            afterIdleReturnFlow.value = false // snapshot clears before the user commits the close
            advanceUntilIdle()

            testee.onTabClosedSnackbarDismissed("f1")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify(mockFireTabRepository).markDeletable(listOf("f1"))
        verify(mockFireTabRepository).purgeDeletableTabs()
        verify(mockTabRepository, never()).purgeDeletableTabs()
    }
}
