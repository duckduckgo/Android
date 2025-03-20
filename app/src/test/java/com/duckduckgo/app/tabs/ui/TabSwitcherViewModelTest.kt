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
import com.duckduckgo.app.tabs.TabSwitcherAnimationFeature
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
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import com.duckduckgo.app.tabs.store.TabSwitcherDataStore
import com.duckduckgo.app.tabs.store.TabSwitcherPrefsDataStore
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedAppRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.blockingObserve
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.DuckChatPixelName
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.savedsites.api.SavedSitesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
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

    @Mock
    private lateinit var mockWebTrackersBlockedAppRepository: WebTrackersBlockedAppRepository

    @Mock
    private lateinit var mockTabSwitcherPrefsDataStore: TabSwitcherPrefsDataStore

    private lateinit var fakeTabSwitcherDataStore: TabSwitcherPrefsDataStore

    private val tabManagerFeatureFlags = FakeFeatureToggleFactory.create(TabManagerFeatureFlags::class.java)
    private val swipingTabsFeature = FakeFeatureToggleFactory.create(SwipingTabsFeature::class.java)
    private val swipingTabsFeatureProvider = SwipingTabsFeatureProvider(swipingTabsFeature)
    private val tabSwitcherAnimationFeature = FakeFeatureToggleFactory.create(TabSwitcherAnimationFeature::class.java)

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
        swipingTabsFeature.onForInternalUsers().setRawStoredState(State(enable = true))

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
        whenever(mockTabRepository.liveSelectedTab).thenReturn(liveData { null })
    }

    private fun initializeViewModel(tabSwitcherDataStore: TabSwitcherDataStore = mockTabSwitcherPrefsDataStore) {
        testee = TabSwitcherViewModel(
            mockTabRepository,
            mockWebViewSessionStorage,
            mockAdClickManager,
            coroutinesTestRule.testDispatcherProvider,
            mockPixel,
            swipingTabsFeatureProvider,
            duckChatMock,
            tabManagerFeatureFlags,
            tabSwitcherAnimationFeature,
            mockWebTrackersBlockedAppRepository,
            tabSwitcherDataStore,
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
        initializeViewModel()

        testee.onNewTabRequested(fromOverflowMenu = true)
        verify(mockTabRepository).add()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_NEW_TAB_PRESSED)
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenNewTabRequestedFromIconThenRepositoryNotifiedAndSwitcherClosedAndPixelSent() = runTest {
        initializeViewModel()

        testee.onNewTabRequested(fromOverflowMenu = false)
        verify(mockTabRepository).add()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_NEW_TAB_CLICKED)
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenTabSelectedThenRepositoryNotifiedAndSwitcherClosedAndPixelSent() = runTest {
        initializeViewModel()

        testee.onTabSelected("abc")
        verify(mockTabRepository).select(eq("abc"))
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_SWITCH_TABS)
        assertEquals(Command.Close, commandCaptor.lastValue)
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
    fun whenOnMarkTabAsDeletableAfterSwipeGestureUsedThenCallMarkDeletableAndSendPixel() = runTest {
        initializeViewModel()

        val swipeGestureUsed = true
        val tab = tabSwitcherItems.first()

        testee.onTabCloseInNormalModeRequested(tab, swipeGestureUsed)

        verify(mockTabRepository).markDeletable(tab.tabEntity)
        verify(mockAdClickManager, never()).clearTabId(tab.id)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_SWIPED)
    }

    @Test
    fun whenOnMarkTabAsDeletableAfterClosePressedThenCallMarkDeletableAndSendPixel() = runTest {
        initializeViewModel()

        val swipeGestureUsed = false
        val tab = tabSwitcherItems.first()

        testee.onTabCloseInNormalModeRequested(tab, swipeGestureUsed)

        verify(mockTabRepository).markDeletable(tab.tabEntity)
        verify(mockAdClickManager, never()).clearTabId(tab.id)
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_CLICKED)
    }

    @Test
    fun whenUndoDeletableTabThenUndoDeletable() = runTest {
        initializeViewModel()

        val entity = TabEntity("abc", "", "", position = 0)
        testee.undoDeletableTab(entity)

        verify(mockTabRepository).undoDeletable(entity)
    }

    @Test
    fun whenPurgeDeletableTabsThenCallRepositoryPurgeDeletableTabs() = runTest {
        initializeViewModel()

        whenever(mockTabRepository.getDeletableTabIds()).thenReturn(listOf("id_1", "id_2"))

        testee.purgeDeletableTabs()

        verify(mockTabRepository).getDeletableTabIds()
        verify(mockAdClickManager).clearTabId("id_1")
        verify(mockAdClickManager).clearTabId("id_2")
        verify(mockTabRepository).purgeDeletableTabs()
    }

    @Test
    fun whenRepositoryDeletableTabsUpdatesThenDeletableTabsEmits() = runTest {
        initializeViewModel()

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
        initializeViewModel()

        val tab = TabEntity("ID", position = 0)

        testee.deletableTabs.observeForever {
            assertEquals(listOf(tab), it)
        }

        repoDeletableTabs.send(listOf(tab))
        repoDeletableTabs.send(listOf(tab))
    }

    @Test
    fun whenOnCloseAllTabsRequestedThenEmitCommandCloseAllTabsRequest() = runTest {
        initializeViewModel()

        testee.onCloseAllTabsRequested()

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_PRESSED)
        assertEquals(Command.CloseAllTabsRequest(tabList.size), commandCaptor.lastValue)
    }

    @Test
    fun whenOnCloseAllTabsConfirmedThenTabDeletedAndTabIdClearedAndSessionDeletedAndPixelFired() = runTest {
        initializeViewModel()

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
    }

    @Test
    fun whenOnUpButtonPressedCalledThePixelSent() {
        initializeViewModel()

        testee.onUpButtonPressed()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_UP_BUTTON_PRESSED)
    }

    @Test
    fun whenOnBackButtonPressedCalledThePixelSent() {
        initializeViewModel()

        testee.onBackButtonPressed()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_BACK_BUTTON_PRESSED)
    }

    @Test
    fun whenOnMenuOpenedCalledThePixelSent() {
        initializeViewModel()

        testee.onMenuOpened()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_PRESSED)
    }

    @Test
    fun whenOnDownloadsMenuPressedCalledThePixelSent() {
        initializeViewModel()

        testee.onDownloadsMenuPressed()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_MENU_DOWNLOADS_PRESSED)
    }

    @Test
    fun whenOnSettingsMenuPressedCalledThePixelSent() {
        initializeViewModel()

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
        initializeViewModel()

        val fromIndex = 0
        val toIndex = 2

        testee.onTabMoved(fromIndex, toIndex)

        verify(mockTabRepository).updateTabPosition(fromIndex, toIndex)
    }

    @Test
    fun whenListLayoutTypeToggledCorrectPixelsAreFired() = runTest {
        initializeViewModel()

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
        initializeViewModel()

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
        initializeViewModel()

        whenever(duckChatMock.wasOpenedBefore()).thenReturn(false)

        testee.onDuckChatMenuClicked()

        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN)
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_NEW_TAB_MENU, mapOf("was_used_before" to "0"))
        verify(duckChatMock).openDuckChat()
    }

    @Test
    fun `when Duck Chat menu item clicked and it was used before then open Duck Chat and send a pixel`() = runTest {
        initializeViewModel()

        whenever(duckChatMock.wasOpenedBefore()).thenReturn(true)

        testee.onDuckChatMenuClicked()

        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN)
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_NEW_TAB_MENU, mapOf("was_used_before" to "1"))
        verify(duckChatMock).openDuckChat()
    }

    @Test
    fun `when animated info panel then tab switcher items include animation tile and tabs`() = runTest {
        tabSwitcherAnimationFeature.self().setRawStoredState(State(enable = true))

        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabs.value = listOf(tab1, tab2)

        whenever(mockTabSwitcherPrefsDataStore.isAnimationTileDismissed()).thenReturn(flowOf(false))
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)

        initializeViewModel()

        val items = testee.tabSwitcherItems.blockingObserve() ?: listOf()

        assertEquals(3, items.size)
        assert(items.first() is TabSwitcherItem.TrackerAnimationInfoPanel)
        assert(items[1] is TabSwitcherItem.Tab)
        assert(items[2] is TabSwitcherItem.Tab)
    }

    @Test
    fun `when animated info panel not visible then tab switcher items contain only tabs`() = runTest {
        tabSwitcherAnimationFeature.self().setRawStoredState(State(enable = true))

        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabs.value = listOf(tab1, tab2)

        whenever(mockTabSwitcherPrefsDataStore.isAnimationTileDismissed()).thenReturn(flowOf(true))

        initializeViewModel()

        val items = testee.tabSwitcherItems.blockingObserve() ?: listOf()

        assertEquals(2, items.size)
        items.forEach { item ->
            assert(item is TabSwitcherItem.Tab)
        }
    }

    @Test
    fun `when tab switcher animation feature disabled then tab switcher items contain only tabs`() = runTest {
        initializeViewModel(FakeTabSwitcherDataStore())
        tabSwitcherAnimationFeature.self().setRawStoredState(State(enable = false))
        whenever(mockTabSwitcherPrefsDataStore.isAnimationTileDismissed()).thenReturn(flowOf(true))

        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabs.value = listOf(tab1, tab2)

        val items = testee.tabSwitcherItems.blockingObserve() ?: listOf()

        assertEquals(2, items.size)
        items.forEach { item ->
            assert(item is TabSwitcherItem.Tab)
        }
    }

    @Test
    fun `when animated info panel positive button clicked then animated info panel is still visible`() = runTest {
        initializeViewModel(FakeTabSwitcherDataStore())
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)

        tabSwitcherAnimationFeature.self().setRawStoredState(State(enable = true))

        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabs.value = listOf(tab1, tab2)

        testee.onTrackerAnimationTilePositiveButtonClicked()

        val items = testee.tabSwitcherItems.blockingObserve() ?: listOf()

        assertTrue(items.first() is TabSwitcherItem.TrackerAnimationInfoPanel)
    }

    @Test
    fun `when animated info panel negative button clicked then animated info panel is removed`() = runTest {
        initializeViewModel(FakeTabSwitcherDataStore())

        tabSwitcherAnimationFeature.self().setRawStoredState(State(enable = true))

        val tab1 = TabEntity("1", position = 1)
        val tab2 = TabEntity("2", position = 2)
        tabs.value = listOf(tab1, tab2)

        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)

        testee.onTrackerAnimationTileNegativeButtonClicked()

        val items = testee.tabSwitcherItems.blockingObserve() ?: listOf()

        assertFalse(items.first() is TabSwitcherItem.TrackerAnimationInfoPanel)
    }

    @Test
    fun `when animated info panel visible then impressions pixel fired`() = runTest {
        initializeViewModel(FakeTabSwitcherDataStore())
        tabSwitcherAnimationFeature.self().setRawStoredState(State(enable = true))
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)

        testee.onTrackerAnimationInfoPanelVisible()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_INFO_PANEL_IMPRESSIONS)
    }

    @Test
    fun `when animated info panel clicked then tapped pixel fired`() = runTest {
        initializeViewModel(FakeTabSwitcherDataStore())
        tabSwitcherAnimationFeature.self().setRawStoredState(State(enable = true))
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)

        testee.onTrackerAnimationInfoPanelClicked()

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_INFO_PANEL_TAPPED)
    }

    @Test
    fun `when animated info panel negative button clicked then dismiss pixel fired`() = runTest {
        initializeViewModel(FakeTabSwitcherDataStore())
        tabSwitcherAnimationFeature.self().setRawStoredState(State(enable = true))
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(15)

        testee.onTrackerAnimationTileNegativeButtonClicked()

        verify(mockPixel).fire(pixel = AppPixelName.TAB_MANAGER_INFO_PANEL_DISMISSED, parameters = mapOf("trackerCount" to "15"))
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
}
