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
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

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

    private lateinit var testee: TabSwitcherViewModel

    private val repoDeletableTabs = Channel<List<TabEntity>>()
    private val tabs = MutableLiveData<List<TabEntity>>()

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        runBlocking {
            whenever(mockTabRepository.flowDeletableTabs)
                .thenReturn(repoDeletableTabs.consumeAsFlow())
            whenever(mockTabRepository.liveTabs)
                .thenReturn(tabs)
            whenever(mockTabRepository.add()).thenReturn("TAB_ID")
            testee = TabSwitcherViewModel(
                mockTabRepository,
                mockWebViewSessionStorage,
                mockAdClickManager,
                coroutinesTestRule.testDispatcherProvider,
                mockPixel,
            )
            testee.command.observeForever(mockCommandObserver)
        }
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
}
