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

import android.annotation.SuppressLint
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.liveData
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.model.TabSwitcherData.UserState
import com.duckduckgo.app.tabs.model.TabSwitcherData.UserState.EXISTING
import com.duckduckgo.app.tabs.model.TabSwitcherData.UserState.NEW
import com.duckduckgo.app.tabs.store.TabSwitcherDataStore
import com.duckduckgo.app.tabs.store.TabSwitcherPrefsDataStore
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.SelectableTab
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.BackButtonType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.DynamicInterface
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.FabType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.LayoutMode
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Normal
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Selection
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedAppRepository
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.blockingObserve
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.tabs.SwipingTabsFeature
import com.duckduckgo.common.ui.tabs.SwipingTabsFeatureProvider
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@OptIn(ExperimentalCoroutinesApi::class)
class TabSwitcherViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock
    private lateinit var mockCommandObserver: Observer<Command>

    @Mock
    private lateinit var mockTabSwitcherItemsObserver: Observer<List<TabSwitcherItem>>

    private val commandCaptor = argumentCaptor<Command>()

    @Mock
    private lateinit var mockTabRepository: TabRepository

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var statisticsDataStore: StatisticsDataStore

    @Mock
    private lateinit var duckChatMock: DuckChat

    @Mock
    private lateinit var duckAiFeatureStateMock: DuckAiFeatureState

    @Mock
    private lateinit var faviconManager: FaviconManager

    @Mock
    private lateinit var savedSitesRepository: SavedSitesRepository

    @Mock
    private lateinit var mockWebTrackersBlockedAppRepository: WebTrackersBlockedAppRepository

    @Mock
    private lateinit var mockTabSwitcherPrefsDataStore: TabSwitcherPrefsDataStore

    @Mock
    private lateinit var mockTabSwitcherAnimationInfoPanelPixels: TabSwitcherAnimationInfoPanelPixels

    private val tabManagerFeatureFlags = FakeFeatureToggleFactory.create(TabManagerFeatureFlags::class.java)
    private val swipingTabsFeature = FakeFeatureToggleFactory.create(SwipingTabsFeature::class.java)
    private val swipingTabsFeatureProvider = SwipingTabsFeatureProvider(swipingTabsFeature)

    private lateinit var testee: TabSwitcherViewModel

    private var tabList = listOf(
        TabEntity("1", url = "https://cnn.com", position = 1),
        TabEntity("2", url = "http://test.com", position = 2),
        TabEntity("3", position = 3),
    )
    private val tabSwitcherItems
        get() = tabList.map { NormalTab(it, false) }
    private val repoDeletableTabs = Channel<List<TabEntity>>()
    private val tabSwitcherData = TabSwitcherData(NEW, GRID)

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        swipingTabsFeature.self().setRawStoredState(State(enable = false))
        tabManagerFeatureFlags.multiSelection().setRawStoredState(State(enable = false))
        tabManagerFeatureFlags.newToolbarFeature().setRawStoredState(State(enable = false))
        swipingTabsFeature.enabledForUsers().setRawStoredState(State(enable = true))

        whenever(mockTabSwitcherPrefsDataStore.isAnimationTileDismissed()).thenReturn(flowOf(false))
        whenever(statisticsDataStore.variant).thenReturn("")
        whenever(mockTabRepository.flowDeletableTabs).thenReturn(repoDeletableTabs.consumeAsFlow())
        runBlocking {
            whenever(mockTabRepository.add()).thenReturn("TAB_ID")
            whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(0)
        }
        whenever(mockTabRepository.tabSwitcherData).thenReturn(flowOf(tabSwitcherData))

        whenever(duckAiFeatureStateMock.showOmnibarShortcutOnNtpAndOnFocus).thenReturn(MutableStateFlow(false))

        initializeMockTabEntitesData()
        initializeViewModel()
    }

    private fun initializeMockTabEntitesData() {
        whenever(mockTabRepository.flowTabs).thenReturn(flowOf(tabList))
        whenever(mockTabRepository.liveSelectedTab).thenReturn(liveData { tabList.first() })
        whenever(mockTabRepository.flowSelectedTab).thenReturn(flowOf(tabList.first()))
    }

    private fun initializeViewModel(tabSwitcherDataStore: TabSwitcherDataStore = mockTabSwitcherPrefsDataStore) {
        testee = TabSwitcherViewModel(
            mockTabRepository,
            coroutinesTestRule.testDispatcherProvider,
            mockPixel,
            swipingTabsFeatureProvider,
            duckChatMock,
            duckAiFeatureState = duckAiFeatureStateMock,
            tabManagerFeatureFlags,
            mockWebTrackersBlockedAppRepository,
            tabSwitcherDataStore,
            faviconManager,
            savedSitesRepository,
            mockTabSwitcherAnimationInfoPanelPixels,
        )
        testee.command.observeForever(mockCommandObserver)
        testee.tabSwitcherItemsLiveData.observeForever(mockTabSwitcherItemsObserver)
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
    fun whenNewTabRequestedUsingFabThenRepositoryNotifiedAndSwitcherClosedAndPixelSent() = runTest {
        prepareSelectionMode()

        testee.onFabClicked()

        verify(mockTabRepository).add()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_NEW_TAB_CLICKED)
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenAllTabsClosedUsingFabThenViewStateUpdatedAndCommandSentAndPixelSent() = runTest {
        prepareSelectionMode()

        testee.onSelectAllTabs()
        assertEquals(testee.selectionViewState.value.mode, Selection(selectedTabs = tabSwitcherItems.map { it.id }))

        testee.onFabClicked()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TABS)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TABS_DAILY, type = Daily())
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.CloseAllTabsRequest(tabList.size), commandCaptor.lastValue)
    }

    @Test
    fun whenSomeButNotAllTabsClosedUsingFabThenViewStateUpdatedAndCommandSentAndPixelSent() = runTest {
        prepareSelectionMode()

        assertEquals(testee.selectionViewState.value.mode, Normal)
        testee.onSelectionModeRequested()
        assertEquals(testee.selectionViewState.value.mode, Selection())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_SELECT_TABS)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_SELECT_TABS_DAILY, type = Daily())

        testee.onTabSelected(tabList.first().tabId)
        assertEquals(testee.selectionViewState.value.mode, Selection(selectedTabs = listOf(tabList.first().tabId)))

        testee.onFabClicked()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TABS)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TABS_DAILY, type = Daily())
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.CloseTabsRequest(listOf(tabList.first().tabId)), commandCaptor.lastValue)
    }

    @Test
    fun whenOtherTabsClosedThenCommandSentAndPixelSent() = runTest {
        prepareSelectionMode()

        testee.onSelectionModeRequested()
        testee.onTabSelected(tabList.first().tabId)
        testee.onCloseOtherTabsRequested()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_CLOSE_OTHER_TABS)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_CLOSE_OTHER_TABS_DAILY, type = Daily())
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.CloseTabsRequest(tabList.drop(1).map { it.tabId }, true), commandCaptor.lastValue)
    }

    @Test
    fun whenTabsClosedThenCommandSentAndPixelSent() = runTest {
        prepareSelectionMode()

        testee.onSelectionModeRequested()
        testee.onTabSelected(tabList.first().tabId)
        testee.onCloseSelectedTabsRequested(true)

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_CLOSE_TABS)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_CLOSE_TABS_DAILY, type = Daily())
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.CloseTabsRequest(listOf(tabList.first().tabId)), commandCaptor.lastValue)
    }

    @Test
    fun whenTabSelectedThenRepositoryNotifiedAndSwitcherClosedAndPixelSent() = runTest {
        testee.onTabSelected("abc")
        verify(mockTabRepository).select(eq("abc"))
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SWITCH_TABS)
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenTabSelectedAndDeselectedThenViewStateUpdatedAndPixelsSent() = runTest {
        prepareSelectionMode()

        testee.onSelectionModeRequested()
        assertEquals(testee.selectionViewState.value.mode, Selection())
        val selectedTabId = tabList[1].tabId
        testee.onTabSelected(selectedTabId)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_TAB_SELECTED)
        assertEquals(testee.selectionViewState.value.mode, Selection(selectedTabs = listOf(selectedTabId)))

        testee.onTabSelected(selectedTabId)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_TAB_DESELECTED)
        assertEquals(testee.selectionViewState.value.mode, Selection())
    }

    @Test
    fun whenAllTabsClosedThenCloseAndShowUndoMessageCommandFired() = runTest {
        prepareSelectionMode()

        val tabIds = tabList.map { it.tabId }
        testee.onCloseTabsConfirmed(tabIds)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.CloseAndShowUndoMessage(tabIds), commandCaptor.lastValue)
    }

    @Test
    fun whenAllTabsSelectedAndDeselectedThenViewStateUpdatedAndPixelsSent() = runTest {
        prepareSelectionMode()

        testee.onSelectionModeRequested()
        testee.onSelectAllTabs()

        assertEquals(testee.selectionViewState.value.mode, Selection(tabList.map { it.tabId }))
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SELECT_ALL)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SELECT_ALL_DAILY, type = Daily())

        testee.onDeselectAllTabs()

        assertEquals(testee.selectionViewState.value.mode, Selection())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_DESELECT_ALL)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_DESELECT_ALL_DAILY, type = Daily())
    }

    @Test
    fun whenShareLinksMenuTappedThenPixelsSent() = runTest {
        prepareSelectionMode()

        testee.onSelectionModeRequested()
        testee.onShareSelectedTabs()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SHARE_LINKS)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SHARE_LINKS_DAILY, type = Daily())
    }

    @Test
    fun whenShareSingleLinkThenShareLinkCommandSentAndPixelsSent() = runTest {
        prepareSelectionMode()

        val tab = tabList.first()

        testee.onSelectionModeRequested()
        testee.onTabSelected(tab.tabId)
        testee.onShareSelectedTabs()

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShareLink(tab.url.orEmpty(), tab.title.orEmpty()), commandCaptor.lastValue)

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SHARE_LINKS)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SHARE_LINKS_DAILY, type = Daily())
    }

    @Test
    fun whenShareMultipleLinksThenShareLinksCommandSentExcludingNewTabPageAndPixelsSent() = runTest {
        prepareSelectionMode()

        testee.onSelectionModeRequested()
        testee.onSelectAllTabs()
        testee.onShareSelectedTabs()

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShareLinks(tabList.take(2).map { it.url.orEmpty() }), commandCaptor.lastValue)

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SHARE_LINKS)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SHARE_LINKS_DAILY, type = Daily())
    }

    @Test
    fun whenBookmarkTabsMenuTappedThenBookmarkTabsRequestCommandSentAndPixelsSent() = runTest {
        prepareSelectionMode()

        testee.onSelectionModeRequested()
        testee.onSelectAllTabs()
        testee.onBookmarkSelectedTabs()

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.BookmarkTabsRequest(tabList.map { it.tabId }), commandCaptor.lastValue)

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_BOOKMARK_TABS)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_BOOKMARK_TABS_DAILY, type = Daily())
    }

    @Test
    fun whenBookmarkTabsConfirmedThenShowUndoBookmarkMessageCommandSentSkippingNewTabPage() = runTest {
        prepareSelectionMode()

        val tabIds = tabList.map { it.tabId }
        val bookmark = Bookmark(
            id = tabIds.first(),
            url = tabList.first().url.orEmpty(),
            title = tabList.first().title.orEmpty(),
            lastModified = null,
        )

        whenever(savedSitesRepository.insertBookmark(any(), any())).thenReturn(bookmark)
        whenever(savedSitesRepository.getBookmark(any())).thenReturn(null)

        testee.onBookmarkTabsConfirmed(tabIds)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowUndoBookmarkMessage(2), commandCaptor.lastValue)
    }

    @Test
    fun whenBookmarkTabsConfirmedThenShowUndoBookmarkMessageCommandSentSkippingUnsavedTabs() = runTest {
        prepareSelectionMode()

        val tabIds = tabList.map { it.tabId }
        whenever(savedSitesRepository.insertBookmark(any(), any())).thenReturn(null)

        testee.onBookmarkTabsConfirmed(tabIds)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowUndoBookmarkMessage(0), commandCaptor.lastValue)
    }

    @Test
    fun whenBookmarkTabsConfirmedThenShowUndoBookmarkMessageCommandSentSkippingExistingTabs() = runTest {
        prepareSelectionMode()

        val tabIds = tabList.map { it.tabId }
        val bookmark = Bookmark(
            id = tabIds.first(),
            url = tabList.first().url.orEmpty(),
            title = tabList.first().title.orEmpty(),
            lastModified = null,
        )

        whenever(savedSitesRepository.insertBookmark(any(), any())).thenReturn(bookmark)
        whenever(savedSitesRepository.getBookmark(bookmark.url)).thenReturn(bookmark)
        whenever(savedSitesRepository.getBookmark(tabList[1].url.orEmpty())).thenReturn(null)
        whenever(savedSitesRepository.getBookmark(tabList[2].url.orEmpty())).thenReturn(null)

        testee.onBookmarkTabsConfirmed(tabIds)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowUndoBookmarkMessage(1), commandCaptor.lastValue)
    }

    @Test
    fun whenUndoBookmarkActionTappedThenBookmarkDeletedFromRepository() = runTest {
        prepareSelectionMode()

        val tab = tabList.first()
        val bookmark = Bookmark(
            id = tab.tabId,
            url = tab.url.orEmpty(),
            title = tab.title.orEmpty(),
            lastModified = null,
        )

        whenever(savedSitesRepository.insertBookmark(any(), any())).thenReturn(bookmark)

        testee.onBookmarkTabsConfirmed(listOf(tab.tabId))
        testee.undoBookmarkAction()

        verify(savedSitesRepository).delete(bookmark)
    }

    @Test
    fun whenUndoBookmarkActionAndUndoActionNotTappedThenNoBookmarkIsRemoved() = runTest {
        prepareSelectionMode()

        val tab = tabList.first()
        val bookmark = Bookmark(
            id = tab.tabId,
            url = tab.url.orEmpty(),
            title = tab.title.orEmpty(),
            lastModified = null,
        )

        whenever(savedSitesRepository.insertBookmark(any(), any())).thenReturn(bookmark)

        testee.onBookmarkTabsConfirmed(listOf(tab.tabId))
        testee.finishBookmarkAction()
        testee.undoBookmarkAction()

        verify(savedSitesRepository, never()).delete(bookmark)
    }

    @Test
    fun whenPopupMenuTappedThenCorrectPixelSentBasedOnSelectionMode() = runTest {
        prepareSelectionMode()

        testee.onMenuOpened()
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_PRESSED)

        testee.onSelectionModeRequested()

        testee.onMenuOpened()
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_PRESSED)
    }

    @Test
    fun whenTabDeletedThenRepositoryNotifiedAndCloseCommandSent() = runTest {
        val tab = tabSwitcherItems.first()
        whenever(mockTabRepository.flowTabs).thenReturn(flowOf(listOf(tabList.first())))
        whenever(mockTabRepository.getTab(any())).thenReturn(tab.tabEntity)

        initializeViewModel()

        testee.onTabCloseInNormalModeRequested(tab)
        verify(mockTabRepository).deleteTabs(listOf(tab.id))

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenTabClosedInNormalModeWithSwipeGestureThenCallMarkDeletableAndSendUndoCommandAndSendPixel() = runTest {
        tabManagerFeatureFlags.multiSelection().setRawStoredState(State(enable = true))

        val swipeGestureUsed = true
        val tab = tabSwitcherItems.first()

        testee.onTabCloseInNormalModeRequested(tab, swipeGestureUsed)

        verify(mockTabRepository).markDeletable(tab.tabEntity)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_SWIPED)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowUndoDeleteTabsMessage(listOf(tab.id)), commandCaptor.lastValue)
    }

    @Test
    fun whenTabClosedUsingCloseButtonInNormalModeThenCallMarkDeletableAndSendUndoCommandAndSendPixel() = runTest {
        tabManagerFeatureFlags.multiSelection().setRawStoredState(State(enable = true))

        val swipeGestureUsed = false
        val tab = tabSwitcherItems.first()

        testee.onTabCloseInNormalModeRequested(tab, swipeGestureUsed)

        verify(mockTabRepository).markDeletable(tab.tabEntity)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_CLICKED)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowUndoDeleteTabsMessage(listOf(tab.id)), commandCaptor.lastValue)
    }

    @Test
    fun whenUndoDeletableTabThenUndoDelete() = runTest {
        val entity = TabEntity("abc", "", "", position = 0)
        testee.onUndoDeleteTab(entity)

        verify(mockTabRepository).undoDeletable(entity)
    }

    @Test
    fun whenUndoDeletableTabsThenOnUndoDelete() = runTest {
        val tabs = tabList.map { it.tabId }
        testee.onUndoDeleteTabs(tabs)

        verify(mockTabRepository).undoDeletable(tabs)
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
        assertEquals(Command.CloseAllTabsRequest(tabList.size), commandCaptor.lastValue)
    }

    @Test
    fun whenOnCloseAllTabsConfirmedThenTabDeletedAndTabIdClearedAndSessionDeletedAndPixelFiredAndTabSwitcherClosed() = runTest {
        val tabIdCaptor = argumentCaptor<String>()
        whenever(mockTabRepository.getTab(tabIdCaptor.capture())).thenAnswer { _ -> tabList.first { it.tabId == tabIdCaptor.lastValue } }

        testee.tabSwitcherItemsLiveData.blockingObserve()

        testee.onCloseAllTabsConfirmed()

        tabList.forEach {
            verify(mockTabRepository).deleteTabs(tabList.map { it.tabId })
        }
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED_DAILY, type = Daily())

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenCloseTabsConfirmedThenTabMarkedDeletableAndTabIdClearedAndSessionDeletedAndPixelFiredAndUndoSnackbarShown() = runTest {
        val tabIdCaptor = argumentCaptor<String>()
        whenever(mockTabRepository.getTab(tabIdCaptor.capture())).thenAnswer { _ -> tabList.first { it.tabId == tabIdCaptor.lastValue } }

        val tabId = tabList.first().tabId

        testee.tabSwitcherItemsLiveData.blockingObserve()
        testee.onCloseTabsConfirmed(listOf(tabId))

        verify(mockTabRepository).markDeletable(listOf(tabId))

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TABS_CONFIRMED)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TABS_CONFIRMED_DAILY, type = Daily())

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowUndoDeleteTabsMessage(listOf(tabId)), commandCaptor.lastValue)
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

    @Test
    fun whenOnDraggingStartedThePixelSent() = runTest {
        whenever(mockTabRepository.tabSwitcherData).thenReturn(flowOf(TabSwitcherData(TabSwitcherData.UserState.EXISTING, GRID)))

        // we need to use the new stubbing here
        initializeViewModel()

        testee.onTabDraggingStarted()

        val params = mapOf("userState" to EXISTING.name)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_REARRANGE_TABS_DAILY, params, emptyMap(), Daily())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun whenOnDraggingStartedThePixelsSent() = runTest {
        initializeViewModel()

        testee.onTabDraggingStarted()

        advanceUntilIdle()

        val params = mapOf("userState" to NEW.name)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_REARRANGE_TABS_DAILY, params, emptyMap(), Daily())
    }

    @Test
    fun whenOnTabMovedRepositoryUpdatesTabPosition() = runTest {
        val fromIndex = 0
        val toIndex = 2

        testee.onTabMoved(fromIndex, toIndex)

        verify(mockTabRepository).updateTabPosition(fromIndex, toIndex)
    }

    @Test
    fun whenListLayoutTypeToggledCorrectPixelsAreFired() = runTest {
        coroutinesTestRule.testScope.launch {
            testee.layoutType.collect()
        }

        testee.onLayoutTypeToggled()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_LIST_VIEW_BUTTON_CLICKED)
    }

    @Test
    fun whenGridLayoutTypeToggledCorrectPixelsAreFired() = runTest {
        whenever(mockTabRepository.tabSwitcherData).thenReturn(flowOf(tabSwitcherData.copy(layoutType = LIST)))

        // we need to use the new stubbing here
        initializeViewModel()

        coroutinesTestRule.testScope.launch {
            testee.layoutType.collect()
        }

        testee.onLayoutTypeToggled()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_GRID_VIEW_BUTTON_CLICKED)
    }

    @Test
    fun whenListLayoutTypeToggledTheTypeIsChangedToGrid() = runTest {
        coroutinesTestRule.testScope.launch {
            testee.layoutType.collect()
        }

        // the default layout type is GRID
        testee.onLayoutTypeToggled()

        verify(mockTabRepository).setTabLayoutType(LIST)
    }

    @Test
    fun whenGridLayoutTypeToggledTheTypeIsChangedToList() = runTest {
        whenever(mockTabRepository.tabSwitcherData).thenReturn(flowOf(tabSwitcherData.copy(layoutType = LIST)))

        // we need to use the new stubbing here
        initializeViewModel()

        coroutinesTestRule.testScope.launch {
            testee.layoutType.collect()
        }

        testee.onLayoutTypeToggled()

        verify(mockTabRepository).setTabLayoutType(GRID)
    }

    @Test
    fun `when Duck Chat menu item clicked and it wasn't used before then open Duck Chat and send a pixel`() = runTest {
        whenever(duckChatMock.wasOpenedBefore()).thenReturn(false)

        testee.onDuckAIButtonClicked()

        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_TAB_SWITCHER_FAB, mapOf("was_used_before" to "0"))
        verify(duckChatMock).openDuckChat()
    }

    @Test
    fun `when Duck Chat menu item clicked and it was used before then open Duck Chat and send a pixel`() = runTest {
        whenever(duckChatMock.wasOpenedBefore()).thenReturn(true)

        testee.onDuckAIButtonClicked()

        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_TAB_SWITCHER_FAB, mapOf("was_used_before" to "1"))
        verify(duckChatMock).openDuckChat()
    }

    @Test
    fun `when Duck Chat fab clicked and it wasn't used before then open Duck Chat and send a pixel`() = runTest {
        whenever(duckChatMock.wasOpenedBefore()).thenReturn(false)

        testee.onDuckAIFabClicked()

        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_TAB_SWITCHER_FAB, mapOf("was_used_before" to "0"))
        verify(duckChatMock).openDuckChat()
    }

    @Test
    fun `when Duck Chat fab clicked and it was used before then open Duck Chat and send a pixel`() = runTest {
        whenever(duckChatMock.wasOpenedBefore()).thenReturn(true)

        testee.onDuckAIFabClicked()

        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_TAB_SWITCHER_FAB, mapOf("was_used_before" to "1"))
        verify(duckChatMock).openDuckChat()
    }

    @Test
    fun whenNormalModeAndNoTabsThenVerifyDynamicInterface() {
        val viewState = SelectionViewState(tabSwitcherItems = emptyList(), mode = Normal, layoutType = null, isDuckAIButtonVisible = true)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = true,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = true,
            isAIFabVisible = true,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndOneNewTabPageThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true))
        val viewState = SelectionViewState(tabSwitcherItems = tabItems, mode = Normal, layoutType = null, isDuckAIButtonVisible = true)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = true,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = false,
            isMainFabVisible = true,
            isAIFabVisible = true,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = true,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = true,
            isAIFabVisible = true,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndNewVisualDesignEnabledAndDuckChatDisabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = false,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = true,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = true,
            isAIFabVisible = false,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndNewVisualDesignDisabledAndDuckChatDisabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = false,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = true,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = true,
            isAIFabVisible = false,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndLayoutIsGridThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(tabSwitcherItems = tabItems, mode = Normal, layoutType = GRID, isDuckAIButtonVisible = true)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = true,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = true,
            isAIFabVisible = true,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.LIST,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndLayoutIsListThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1", "http://cnn.com"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = LIST,
            isDuckAIButtonVisible = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = true,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = true,
            isAIFabVisible = true,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.GRID,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndNoTabsSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), false),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = SelectionViewState(tabSwitcherItems = tabItems, mode = Selection(emptyList()), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = true,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = false,
            isSelectTabsVisible = false,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = false,
            isCloseAllTabsVisible = false,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndOneTabSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = SelectionViewState(tabSwitcherItems = tabItems, mode = Selection(listOf("1")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = true,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = true,
            isShareSelectedLinksVisible = true,
            isBookmarkSelectedTabsVisible = true,
            isSelectTabsDividerVisible = false,
            isSelectTabsVisible = false,
            isCloseSelectedTabsVisible = true,
            isCloseOtherTabsVisible = true,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = false,
            isMenuButtonEnabled = true,
            isMainFabVisible = true,
            isAIFabVisible = false,
            mainFabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndOneNewTabPageSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1"), true),
            SelectableTab(TabEntity("2", url = "cnn.com"), false),
        )
        val viewState = SelectionViewState(tabSwitcherItems = tabItems, mode = Selection(listOf("1")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = true,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = false,
            isSelectTabsVisible = false,
            isCloseSelectedTabsVisible = true,
            isCloseOtherTabsVisible = true,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = false,
            isMenuButtonEnabled = true,
            isMainFabVisible = true,
            isAIFabVisible = false,
            mainFabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndMultipleTabsSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2", "http://cnn.com"), true),
            SelectableTab(TabEntity("3"), false),
        )
        val viewState = SelectionViewState(tabSwitcherItems = tabItems, mode = Selection(listOf("1", "2")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = true,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = true,
            isShareSelectedLinksVisible = true,
            isBookmarkSelectedTabsVisible = true,
            isSelectTabsDividerVisible = false,
            isSelectTabsVisible = false,
            isCloseSelectedTabsVisible = true,
            isCloseOtherTabsVisible = true,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = false,
            isMenuButtonEnabled = true,
            isMainFabVisible = true,
            isAIFabVisible = false,
            mainFabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndAllTabsSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), true),
        )
        val viewState = SelectionViewState(tabSwitcherItems = tabItems, mode = Selection(listOf("1", "2")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = true,
            isSelectionActionsDividerVisible = true,
            isShareSelectedLinksVisible = true,
            isBookmarkSelectedTabsVisible = true,
            isSelectTabsDividerVisible = false,
            isSelectTabsVisible = false,
            isCloseSelectedTabsVisible = true,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = false,
            isMenuButtonEnabled = true,
            isMainFabVisible = true,
            isAIFabVisible = false,
            mainFabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    // -----

    @Test
    fun whenNormalModeAndNoTabsAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val viewState = SelectionViewState(
            tabSwitcherItems = emptyList(),
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = true,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndOneNewTabPageAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true))
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = true,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = false,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = true,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndNewVisualDesignEnabledAndDuckChatDisabledAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = false,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndNewVisualDesignDisabledAndDuckChatDisabledAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = false,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndLayoutIsGridAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = GRID,
            isDuckAIButtonVisible = true,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.LIST,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndLayoutIsListAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1", "http://cnn.com"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = LIST,
            isDuckAIButtonVisible = true,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isSelectAllVisible = false,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = true,
            isSelectTabsVisible = true,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = true,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.GRID,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndNoTabsSelectedAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), false),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(emptyList()),
            layoutType = null,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = true,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = false,
            isSelectTabsVisible = false,
            isCloseSelectedTabsVisible = false,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = false,
            isCloseAllTabsVisible = false,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndOneTabSelectedAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(listOf("1")),
            layoutType = null,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = true,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = true,
            isShareSelectedLinksVisible = true,
            isBookmarkSelectedTabsVisible = true,
            isSelectTabsDividerVisible = false,
            isSelectTabsVisible = false,
            isCloseSelectedTabsVisible = true,
            isCloseOtherTabsVisible = true,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = false,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndOneNewTabPageSelectedAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1"), true),
            SelectableTab(TabEntity("2", url = "cnn.com"), false),
        )
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(listOf("1")),
            layoutType = null,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = true,
            isDeselectAllVisible = false,
            isSelectionActionsDividerVisible = false,
            isShareSelectedLinksVisible = false,
            isBookmarkSelectedTabsVisible = false,
            isSelectTabsDividerVisible = false,
            isSelectTabsVisible = false,
            isCloseSelectedTabsVisible = true,
            isCloseOtherTabsVisible = true,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = false,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndMultipleTabsSelectedAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), true),
        )
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(listOf("1", "2")),
            layoutType = null,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = true,
            isSelectionActionsDividerVisible = true,
            isShareSelectedLinksVisible = true,
            isBookmarkSelectedTabsVisible = true,
            isSelectTabsDividerVisible = false,
            isSelectTabsVisible = false,
            isCloseSelectedTabsVisible = true,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = false,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndAllTabsSelectedAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), true),
        )
        val viewState = SelectionViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(listOf("1", "2")),
            layoutType = null,
            isNewToolbarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabMenuVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = false,
            isDeselectAllVisible = true,
            isSelectionActionsDividerVisible = true,
            isShareSelectedLinksVisible = true,
            isBookmarkSelectedTabsVisible = true,
            isSelectTabsDividerVisible = false,
            isSelectTabsVisible = false,
            isCloseSelectedTabsVisible = true,
            isCloseOtherTabsVisible = false,
            isCloseAllTabsDividerVisible = true,
            isCloseAllTabsVisible = false,
            isMenuButtonEnabled = true,
            isMainFabVisible = false,
            isAIFabVisible = false,
            mainFabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonMode = LayoutMode.HIDDEN,
            layoutMenuMode = LayoutMode.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun `when animated info panel then tab switcher items include animation tile and tabs`() = runTest {
        val fakeTabSwitcherDataStore = FakeTabSwitcherDataStore().apply {
            setIsAnimationTileDismissed(false)
        }

        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabList = listOf(tab1, tab2)

        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)

        initializeMockTabEntitesData()
        initializeViewModel(fakeTabSwitcherDataStore)

        val items = testee.tabSwitcherItemsLiveData.blockingObserve() ?: listOf()

        assertEquals(3, items.size)
        assert(items.first() is TabSwitcherItem.TrackerAnimationInfoPanel)
        assert(items[1] is TabSwitcherItem.Tab)
        assert(items[2] is TabSwitcherItem.Tab)
    }

    @Test
    fun `when animated info panel not visible then tab switcher items contain only tabs`() = runTest {
        val fakeTabSwitcherDataStore = FakeTabSwitcherDataStore().apply {
            setIsAnimationTileDismissed(true)
        }

        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabList = listOf(tab1, tab2)

        initializeMockTabEntitesData()
        initializeViewModel(fakeTabSwitcherDataStore)

        val items = testee.tabSwitcherItemsLiveData.blockingObserve() ?: listOf()

        assertEquals(2, items.size)
        items.forEach { item ->
            assert(item is TabSwitcherItem.Tab)
        }
    }

    @Test
    fun `when tab switcher animation feature disabled then tab switcher items contain only tabs`() = runTest {
        val fakeTabSwitcherDataStore = FakeTabSwitcherDataStore().apply {
            setIsAnimationTileDismissed(true)
        }

        initializeMockTabEntitesData()
        initializeViewModel(fakeTabSwitcherDataStore)

        val items = testee.tabSwitcherItemsLiveData.blockingObserve() ?: listOf()

        assertEquals(tabList.size, items.size)
        items.forEach { item ->
            assert(item is TabSwitcherItem.Tab)
        }
    }

    @Test
    fun `when animated info panel positive button clicked then animated info panel is hidden`() = runTest {
        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabList = listOf(tab1, tab2)

        initializeMockTabEntitesData()
        initializeViewModel(FakeTabSwitcherDataStore())

        testee.onTrackerAnimationTilePositiveButtonClicked()

        val items = testee.tabSwitcherItemsLiveData.blockingObserve() ?: listOf()

        assertFalse(items.first() is TabSwitcherItem.TrackerAnimationInfoPanel)
    }

    @Test
    fun `when animated info panel negative button clicked then animated info panel is still visible`() = runTest {
        initializeViewModel(FakeTabSwitcherDataStore())

        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabList = listOf(tab1, tab2)

        testee.onTrackerAnimationTileNegativeButtonClicked()

        val items = testee.tabSwitcherItemsLiveData.blockingObserve() ?: listOf()

        assertTrue(items.first() is TabSwitcherItem.TrackerAnimationInfoPanel)
    }

    @Test
    fun `when animated info panel visible then impressions pixel fired`() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)
        initializeViewModel(FakeTabSwitcherDataStore())

        testee.onTrackerAnimationInfoPanelVisible()

        verify(mockTabSwitcherAnimationInfoPanelPixels).fireInfoPanelImpression()
    }

    @Test
    fun `when animated info panel clicked then tapped pixel fired`() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)

        initializeViewModel()

        testee.onTrackerAnimationInfoPanelClicked()

        verify(mockTabSwitcherAnimationInfoPanelPixels).fireInfoPanelTapped()
    }

    @Test
    fun `when animated info panel positive button clicked then dismiss pixel fired`() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)
        initializeViewModel()

        testee.onTrackerAnimationTilePositiveButtonClicked()

        verify(mockTabSwitcherAnimationInfoPanelPixels).fireInfoPanelDismissed()
    }

    private class FakeTabSwitcherDataStore : TabSwitcherDataStore {

        private val animationTileDismissedFlow = MutableStateFlow(false)

        override val data: Flow<TabSwitcherData>
            get() = flowOf(TabSwitcherData(NEW, GRID))

        override suspend fun setUserState(userState: UserState) {}

        override suspend fun setTabLayoutType(layoutType: LayoutType) {}

        override fun isAnimationTileDismissed(): Flow<Boolean> = animationTileDismissedFlow

        override suspend fun setIsAnimationTileDismissed(isDismissed: Boolean) {
            animationTileDismissedFlow.value = isDismissed
        }
    }

    private fun TestScope.prepareSelectionMode() {
        tabManagerFeatureFlags.multiSelection().setRawStoredState(State(enable = true))

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            testee.selectionViewState.collect()
        }
    }

    private class FakeUserBrowserProperties : UserBrowserProperties {

        private var daysSinceInstall: Long = 0

        fun setDaysSinceInstalled(days: Long) {
            daysSinceInstall = days
        }

        override fun appTheme(): DuckDuckGoTheme {
            TODO("Not yet implemented")
        }

        override suspend fun bookmarks(): Long {
            TODO("Not yet implemented")
        }

        override suspend fun favorites(): Long {
            TODO("Not yet implemented")
        }

        override fun daysSinceInstalled(): Long = daysSinceInstall

        override suspend fun daysUsedSince(since: Date): Long {
            TODO("Not yet implemented")
        }

        override fun defaultBrowser(): Boolean {
            TODO("Not yet implemented")
        }

        override fun emailEnabled(): Boolean {
            TODO("Not yet implemented")
        }

        override fun searchCount(): Long {
            TODO("Not yet implemented")
        }

        override fun widgetAdded(): Boolean {
            TODO("Not yet implemented")
        }
    }
}
