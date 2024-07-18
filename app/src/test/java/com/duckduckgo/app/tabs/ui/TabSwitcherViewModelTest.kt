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

package com.duckduckgo.app.tabs.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import app.cash.turbine.test
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.DAILY
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData
import com.duckduckgo.app.tabs.model.TabSwitcherData.UserState.NEW
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TabSwitcherViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock
    private lateinit var mockCommandObserver: Observer<Command>

    private val commandCaptor = argumentCaptor<Command>()

    @Mock
    private lateinit var mockTabRepository: TabRepository

    @Mock
    private lateinit var mockWebViewSessionStorage: WebViewSessionStorage

    @Mock
    private lateinit var mockAdClickManager: AdClickManager

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var statisticsDataStore: StatisticsDataStore

    private lateinit var testee: TabSwitcherViewModel

    private val repoDeletableTabs = Channel<List<TabEntity>>()
    private val tabs = MutableLiveData<List<TabEntity>>()

    private val tabSwitcherData = TabSwitcherData(NEW, false, 0)
    private val flowTabs = flowOf(listOf(TabEntity("1", position = 1), TabEntity("2", position = 2)))

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockTabRepository.flowDeletableTabs)
            .thenReturn(repoDeletableTabs.consumeAsFlow())
        whenever(mockTabRepository.liveTabs)
            .thenReturn(tabs)
        runBlocking {
            whenever(mockTabRepository.add()).thenReturn("TAB_ID")
        }
        whenever(mockTabRepository.tabSwitcherData).thenReturn(flowOf(tabSwitcherData))
        whenever(mockTabRepository.flowTabs).thenReturn(flowTabs)
        whenever(statisticsDataStore.variant).thenReturn("")

        initializeViewModel()
    }

    private fun initializeViewModel() {
        testee = TabSwitcherViewModel(
            mockTabRepository,
            mockWebViewSessionStorage,
            mockAdClickManager,
            coroutinesTestRule.testDispatcherProvider,
            mockPixel,
            statisticsDataStore,
        )
        testee.command.observeForever(mockCommandObserver)
    }

    @After
    fun after() {
        repoDeletableTabs.close()
    }

    @Test
    fun whenNewTabRequestedFromOverflowMenuThenRepositoryNotifiedAndSwitcherClosedAndPixelSent() = runTest {
        testee.onNewTabRequested(fromOverflowMenu = true)
        verify(mockTabRepository).add()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_NEW_TAB_PRESSED)
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenNewTabRequestedFromIconThenRepositoryNotifiedAndSwitcherClosedAndPixelSent() = runTest {
        testee.onNewTabRequested(fromOverflowMenu = false)
        verify(mockTabRepository).add()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_NEW_TAB_CLICKED)
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenTabSelectedThenRepositoryNotifiedAndSwitcherClosedAndPixelSent() = runTest {
        testee.onTabSelected(TabEntity("abc", "", "", position = 0))
        verify(mockTabRepository).select(eq("abc"))
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SWITCH_TABS)
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenTabDeletedThenRepositoryNotified() = runTest {
        val entity = TabEntity("abc", "", "", position = 0)
        testee.onTabDeleted(entity)
        verify(mockTabRepository).delete(entity)
        verify(mockAdClickManager).clearTabId(entity.tabId)
    }

    @Test
    fun whenOnMarkTabAsDeletableAfterSwipeGestureUsedThenCallMarkDeletableAndSendPixel() = runTest {
        val swipeGestureUsed = true
        val entity = TabEntity("abc", "", "", position = 0)

        testee.onMarkTabAsDeletable(entity, swipeGestureUsed)

        verify(mockTabRepository).markDeletable(entity)
        verify(mockAdClickManager).clearTabId(entity.tabId)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_SWIPED)
    }

    @Test
    fun whenOnMarkTabAsDeletableAfterClosePressedThenCallMarkDeletableAndSendPixel() = runTest {
        val swipeGestureUsed = false
        val entity = TabEntity("abc", "", "", position = 0)

        testee.onMarkTabAsDeletable(entity, swipeGestureUsed)

        verify(mockTabRepository).markDeletable(entity)
        verify(mockAdClickManager).clearTabId(entity.tabId)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_CLICKED)
    }

    @Test
    fun whenUndoDeletableTabThenUndoDeletable() = runTest {
        val entity = TabEntity("abc", "", "", position = 0)
        testee.undoDeletableTab(entity)

        verify(mockTabRepository).undoDeletable(entity)
    }

    @Test
    fun whenPurgeDeletableTabsThenCallRepositoryPurgeDeletableTabs() = runTest {
        testee.purgeDeletableTabs()

        verify(mockTabRepository).purgeDeletableTabs()
    }

    @Test
    fun whenRepositoryDeletableTabsUpdatesThenDeletableTabsEmits() = runTest {
        val tab = TabEntity("ID", position = 0)

        val expectedTabs = listOf(listOf(), listOf(tab))
        var index = 0
        testee.deletableTabs.observeForever {
            assertEquals(expectedTabs[index++], it)
        }

        repoDeletableTabs.send(listOf())
        repoDeletableTabs.send(listOf(tab))
    }

    @Test
    fun whenRepositoryDeletableTabsEmitsSameValueThenDeletableTabsEmitsAll() = runTest {
        val tab = TabEntity("ID", position = 0)

        testee.deletableTabs.observeForever {
            assertEquals(listOf(tab), it)
        }

        repoDeletableTabs.send(listOf(tab))
        repoDeletableTabs.send(listOf(tab))
    }

    @Test
    fun whenOnCloseAllTabsRequestedThenEmitCommandCloseAllTabsRequest() = runTest {
        testee.onCloseAllTabsRequested()

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_PRESSED)
        assertEquals(Command.CloseAllTabsRequest, commandCaptor.lastValue)
    }

    @Test
    fun whenOnCloseAllTabsConfirmedThenTabDeletedAndTabIdClearedAndSessionDeletedAndPixelFired() = runTest {
        val tab = TabEntity("ID", position = 0)
        tabs.postValue(listOf(tab))

        testee.onCloseAllTabsConfirmed()

        testee.tabs.observeForever {
            runBlocking {
                verify(mockTabRepository).delete(tab)
            }
            verify(mockAdClickManager).clearTabId(tab.tabId)
            verify(mockWebViewSessionStorage).deleteSession(tab.tabId)
        }
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED)
    }

    @Test
    fun whenOnUpButtonPressedCalledThePixelSent() {
        testee.onUpButtonPressed()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_UP_BUTTON_PRESSED)
    }

    @Test
    fun whenOnBackButtonPressedCalledThePixelSent() {
        testee.onBackButtonPressed()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_BACK_BUTTON_PRESSED)
    }

    @Test
    fun whenOnMenuOpenedCalledThePixelSent() {
        testee.onMenuOpened()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_PRESSED)
    }

    @Test
    fun whenOnDownloadsMenuPressedCalledThePixelSent() {
        testee.onDownloadsMenuPressed()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_DOWNLOADS_PRESSED)
    }

    @Test
    fun whenOnSettingsMenuPressedCalledThePixelSent() {
        testee.onSettingsMenuPressed()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_SETTINGS_PRESSED)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun whenOnDraggingStartedAnnouncementDismissedAndThePixelSent() = runTest {
        testee.onTabDraggingStarted()

        advanceUntilIdle()

        verify(mockTabRepository).setWasAnnouncementDismissed(true)

        val params = mapOf("userState" to NEW.name)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_REARRANGE_TABS, params, emptyMap(), COUNT)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_REARRANGE_TABS_DAILY, params, emptyMap(), DAILY)
    }

    @Test
    fun whenOnTabFeatureAnnouncementDisplayedAnnouncementCountIncremented() = runTest {
        val initialCount = 0
        val expectedCount = initialCount + 1

        testee.onTabFeatureAnnouncementDisplayed()

        verify(mockTabRepository).setAnnouncementDisplayCount(expectedCount)
    }

    @Test
    fun onFeatureAnnouncementCloseButtonTapped_announcementIsMarkedAsDismissed() = runTest {
        testee.onFeatureAnnouncementCloseButtonTapped()

        verify(mockTabRepository).setWasAnnouncementDismissed(true)
    }

    @Test
    fun whenOnTabMovedRepositoryUpdatesTabPosition() = runTest {
        val fromIndex = 0
        val toIndex = 2

        testee.onTabMoved(fromIndex, toIndex)

        verify(mockTabRepository).updateTabPosition(fromIndex, toIndex)
    }

    @Test
    fun isFeatureAnnouncementVisible_ExistingUser_NotDismissed_BelowMaxCount_MultipleTabs() = runTest {
        whenever(mockTabRepository.tabSwitcherData).thenReturn(flowOf(TabSwitcherData(TabSwitcherData.UserState.EXISTING, false, 2)))

        // we need to use the new stubbing here
        initializeViewModel()

        testee.isFeatureAnnouncementVisible.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun isFeatureAnnouncementVisible_ReturningUser_NotDismissed_BelowMaxCount_MultipleTabs() = runTest {
        whenever(statisticsDataStore.variant).thenReturn("ru")

        // we need to use the new stubbing here
        initializeViewModel()

        testee.isFeatureAnnouncementVisible.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun isFeatureAnnouncementVisible_NewUser() = runTest {
        val isVisible = testee.isFeatureAnnouncementVisible.value
        assertFalse(isVisible)
    }

    @Test
    fun isFeatureAnnouncementVisible_Dismissed() = runTest {
        `when`(mockTabRepository.tabSwitcherData).thenReturn(flowOf(TabSwitcherData(TabSwitcherData.UserState.EXISTING, true, 0)))

        val isVisible = testee.isFeatureAnnouncementVisible.value
        assertFalse(isVisible)
    }

    @Test
    fun isFeatureAnnouncementVisible_AboveMaxDisplayCount() = runTest {
        `when`(mockTabRepository.tabSwitcherData).thenReturn(flowOf(TabSwitcherData(TabSwitcherData.UserState.EXISTING, false, 4)))

        val isVisible = testee.isFeatureAnnouncementVisible.value
        assertFalse(isVisible)
    }

    @Test
    fun isFeatureAnnouncementVisible_SingleTab() = runTest {
        val data = TabSwitcherData(TabSwitcherData.UserState.EXISTING, false, 0)

        `when`(mockTabRepository.tabSwitcherData).thenReturn(flowOf(data))
        `when`(mockTabRepository.flowTabs).thenReturn(flowOf(listOf(TabEntity("1", position = 1))))

        val isVisible = testee.isFeatureAnnouncementVisible.value
        assertFalse(isVisible)
    }
}
