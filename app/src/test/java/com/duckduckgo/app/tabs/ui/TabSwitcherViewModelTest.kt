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
import androidx.lifecycle.liveData
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.SwipingTabsFeature
import com.duckduckgo.app.browser.SwipingTabsFeatureProvider
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.model.TabSwitcherData.UserState.EXISTING
import com.duckduckgo.app.tabs.model.TabSwitcherData.UserState.NEW
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.SelectableTab
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.BackButtonType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.DynamicInterface
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.FabType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.LayoutButtonType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Normal
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Selection
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.blockingObserve
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.DuckChatPixelName
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    private lateinit var mockWebViewSessionStorage: WebViewSessionStorage

    @Mock
    private lateinit var mockAdClickManager: AdClickManager

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var statisticsDataStore: StatisticsDataStore

    @Mock
    private lateinit var duckChatMock: DuckChat

    @Mock
    private lateinit var faviconManager: FaviconManager

    @Mock
    private lateinit var savedSitesRepository: SavedSitesRepository

    private val tabManagerFeatureFlags = FakeFeatureToggleFactory.create(TabManagerFeatureFlags::class.java)

    private val swipingTabsFeature = FakeFeatureToggleFactory.create(SwipingTabsFeature::class.java)
    private val swipingTabsFeatureProvider = SwipingTabsFeatureProvider(swipingTabsFeature)

    private lateinit var testee: TabSwitcherViewModel

    private val tabList = listOf(
        TabEntity("1", url = "https://cnn.com", position = 1),
        TabEntity("2", url = "http://test.com", position = 2),
        TabEntity("3", position = 3),
    )
    private val tabSwitcherItems = tabList.map { NormalTab(it, false) }
    private val repoDeletableTabs = Channel<List<TabEntity>>()
    private val tabs = MutableLiveData<List<TabEntity>>(tabList)

    private val tabSwitcherData = TabSwitcherData(NEW, GRID)
    private val flowTabs = flowOf(tabList)

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        swipingTabsFeature.self().setRawStoredState(State(enable = false))
        tabManagerFeatureFlags.multiSelection().setRawStoredState(State(enable = false))

        whenever(mockTabRepository.flowDeletableTabs).thenReturn(repoDeletableTabs.consumeAsFlow())
        runBlocking {
            whenever(mockTabRepository.add()).thenReturn("TAB_ID")
        }
        whenever(mockTabRepository.tabSwitcherData).thenReturn(flowOf(tabSwitcherData))
        whenever(mockTabRepository.flowTabs).thenReturn(flowTabs)
        whenever(statisticsDataStore.variant).thenReturn("")
        whenever(mockTabRepository.liveSelectedTab).thenReturn(liveData { tabList.first() })
        whenever(mockTabRepository.flowSelectedTab).thenReturn(flowOf(tabList.first()))

        initializeViewModel()
    }

    private fun initializeViewModel() {
        testee = TabSwitcherViewModel(
            mockTabRepository,
            mockWebViewSessionStorage,
            mockAdClickManager,
            coroutinesTestRule.testDispatcherProvider,
            mockPixel,
            swipingTabsFeatureProvider,
            duckChatMock,
            tabManagerFeatureFlags,
            faviconManager,
            savedSitesRepository,
        )
        testee.command.observeForever(mockCommandObserver)
        testee.tabSwitcherItems.observeForever(mockTabSwitcherItemsObserver)
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
        testee.selectionViewState.first()

        testee.onFabClicked()

        verify(mockTabRepository).add()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_NEW_TAB_CLICKED)
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenAllTabsClosedUsingFabThenRepositoryNotifiedAndPixelSent() = runTest {
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
    fun whenSomeButNotAllTabsClosedUsingFabThenRepositoryNotifiedPixelSent() = runTest {
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
    fun whenOtherTabsClosedThenRepositoryNotifiedAndPixelSent() = runTest {
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
    fun whenTabsClosedThenRepositoryNotifiedAndPixelSent() = runTest {
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
    fun whenShareSingleLinkThenShareLinkCommandSent() = runTest {
        prepareSelectionMode()

        val tab = tabList.first()

        testee.onSelectionModeRequested()
        testee.onTabSelected(tab.tabId)
        testee.onShareSelectedTabs()

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShareLink(tab.url.orEmpty(), tab.title.orEmpty()), commandCaptor.lastValue)
    }

    @Test
    fun whenShareMultipleLinksThenShareLinksCommandSentExcludingNewTabPage() = runTest {
        prepareSelectionMode()

        testee.onSelectionModeRequested()
        testee.onSelectAllTabs()
        testee.onShareSelectedTabs()

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShareLinks(tabList.take(2).map { it.url.orEmpty() }), commandCaptor.lastValue)
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
        verify(mockAdClickManager).clearTabId(tab.id)

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
        verify(mockAdClickManager, never()).clearTabId(tab.id)
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
        verify(mockAdClickManager, never()).clearTabId(tab.id)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_CLICKED)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowUndoDeleteTabsMessage(listOf(tab.id)), commandCaptor.lastValue)
    }

    @Test
    fun whenUndoDeletableTabThenUndoDeletable() = runTest {
        val entity = TabEntity("abc", "", "", position = 0)
        testee.undoDeletableTab(entity)

        verify(mockTabRepository).undoDeletable(entity)
    }

    @Test
    fun whenUndoDeletableTabsThenUndoDeletable() = runTest {
        val tabs = tabList.map { it.tabId }
        testee.undoDeletableTabs(tabs)

        verify(mockTabRepository).undoDeletable(tabs)
    }

    @Test
    fun whenPurgeDeletableTabsThenCallRepositoryPurgeDeletableTabs() = runTest {
        whenever(mockTabRepository.getDeletableTabIds()).thenReturn(listOf("id_1", "id_2"))

        testee.purgeDeletableTabs()

        verify(mockTabRepository).getDeletableTabIds()
        verify(mockAdClickManager).clearTabId("id_1")
        verify(mockAdClickManager).clearTabId("id_2")
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

        testee.tabSwitcherItems.blockingObserve()

        testee.onCloseAllTabsConfirmed()

        tabList.forEach {
            verify(mockTabRepository).deleteTabs(tabList.map { it.tabId })
            verify(mockAdClickManager).clearTabId(it.tabId)
            verify(mockWebViewSessionStorage).deleteSession(it.tabId)
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

        testee.tabSwitcherItems.blockingObserve()
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

        testee.onDuckChatMenuClicked()

        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN)
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_NEW_TAB_MENU, mapOf("was_used_before" to "0"))
        verify(duckChatMock).openDuckChat()
    }

    @Test
    fun `when Duck Chat menu item clicked and it was used before then open Duck Chat and send a pixel`() = runTest {
        whenever(duckChatMock.wasOpenedBefore()).thenReturn(true)

        testee.onDuckChatMenuClicked()

        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN)
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_NEW_TAB_MENU, mapOf("was_used_before" to "1"))
        verify(duckChatMock).openDuckChat()
    }

    @Test
    fun whenNormalModeAndNoTabsThenVerifyDynamicInterface() {
        val viewState = SelectionViewState(tabItems = emptyList(), mode = Normal, layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabVisible = true,
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
            isMoreMenuItemEnabled = true,
            isFabVisible = true,
            fabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonType = LayoutButtonType.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndOneNewTabPageThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true))
        val viewState = SelectionViewState(tabItems = tabItems, mode = Normal, layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabVisible = true,
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
            isMoreMenuItemEnabled = false,
            isFabVisible = true,
            fabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonType = LayoutButtonType.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(tabItems = tabItems, mode = Normal, layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabVisible = true,
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
            isMoreMenuItemEnabled = true,
            isFabVisible = true,
            fabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonType = LayoutButtonType.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndLayoutIsGridThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(tabItems = tabItems, mode = Normal, layoutType = GRID)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabVisible = true,
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
            isMoreMenuItemEnabled = true,
            isFabVisible = true,
            fabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonType = LayoutButtonType.LIST,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndLayoutIsListThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1", "http://cnn.com"), true), NormalTab(TabEntity("2"), false))
        val viewState = SelectionViewState(tabItems = tabItems, mode = Normal, layoutType = LIST)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabVisible = true,
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
            isMoreMenuItemEnabled = true,
            isFabVisible = true,
            fabType = FabType.NEW_TAB,
            backButtonType = BackButtonType.ARROW,
            layoutButtonType = LayoutButtonType.GRID,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndNoTabsSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), false),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = SelectionViewState(tabItems = tabItems, mode = Selection(emptyList()), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabVisible = false,
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
            isMoreMenuItemEnabled = true,
            isFabVisible = false,
            fabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonType = LayoutButtonType.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndOneTabSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = SelectionViewState(tabItems = tabItems, mode = Selection(listOf("1")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabVisible = false,
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
            isMoreMenuItemEnabled = true,
            isFabVisible = true,
            fabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonType = LayoutButtonType.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndOneNewTabPageSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1"), true),
            SelectableTab(TabEntity("2", url = "cnn.com"), false),
        )
        val viewState = SelectionViewState(tabItems = tabItems, mode = Selection(listOf("1")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabVisible = false,
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
            isMoreMenuItemEnabled = true,
            isFabVisible = true,
            fabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonType = LayoutButtonType.HIDDEN,
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
        val viewState = SelectionViewState(tabItems = tabItems, mode = Selection(listOf("1", "2")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabVisible = false,
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
            isMoreMenuItemEnabled = true,
            isFabVisible = true,
            fabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonType = LayoutButtonType.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndAllTabsSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), true),
        )
        val viewState = SelectionViewState(tabItems = tabItems, mode = Selection(listOf("1", "2")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabVisible = false,
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
            isMoreMenuItemEnabled = true,
            isFabVisible = true,
            fabType = FabType.CLOSE_TABS,
            backButtonType = BackButtonType.CLOSE,
            layoutButtonType = LayoutButtonType.HIDDEN,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    private fun TestScope.prepareSelectionMode() {
        tabManagerFeatureFlags.multiSelection().setRawStoredState(State(enable = true))

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            testee.selectionViewState.collect()
        }
    }
}
