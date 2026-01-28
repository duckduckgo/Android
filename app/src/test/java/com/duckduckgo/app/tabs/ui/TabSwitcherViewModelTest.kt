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
import com.duckduckgo.app.browser.api.OmnibarRepository
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.store.StatisticsDataStore
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
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.ViewState
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.ViewState.BackButtonType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.ViewState.DynamicInterface
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.ViewState.LayoutMode
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.ViewState.Mode.Normal
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.ViewState.Mode.Selection
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
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date
import kotlin.Boolean

@SuppressLint("DenyListedApi")
@OptIn(ExperimentalCoroutinesApi::class)
class TabSwitcherViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockCommandObserver: Observer<Command> = mock()

    private val mockTabSwitcherItemsObserver: Observer<List<TabSwitcherItem>> = mock()

    private val commandCaptor = argumentCaptor<Command>()

    private val mockTabRepository: TabRepository = mock()

    private val mockPixel: Pixel = mock()

    private val statisticsDataStore: StatisticsDataStore = mock()

    private val duckChatMock: DuckChat = mock()

    private val duckAiFeatureStateMock: DuckAiFeatureState = mock()

    private val faviconManager: FaviconManager = mock()

    private val savedSitesRepository: SavedSitesRepository = mock()

    private val mockWebTrackersBlockedAppRepository: WebTrackersBlockedAppRepository = mock()

    private val mockTabSwitcherPrefsDataStore: TabSwitcherPrefsDataStore = mock()

    private val mockTrackersAnimationInfoPanelPixels: TrackersAnimationInfoPanelPixels = mock()

    private val mockOmnibarFeatureRepository: OmnibarRepository = mock()

    private val androidBrowserConfig = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private val swipingTabsFeature = FakeFeatureToggleFactory.create(SwipingTabsFeature::class.java)
    private val swipingTabsFeatureProvider = SwipingTabsFeatureProvider(swipingTabsFeature)

    private val mockDuckAiFeatureStateFullScreenModeFlow = MutableStateFlow(false)

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
    fun before() = runTest {
        MockitoAnnotations.openMocks(this)

        swipingTabsFeature.self().setRawStoredState(State(enable = false))
        swipingTabsFeature.enabledForUsers().setRawStoredState(State(enable = true))

        whenever(mockTabSwitcherPrefsDataStore.isTrackersAnimationInfoTileHidden()).thenReturn(flowOf(false))
        whenever(statisticsDataStore.variant).thenReturn("")
        whenever(mockTabRepository.flowDeletableTabs).thenReturn(repoDeletableTabs.consumeAsFlow())
        whenever(duckAiFeatureStateMock.showFullScreenMode).thenReturn(mockDuckAiFeatureStateFullScreenModeFlow)
        runBlocking {
            whenever(mockTabRepository.add()).thenReturn("TAB_ID")
            whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(0)
        }
        whenever(mockTabRepository.tabSwitcherData).thenReturn(flowOf(tabSwitcherData))

        whenever(duckAiFeatureStateMock.showOmnibarShortcutOnNtpAndOnFocus).thenReturn(MutableStateFlow(false))

        initializeMockTabEntitesData()
        initializeViewModel()
        prepareSelectionMode()
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
            mockWebTrackersBlockedAppRepository,
            tabSwitcherDataStore,
            faviconManager,
            savedSitesRepository,
            mockTrackersAnimationInfoPanelPixels,
            mockOmnibarFeatureRepository,
            androidBrowserConfig,
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
    fun whenNewTabRequestedAndSwipingTabsEnabledAndHandleAboutBlankEnabledAndEmptyTabExistsThenSelectEmptyTab() = runTest {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))
        swipingTabsFeature.enabledForUsers().setRawStoredState(State(enable = true))
        androidBrowserConfig.handleAboutBlank().setRawStoredState(State(enable = true))

        val emptyTab = TabEntity("EMPTY_TAB", url = "", sourceTabId = null, position = 0)
        val tabListWithEmptyTab = listOf(
            TabEntity("1", url = "https://cnn.com", position = 1),
            emptyTab,
        )
        tabList = tabListWithEmptyTab
        initializeMockTabEntitesData()
        initializeViewModel()
        prepareSelectionMode()

        testee.onNewTabRequested()

        verify(mockTabRepository).select("EMPTY_TAB")
        verify(mockTabRepository, never()).add()
    }

    @Test
    fun whenNewTabRequestedAndSwipingTabsEnabledAndHandleAboutBlankEnabledAndEmptyTabWithSourceTabExistsThenAddNewTab() = runTest {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))
        swipingTabsFeature.enabledForUsers().setRawStoredState(State(enable = true))
        androidBrowserConfig.handleAboutBlank().setRawStoredState(State(enable = true))

        val tabListWithEmptyTabWithSource = listOf(
            TabEntity("1", url = "https://cnn.com", position = 1),
            TabEntity("EMPTY_TAB", url = "", sourceTabId = "SOURCE_TAB", position = 0),
        )
        tabList = tabListWithEmptyTabWithSource
        initializeMockTabEntitesData()
        initializeViewModel()
        prepareSelectionMode()

        testee.onNewTabRequested()

        verify(mockTabRepository, never()).select(any())
        verify(mockTabRepository).add()
    }

    @Test
    fun whenNewTabRequestedAndSwipingTabsEnabledAndHandleAboutBlankEnabledAndNoEmptyTabExistsThenAddNewTab() = runTest {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))
        swipingTabsFeature.enabledForUsers().setRawStoredState(State(enable = true))
        androidBrowserConfig.handleAboutBlank().setRawStoredState(State(enable = true))

        val tabListWithoutEmptyTab = listOf(
            TabEntity("1", url = "https://cnn.com", position = 1),
            TabEntity("2", url = "https://test.com", position = 2),
        )
        tabList = tabListWithoutEmptyTab
        initializeMockTabEntitesData()
        initializeViewModel()
        prepareSelectionMode()

        testee.onNewTabRequested()

        verify(mockTabRepository, never()).select(any())
        verify(mockTabRepository).add()
    }

    @Test
    fun whenNewTabRequestedAndSwipingTabsEnabledAndHandleAboutBlankDisabledAndEmptyTabExistsThenSelectEmptyTab() = runTest {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))
        swipingTabsFeature.enabledForUsers().setRawStoredState(State(enable = true))
        androidBrowserConfig.handleAboutBlank().setRawStoredState(State(enable = false))

        val emptyTab = TabEntity("EMPTY_TAB", url = "", sourceTabId = "SOURCE_TAB", position = 0)
        val tabListWithEmptyTab = listOf(
            TabEntity("1", url = "https://cnn.com", position = 1),
            emptyTab,
        )
        tabList = tabListWithEmptyTab
        initializeMockTabEntitesData()
        initializeViewModel()
        prepareSelectionMode()

        testee.onNewTabRequested()

        verify(mockTabRepository).select("EMPTY_TAB")
        verify(mockTabRepository, never()).add()
    }

    @Test
    fun whenNewTabRequestedAndSwipingTabsEnabledAndHandleAboutBlankDisabledAndNoEmptyTabExistsThenAddNewTab() = runTest {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))
        swipingTabsFeature.enabledForUsers().setRawStoredState(State(enable = true))
        androidBrowserConfig.handleAboutBlank().setRawStoredState(State(enable = false))

        val tabListWithoutEmptyTab = listOf(
            TabEntity("1", url = "https://cnn.com", position = 1),
            TabEntity("2", url = "https://test.com", position = 2),
        )
        tabList = tabListWithoutEmptyTab
        initializeMockTabEntitesData()
        initializeViewModel()
        prepareSelectionMode()

        testee.onNewTabRequested()

        verify(mockTabRepository, never()).select(any())
        verify(mockTabRepository).add()
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
        assertEquals(testee.viewState.value.mode, Selection())
        val selectedTabId = tabList[1].tabId
        testee.onTabSelected(selectedTabId)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_TAB_SELECTED)
        assertEquals(testee.viewState.value.mode, Selection(selectedTabs = listOf(selectedTabId)))

        testee.onTabSelected(selectedTabId)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_TAB_DESELECTED)
        assertEquals(testee.viewState.value.mode, Selection())
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

        assertEquals(testee.viewState.value.mode, Selection(tabList.map { it.tabId }))
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SELECT_ALL)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SELECT_ALL_DAILY, type = Daily())

        testee.onDeselectAllTabs()

        assertEquals(testee.viewState.value.mode, Selection())
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
        verify(mockTabRepository).markDeletable(tab.tabEntity)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowUndoDeleteTabsMessage(listOf(tab.id)), commandCaptor.lastValue)
    }

    @Test
    fun whenTabClosedInNormalModeWithSwipeGestureThenCallMarkDeletableAndSendUndoCommandAndSendPixel() = runTest {
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

        testee.onCloseAllTabsConfirmed()

        verify(mockTabRepository).markDeletable(tabList.map { it.tabId })
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED_DAILY, type = Daily())

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.CloseAndShowUndoMessage(tabList.map { it.tabId }), commandCaptor.lastValue)
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
    fun `when Duck Chat menu item clicked and fullscreen mode enabled then new tab created and screen closed`() = runTest {
        mockDuckAiFeatureStateFullScreenModeFlow.emit(true)

        whenever(duckChatMock.wasOpenedBefore()).thenReturn(false)

        val duckChatURL = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
        whenever(duckChatMock.getDuckChatUrl(any(), any(), any())).thenReturn(duckChatURL)

        testee.onDuckAIButtonClicked()

        verify(mockTabRepository).add(duckChatURL, true)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenNormalModeAndNoTabsThenVerifyDynamicInterface() {
        val viewState = ViewState(tabSwitcherItems = emptyList(), mode = Normal, layoutType = null, isDuckAIButtonVisible = true)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndOneNewTabPageThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true))
        val viewState = ViewState(tabSwitcherItems = tabItems, mode = Normal, layoutType = null, isDuckAIButtonVisible = true)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndNewVisualDesignEnabledAndDuckChatDisabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = false,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndNewVisualDesignDisabledAndDuckChatDisabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = false,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndLayoutIsGridThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(tabSwitcherItems = tabItems, mode = Normal, layoutType = GRID, isDuckAIButtonVisible = true)
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.LIST,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndLayoutIsListThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1", "http://cnn.com"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = LIST,
            isDuckAIButtonVisible = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.GRID,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndNoTabsSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), false),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = ViewState(tabSwitcherItems = tabItems, mode = Selection(emptyList()), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndOneTabSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = ViewState(tabSwitcherItems = tabItems, mode = Selection(listOf("1")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndOneNewTabPageSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1"), true),
            SelectableTab(TabEntity("2", url = "cnn.com"), false),
        )
        val viewState = ViewState(tabSwitcherItems = tabItems, mode = Selection(listOf("1")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
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
        val viewState = ViewState(tabSwitcherItems = tabItems, mode = Selection(listOf("1", "2")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndAllTabsSelectedThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), true),
        )
        val viewState = ViewState(tabSwitcherItems = tabItems, mode = Selection(listOf("1", "2")), layoutType = null)
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    // -----

    @Test
    fun whenNormalModeAndNoTabsAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val viewState = ViewState(
            tabSwitcherItems = emptyList(),
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndOneNewTabPageAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndNewVisualDesignEnabledAndDuckChatDisabledAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = false,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndNewVisualDesignDisabledAndDuckChatDisabledAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = false,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndLayoutIsGridAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = GRID,
            isDuckAIButtonVisible = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.LIST,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndMultipleTabsAndLayoutIsListAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(NormalTab(TabEntity("1", "http://cnn.com"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = LIST,
            isDuckAIButtonVisible = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = true,
            isNewTabButtonVisible = true,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.GRID,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndNoTabsSelectedAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), false),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(emptyList()),
            layoutType = null,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isSelectAllVisible = true,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndOneTabSelectedAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(listOf("1")),
            layoutType = null,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndOneNewTabPageSelectedAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1"), true),
            SelectableTab(TabEntity("2", url = "cnn.com"), false),
        )
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(listOf("1")),
            layoutType = null,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndMultipleTabsSelectedAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), true),
        )
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(listOf("1", "2")),
            layoutType = null,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndAllTabsSelectedAndNewToolbarEnabledThenVerifyDynamicInterface() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), true),
        )
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(listOf("1", "2")),
            layoutType = null,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun `when animated info panel has not been hidden then tab switcher items include animation tile and tabs`() = runTest {
        val fakeTabSwitcherDataStore = FakeTabSwitcherDataStore().apply {
            setTrackersAnimationInfoTileHidden(false)
        }

        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabList = listOf(tab1, tab2)

        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)

        initializeMockTabEntitesData()
        initializeViewModel(fakeTabSwitcherDataStore)

        val items = testee.tabSwitcherItemsLiveData.blockingObserve() ?: listOf()

        assertEquals(3, items.size)
        assert(items.first() is TabSwitcherItem.TrackersAnimationInfoPanel)
        assert(items[1] is TabSwitcherItem.Tab)
        assert(items[2] is TabSwitcherItem.Tab)
    }

    @Test
    fun `when animated info panel has been hidden then tab switcher items contain only tabs`() = runTest {
        val fakeTabSwitcherDataStore = FakeTabSwitcherDataStore().apply {
            setTrackersAnimationInfoTileHidden(true)
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
    fun `when animated info panel positive button clicked then animated info panel is hidden`() = runTest {
        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabList = listOf(tab1, tab2)

        initializeMockTabEntitesData()
        initializeViewModel(FakeTabSwitcherDataStore())

        testee.onTrackerAnimationTilePositiveButtonClicked()

        val items = testee.tabSwitcherItemsLiveData.blockingObserve() ?: listOf()

        assertFalse(items.first() is TabSwitcherItem.TrackersAnimationInfoPanel)
    }

    @Test
    fun `when animated info panel negative button clicked then animated info panel is still visible`() = runTest {
        initializeViewModel(FakeTabSwitcherDataStore())

        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabList = listOf(tab1, tab2)

        testee.onTrackerAnimationTileNegativeButtonClicked()

        val items = testee.tabSwitcherItemsLiveData.blockingObserve() ?: listOf()

        assertTrue(items.first() is TabSwitcherItem.TrackersAnimationInfoPanel)
    }

    @Test
    fun `when animated info panel visible then impressions pixel fired`() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)
        initializeViewModel(FakeTabSwitcherDataStore())

        testee.onTrackerAnimationInfoPanelVisible()

        verify(mockTrackersAnimationInfoPanelPixels).fireInfoPanelImpression()
    }

    @Test
    fun `when animated info panel clicked then tapped pixel fired`() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)

        initializeViewModel()

        testee.onTrackerAnimationInfoPanelClicked()

        verify(mockTrackersAnimationInfoPanelPixels).fireInfoPanelTapped()
    }

    @Test
    fun `when animated info panel positive button clicked then dismiss pixel fired`() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)
        initializeViewModel()

        testee.onTrackerAnimationTilePositiveButtonClicked()

        verify(mockTrackersAnimationInfoPanelPixels).fireInfoPanelDismissed()
    }

    @Test
    fun whenNormalModeAndSplitOmnibarEnabledThenMenuButtonHiddenAndBottomBarVisible() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = true,
            isSplitOmnibarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = true,
            isMenuButtonVisible = false,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = true,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenNormalModeAndSplitOmnibarEnabledAndDuckAIDisabledThenMenuButtonHiddenAndBottomBarVisible() {
        val tabItems = listOf(NormalTab(TabEntity("1"), true), NormalTab(TabEntity("2"), false))
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Normal,
            layoutType = null,
            isDuckAIButtonVisible = false,
            isSplitOmnibarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = false,
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
            backButtonType = BackButtonType.ARROW,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = true,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndSplitOmnibarEnabledThenMenuButtonVisibleAndBottomBarHidden() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(listOf("1")),
            layoutType = null,
            isDuckAIButtonVisible = false,
            isSplitOmnibarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndSplitOmnibarEnabledAndNoTabsSelectedThenMenuButtonVisibleAndBottomBarHidden() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), false),
            SelectableTab(TabEntity("2"), false),
        )
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(emptyList()),
            layoutType = null,
            isDuckAIButtonVisible = false,
            isSplitOmnibarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    @Test
    fun whenSelectionModeAndSplitOmnibarEnabledAndAllTabsSelectedThenMenuButtonVisibleAndBottomBarHidden() {
        val tabItems = listOf(
            SelectableTab(TabEntity("1", "http://cnn.com"), true),
            SelectableTab(TabEntity("2"), true),
        )
        val viewState = ViewState(
            tabSwitcherItems = tabItems,
            mode = Selection(listOf("1", "2")),
            layoutType = null,
            isDuckAIButtonVisible = false,
            isSplitOmnibarEnabled = true,
        )
        val expected = DynamicInterface(
            isFireButtonVisible = false,
            isNewTabButtonVisible = false,
            isDuckAIButtonVisible = false,
            isMenuButtonVisible = true,
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
            backButtonType = BackButtonType.CLOSE,
            layoutMenuMode = LayoutMode.HIDDEN,
            isBottomBarVisible = false,
        )
        assertEquals(expected, viewState.dynamicInterface)
    }

    // Tests for ViewState.isSplitOmnibarEnabled based on OmnibarFeatureRepository

    @Test
    fun whenOmnibarFeatureRepositoryHasSplitOmnibarDisabledThenViewStateReflectsIt() = runTest {
        whenever(mockOmnibarFeatureRepository.omnibarType).thenReturn(OmnibarType.SINGLE_TOP)

        initializeViewModel()

        assertFalse(testee.viewState.value.isSplitOmnibarEnabled)
    }

    @Test
    fun whenOmnibarFeatureRepositoryHasSplitOmnibarEnabledThenViewStateReflectsIt() = runTest {
        whenever(mockOmnibarFeatureRepository.omnibarType).thenReturn(OmnibarType.SPLIT)

        initializeViewModel()

        assertTrue(testee.viewState.value.isSplitOmnibarEnabled)
    }

    @Test
    fun whenSplitOmnibarDisabledThenDynamicInterfaceShowsMenuButtonAndHidesBottomBar() = runTest {
        whenever(mockOmnibarFeatureRepository.omnibarType).thenReturn(OmnibarType.SINGLE_TOP)

        initializeViewModel()

        val viewState = testee.viewState.value
        assertTrue(viewState.dynamicInterface.isMenuButtonVisible)
        assertFalse(viewState.dynamicInterface.isBottomBarVisible)
        assertTrue(viewState.dynamicInterface.isFireButtonVisible)
        assertTrue(viewState.dynamicInterface.isNewTabButtonVisible)
    }

    @Test
    fun whenSplitOmnibarEnabledThenDynamicInterfaceHidesMenuButtonAndShowsBottomBar() = runTest {
        whenever(mockOmnibarFeatureRepository.omnibarType).thenReturn(OmnibarType.SPLIT)

        initializeViewModel()

        val viewState = testee.viewState.value
        assertFalse(viewState.dynamicInterface.isMenuButtonVisible)
        assertTrue(viewState.dynamicInterface.isBottomBarVisible)
        assertFalse(viewState.dynamicInterface.isFireButtonVisible)
        assertFalse(viewState.dynamicInterface.isNewTabButtonVisible)
    }

    private class FakeTabSwitcherDataStore : TabSwitcherDataStore {

        private val animationTileDismissedFlow = MutableStateFlow(false)

        override val data: Flow<TabSwitcherData>
            get() = flowOf(TabSwitcherData(NEW, GRID))

        override suspend fun setUserState(userState: UserState) {}

        override suspend fun setTabLayoutType(layoutType: LayoutType) {}

        override fun isTrackersAnimationInfoTileHidden(): Flow<Boolean> = animationTileDismissedFlow

        override suspend fun setTrackersAnimationInfoTileHidden(isHidden: Boolean) {
            animationTileDismissedFlow.value = isHidden
        }
    }

    private fun TestScope.prepareSelectionMode() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            testee.viewState.collect()
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
