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

package com.duckduckgo.app.tabs.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_GRID_VIEW_BUTTON_CLICKED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_INFO_PANEL_DISMISSED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_INFO_PANEL_TAPPED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_LIST_VIEW_BUTTON_CLICKED
import com.duckduckgo.app.pixels.duckchat.createWasUsedBeforePixelParams
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.store.TabSwitcherDataStore
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.SelectableTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackerAnimationInfoPanel
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackerAnimationInfoPanel.Companion.TRACKER_ANIMATION_PANEL_ID
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.BookmarkTabsRequest
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.DismissAnimatedTileDismissalDialog
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShareLink
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShareLinks
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShowAnimatedTileDismissalDialog
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShowUndoBookmarkMessage
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.FabType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Normal
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Selection
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedAppRepository
import com.duckduckgo.common.ui.tabs.SwipingTabsFeatureProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import javax.inject.Inject
import kotlin.Boolean
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@ContributesViewModel(ActivityScope::class)
class TabSwitcherViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
    private val swipingTabsFeature: SwipingTabsFeatureProvider,
    private val duckChat: DuckChat,
    private val duckAiFeatureState: DuckAiFeatureState,
    private val tabManagerFeatureFlags: TabManagerFeatureFlags,
    private val webTrackersBlockedAppRepository: WebTrackersBlockedAppRepository,
    private val tabSwitcherDataStore: TabSwitcherDataStore,
    private val faviconManager: FaviconManager,
    private val savedSitesRepository: SavedSitesRepository,
) : ViewModel() {

    val activeTab = tabRepository.liveSelectedTab
    val deletableTabs: LiveData<List<TabEntity>> = tabRepository.flowDeletableTabs.asLiveData(
        context = viewModelScope.coroutineContext,
    )

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val tabSwitcherItemsFlow = tabRepository.flowTabs
        .debounce(100.milliseconds)
        .conflate()
        .flatMapLatest { tabEntities ->
            combine(
                tabRepository.flowSelectedTab,
                _selectionViewState,
                tabSwitcherDataStore.isAnimationTileDismissed(),
            ) { activeTab, viewState, isAnimationTileDismissed ->
                getTabItems(tabEntities, activeTab, isAnimationTileDismissed, viewState.mode)
            }
        }

    val tabSwitcherItemsLiveData: LiveData<List<TabSwitcherItem>> = tabSwitcherItemsFlow.asLiveData()

    val isNewToolbarEnabled: Boolean by lazy {
        tabManagerFeatureFlags.newToolbarFeature().isEnabled()
    }

    private val _selectionViewState = MutableStateFlow(SelectionViewState(isNewToolbarEnabled = isNewToolbarEnabled))
    val selectionViewState = combine(
        _selectionViewState,
        tabSwitcherItemsFlow,
        tabRepository.tabSwitcherData,
        duckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus,
    ) { viewState, tabSwitcherItems, tabSwitcherData, showDuckAiButton ->
        viewState.copy(
            tabSwitcherItems = tabSwitcherItems,
            layoutType = tabSwitcherData.layoutType,
            isDuckAIButtonVisible = showDuckAiButton,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SelectionViewState(isNewToolbarEnabled = isNewToolbarEnabled))

    val layoutType = tabRepository.tabSwitcherData
        .map { it.layoutType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    // all tab items, including the animated tile
    val tabSwitcherItems: List<TabSwitcherItem>
        get() {
            return if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
                selectionViewState.value.tabSwitcherItems
            } else {
                tabSwitcherItemsLiveData.value.orEmpty()
            }
        }

    // only the actual browser tabs
    val tabs: List<Tab>
        get() = tabSwitcherItems.filterIsInstance<Tab>()

    private val recentlySavedBookmarks = mutableListOf<Bookmark>()

    // to be used in places where selection mode is required
    private val selectionMode: Selection
        get() = requireNotNull(selectionViewState.value.mode as Selection)

    sealed class Command {
        data object Close : Command()
        data class CloseAndShowUndoMessage(val deletedTabIds: List<String>) : Command()
        data class CloseTabsRequest(
            val tabIds: List<String>,
            val isClosingOtherTabs: Boolean = false,
        ) : Command()

        data class CloseAllTabsRequest(val numTabs: Int) : Command()
        data object ShowAnimatedTileDismissalDialog : Command()
        data object DismissAnimatedTileDismissalDialog : Command()
        data class ShareLink(
            val link: String,
            val title: String,
        ) : Command()

        data class ShareLinks(val links: List<String>) : Command()
        data class BookmarkTabsRequest(val tabIds: List<String>) : Command()
        data class ShowUndoBookmarkMessage(val numBookmarks: Int) : Command()
        data class ShowUndoDeleteTabsMessage(val tabIds: List<String>) : Command()
    }

    fun onNewTabRequested(fromOverflowMenu: Boolean = false) = viewModelScope.launch {
        if (swipingTabsFeature.isEnabled) {
            val newTab = tabs.firstOrNull { tabItem -> tabItem.isNewTabPage }
            if (newTab != null) {
                tabRepository.select(tabId = newTab.id)
            } else {
                tabRepository.add()
            }
        } else {
            tabRepository.add()
        }

        command.value = Command.Close
        if (fromOverflowMenu) {
            pixel.fire(AppPixelName.TAB_MANAGER_MENU_NEW_TAB_PRESSED)
        } else {
            pixel.fire(AppPixelName.TAB_MANAGER_NEW_TAB_CLICKED)
        }
    }

    suspend fun onTabSelected(tabId: String) {
        val mode = selectionViewState.value.mode as? Selection ?: Normal
        if (tabManagerFeatureFlags.multiSelection().isEnabled() && mode is Selection) {
            if (tabId in mode.selectedTabs) {
                pixel.fire(AppPixelName.TAB_MANAGER_TAB_DESELECTED)
                unselectTab(tabId)
            } else {
                pixel.fire(AppPixelName.TAB_MANAGER_TAB_SELECTED)
                selectTab(tabId)
            }
        } else {
            tabRepository.select(tabId)
            command.value = Command.Close
            pixel.fire(AppPixelName.TAB_MANAGER_SWITCH_TABS)
        }
    }

    suspend fun onUndoDeleteSnackbarDismissed(tabIds: List<String>) {
        // delete only recently deleted tabs, because others may need to be preserved for restoring
        deleteTabs(tabIds)
    }

    private suspend fun deleteTabs(tabIds: List<String>) {
        tabRepository.deleteTabs(tabIds.filterNot { it == TRACKER_ANIMATION_PANEL_ID })
    }

    private fun triggerEmptySelectionMode() {
        _selectionViewState.update { it.copy(mode = Selection(emptyList())) }
    }

    private fun triggerNormalMode() {
        _selectionViewState.update { it.copy(mode = Normal) }
    }

    fun onSelectAllTabs() {
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SELECT_ALL)
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SELECT_ALL_DAILY, type = Daily())

        _selectionViewState.update { it.copy(mode = Selection(selectedTabs = tabs.map { tab -> tab.id })) }
    }

    fun onDeselectAllTabs() {
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_DESELECT_ALL)
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_DESELECT_ALL_DAILY, type = Daily())

        triggerEmptySelectionMode()
    }

    fun onShareSelectedTabs() {
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SHARE_LINKS)
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SHARE_LINKS_DAILY, type = Daily())

        val selectedTabs = tabs
            .filterIsInstance<SelectableTab>()
            .filter { it.isSelected && !it.isNewTabPage }

        if (selectedTabs.size == 1) {
            val entity = selectedTabs.first().tabEntity
            command.value = ShareLink(
                link = entity.url.orEmpty(),
                title = entity.title.orEmpty(),
            )
        } else {
            val links = selectedTabs.mapNotNull { it.tabEntity.url }
            command.value = ShareLinks(links)
        }
    }

    fun onBookmarkSelectedTabs() {
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_BOOKMARK_TABS)
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_BOOKMARK_TABS_DAILY, type = Daily())

        (selectionViewState.value.mode as? Selection)?.let { mode ->
            command.value = BookmarkTabsRequest(mode.selectedTabs)
        }
    }

    fun undoBookmarkAction() {
        viewModelScope.launch(dispatcherProvider.io()) {
            recentlySavedBookmarks.forEach { bookmark ->
                savedSitesRepository.delete(bookmark)
            }

            recentlySavedBookmarks.clear()
        }
    }

    fun finishBookmarkAction() {
        recentlySavedBookmarks.clear()
    }

    fun onBookmarkTabsConfirmed(tabIds: List<String>) {
        viewModelScope.launch {
            recentlySavedBookmarks.addAll(bookmarkTabs(tabIds))
            command.value = ShowUndoBookmarkMessage(recentlySavedBookmarks.size)
        }
    }

    private suspend fun bookmarkTabs(tabIds: List<String>): List<Bookmark> {
        val results = tabIds.map { tabId ->
            viewModelScope.async {
                saveSiteBookmark(tabId)
            }
        }
        return results.awaitAll().filterNotNull()
    }

    fun onSelectionModeRequested() {
        pixel.fire(AppPixelName.TAB_MANAGER_MENU_SELECT_TABS)
        pixel.fire(AppPixelName.TAB_MANAGER_MENU_SELECT_TABS_DAILY, type = Daily())

        triggerEmptySelectionMode()
    }

    // user has indicated they want to close all tabs
    fun onCloseAllTabsRequested() {
        command.value = Command.CloseAllTabsRequest(tabs.size)

        pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_PRESSED)
        pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_PRESSED_DAILY, type = Daily())
    }

    // user has indicated they want to close selected tabs
    fun onCloseSelectedTabsRequested(fromOverflowMenu: Boolean = false) {
        if (fromOverflowMenu) {
            pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_CLOSE_TABS)
            pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_CLOSE_TABS_DAILY, type = Daily())
        } else {
            pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TABS)
            pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TABS_DAILY, type = Daily())
        }

        val selectedTabs = selectionMode.selectedTabs
        val allTabsCount = tabs.size
        command.value = if (allTabsCount == selectedTabs.size) {
            Command.CloseAllTabsRequest(allTabsCount)
        } else {
            Command.CloseTabsRequest(selectedTabs)
        }
    }

    // user has indicated they want to close all tabs except the selected ones
    fun onCloseOtherTabsRequested() {
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_CLOSE_OTHER_TABS)
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_CLOSE_OTHER_TABS_DAILY, type = Daily())

        val selectedTabs = selectionMode.selectedTabs
        val otherTabsIds = (tabs.map { it.id }) - selectedTabs.toSet()
        if (otherTabsIds.isNotEmpty()) {
            command.value = Command.CloseTabsRequest(otherTabsIds, isClosingOtherTabs = true)
        }
    }

    // user has confirmed they want to close tabs
    fun onCloseTabsConfirmed(tabIds: List<String>) {
        viewModelScope.launch {
            if (selectionViewState.value.mode is Selection) {
                unselectTabs(tabIds)
            }

            if (tabs.size == tabIds.size) {
                pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED)
                pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED_DAILY, type = Daily())

                if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
                    // mark tabs as deletable, the undo snackbar will be displayed when the tab switcher is closed
                    tabRepository.markDeletable(tabIds)
                    command.value = Command.CloseAndShowUndoMessage(tabIds)
                } else {
                    // all tabs can be deleted immediately because no snackbar is needed and the tab switcher will be closed
                    deleteTabs(tabIds)
                    command.value = Command.Close
                }
            } else {
                pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TABS_CONFIRMED)
                pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TABS_CONFIRMED_DAILY, type = Daily())

                // mark tabs as deletable and show undo snackbar
                tabRepository.markDeletable(tabIds)
                command.value = Command.ShowUndoDeleteTabsMessage(tabIds)
            }
        }
    }

    // user has confirmed they want to close all tabs -> mark all tabs as deletable and show undo snackbar
    fun onCloseAllTabsConfirmed() {
        onCloseTabsConfirmed(tabs.map { it.id })
    }

    fun onTabCloseInNormalModeRequested(
        tab: Tab,
        swipeGestureUsed: Boolean = false,
    ) {
        viewModelScope.launch {
            if (tabs.size == 1) {
                if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
                    // mark the tab as deletable, the undo snackbar will be shown after tab switcher is closed
                    markTabAsDeletable(tab, swipeGestureUsed)
                    command.value = Command.CloseAndShowUndoMessage(listOf(tab.id))
                } else {
                    // the last tab can be deleted immediately because no snackbar is needed and the tab switcher will be closed
                    deleteTabs(listOf(tab.id))
                    command.value = Command.Close
                }
            } else {
                markTabAsDeletable(tab, swipeGestureUsed)

                // when the feature flag is disabled, the undo snackbar is shown via deletable tabs observer
                if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
                    command.value = Command.ShowUndoDeleteTabsMessage(listOf(tab.id))
                }
            }
        }
    }

    private suspend fun markTabAsDeletable(
        tab: Tab,
        swipeGestureUsed: Boolean,
    ) {
        tabRepository.markDeletable(tab.tabEntity)
        if (swipeGestureUsed) {
            pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_SWIPED)
        } else {
            pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_CLICKED)
        }

        if (selectionViewState.value.mode is Selection) {
            unselectTab(tab.id)
        }
    }

    // user has tapped the Undo action -> restore the closed tab
    suspend fun onUndoDeleteTab(tab: TabEntity) {
        tabRepository.undoDeletable(tab)
    }

    // user has tapped the Undo action -> restore the closed tabs
    suspend fun onUndoDeleteTabs(tabIds: List<String>) {
        tabRepository.undoDeletable(tabIds)
    }

    // user has not tapped the Undo action -> purge the deletable tabs and remove all data
    suspend fun purgeDeletableTabs() {
        tabRepository.purgeDeletableTabs()
    }

    private fun unselectTab(tabId: String) = unselectTabs(listOf(tabId))

    private fun selectTab(tabId: String) = selectTabs(listOf(tabId))

    private fun unselectTabs(tabIds: List<String>) {
        _selectionViewState.update {
            it.copy(mode = Selection(selectedTabs = selectionMode.selectedTabs - tabIds.toSet()))
        }
    }

    private fun selectTabs(tabIds: List<String>) {
        _selectionViewState.update {
            it.copy(mode = Selection(selectedTabs = selectionMode.selectedTabs + tabIds.toSet()))
        }
    }

    fun onEmptyAreaClicked() {
        if (tabManagerFeatureFlags.multiSelection().isEnabled() && selectionViewState.value.mode is Selection) {
            triggerNormalMode()
        }
    }

    fun onUpButtonPressed() {
        pixel.fire(AppPixelName.TAB_MANAGER_UP_BUTTON_PRESSED)

        if (tabManagerFeatureFlags.multiSelection().isEnabled() && selectionViewState.value.mode is Selection) {
            triggerNormalMode()
        } else {
            command.value = Command.Close
        }
    }

    fun onBackButtonPressed() {
        pixel.fire(AppPixelName.TAB_MANAGER_BACK_BUTTON_PRESSED)

        if (tabManagerFeatureFlags.multiSelection().isEnabled() && selectionViewState.value.mode is Selection) {
            triggerNormalMode()
        } else {
            command.value = Command.Close
        }
    }

    fun onMenuOpened() {
        if (tabManagerFeatureFlags.multiSelection().isEnabled() && selectionViewState.value.mode is Selection) {
            pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_PRESSED)
        } else {
            pixel.fire(AppPixelName.TAB_MANAGER_MENU_PRESSED)
        }
    }

    fun onDownloadsMenuPressed() {
        pixel.fire(AppPixelName.TAB_MANAGER_MENU_DOWNLOADS_PRESSED)
    }

    fun onSettingsMenuPressed() {
        pixel.fire(AppPixelName.TAB_MANAGER_MENU_SETTINGS_PRESSED)
    }

    fun onTabMoved(
        fromIndex: Int,
        toIndex: Int,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            tabRepository.updateTabPosition(fromIndex, toIndex)
        }
    }

    fun onTabDraggingStarted() {
        viewModelScope.launch(dispatcherProvider.io()) {
            val params = mapOf("userState" to tabRepository.tabSwitcherData.first().userState.name)
            pixel.fire(AppPixelName.TAB_MANAGER_REARRANGE_TABS_DAILY, parameters = params, encodedParameters = emptyMap(), Daily())
        }
    }

    fun onLayoutTypeToggled() {
        when (layoutType.value) {
            GRID -> onListLayoutSelected()
            LIST -> onGridLayoutSelected()
            else -> Unit
        }
    }

    fun onListLayoutSelected() {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (layoutType.value != LIST) {
                pixel.fire(TAB_MANAGER_LIST_VIEW_BUTTON_CLICKED)
                tabRepository.setTabLayoutType(LIST)
            }
        }
    }

    fun onGridLayoutSelected() {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (layoutType.value != GRID) {
                pixel.fire(TAB_MANAGER_GRID_VIEW_BUTTON_CLICKED)
                tabRepository.setTabLayoutType(GRID)
            }
        }
    }

    fun onFabClicked() {
        when (selectionViewState.value.dynamicInterface.mainFabType) {
            FabType.NEW_TAB -> onNewTabRequested()
            FabType.CLOSE_TABS -> onCloseSelectedTabsRequested()
        }
    }

    private suspend fun saveSiteBookmark(tabId: String) = withContext(dispatcherProvider.io()) {
        val targetTab = tabs.firstOrNull { it.id == tabId }
        val targetTabUrl = targetTab?.tabEntity?.url
        val isUrlNotBlank = targetTabUrl?.isNotBlank() ?: false

        if (isUrlNotBlank) {
            // Only bookmark new sites
            savedSitesRepository.getBookmark(targetTabUrl!!) ?: run {
                faviconManager.persistCachedFavicon(tabId, targetTabUrl)
                return@withContext savedSitesRepository.insertBookmark(targetTabUrl, targetTab.tabEntity.title.orEmpty())
            }
        }
        return@withContext null
    }

    fun onDuckAIFabClicked() {
        viewModelScope.launch {
            val params = duckChat.createWasUsedBeforePixelParams()
            pixel.fire(DuckChatPixelName.DUCK_CHAT_OPEN_TAB_SWITCHER_FAB, parameters = params)

            duckChat.openDuckChat()
        }
    }

    fun onDuckAIButtonClicked() {
        viewModelScope.launch {
            val params = duckChat.createWasUsedBeforePixelParams()
            pixel.fire(DuckChatPixelName.DUCK_CHAT_OPEN_TAB_SWITCHER_FAB, parameters = params)

            duckChat.openDuckChat()
        }
    }

    fun onTrackerAnimationInfoPanelClicked() {
        pixel.fire(pixel = TAB_MANAGER_INFO_PANEL_TAPPED)
        command.value = ShowAnimatedTileDismissalDialog
    }

    fun onTrackerAnimationTilePositiveButtonClicked() {
        viewModelScope.launch {
            tabSwitcherDataStore.setIsAnimationTileDismissed(isDismissed = true)
            val trackerCount = webTrackersBlockedAppRepository.getTrackerCountForLast7Days()
            val bucketSize: Int = when (trackerCount) {
                0 -> BUCKET_SIZE_0
                in 1..9 -> BUCKET_SIZE_1
                in 10..24 -> BUCKET_SIZE_10
                in 25..49 -> BUCKET_SIZE_25
                in 50..74 -> BUCKET_SIZE_50
                in 75..99 -> BUCKET_SIZE_75
                in 100..149 -> BUCKET_SIZE_100
                in 150..199 -> BUCKET_SIZE_150
                in 200..499 -> BUCKET_SIZE_200
                else -> BUCKET_SIZE_500
            }

            pixel.fire(
                pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
                parameters = mapOf("trackerCount" to bucketSize.toString())
            )
        }
    }

    fun onTrackerAnimationTileNegativeButtonClicked() {
        viewModelScope.launch {
            command.value = DismissAnimatedTileDismissalDialog
        }
    }

    fun onTrackerAnimationInfoPanelVisible() {
        pixel.fire(pixel = AppPixelName.TAB_MANAGER_INFO_PANEL_IMPRESSIONS)
    }

    private suspend fun getTabItems(
        tabEntities: List<TabEntity>,
        activeTab: TabEntity?,
        isAnimationTileDismissed: Boolean,
        mode: Mode,
    ): List<TabSwitcherItem> {
        val normalTabs = tabEntities.map {
            NormalTab(it, isActive = it.tabId == activeTab?.tabId)
        }

        suspend fun getNormalTabItemsWithOptionalAnimationTile(): List<TabSwitcherItem> {
            return if(!isAnimationTileDismissed) {
                val trackerCountForLast7Days = webTrackersBlockedAppRepository.getTrackerCountForLast7Days()

                listOf(TrackerAnimationInfoPanel(trackerCountForLast7Days)) + normalTabs
            } else {
                normalTabs
            }
        }

        return if (tabManagerFeatureFlags.multiSelection().isEnabled() && mode is Selection) {
            tabEntities.map {
                SelectableTab(it, isSelected = it.tabId in mode.selectedTabs)
            }
        } else {
            getNormalTabItemsWithOptionalAnimationTile()
        }
    }

    data class SelectionViewState(
        val tabSwitcherItems: List<TabSwitcherItem> = emptyList(),
        val mode: Mode = Normal,
        val layoutType: LayoutType? = null,
        val isDuckAIButtonVisible: Boolean = false,
        val isNewToolbarEnabled: Boolean = false,
    ) {
        val tabs: List<Tab> = tabSwitcherItems.filterIsInstance<Tab>()
        val numSelectedTabs: Int = (mode as? Selection)?.selectedTabs?.size ?: 0

        val dynamicInterface = when (mode) {
            is Normal -> {
                val isThereOnlyNewTabPage = tabs.size == 1 && tabs.first().isNewTabPage
                DynamicInterface(
                    isFireButtonVisible = true,
                    isNewTabButtonVisible = isNewToolbarEnabled,
                    isNewTabMenuVisible = !isNewToolbarEnabled,
                    isDuckAIButtonVisible = isDuckAIButtonVisible && isNewToolbarEnabled,
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
                    isMenuButtonEnabled = !isThereOnlyNewTabPage,
                    isMainFabVisible = !isNewToolbarEnabled,
                    isAIFabVisible = isDuckAIButtonVisible && !isNewToolbarEnabled,
                    mainFabType = FabType.NEW_TAB,
                    backButtonType = BackButtonType.ARROW,
                    layoutButtonMode = when {
                        layoutType == GRID && !isNewToolbarEnabled -> LayoutMode.LIST
                        layoutType == LIST && !isNewToolbarEnabled -> LayoutMode.GRID
                        else -> LayoutMode.HIDDEN
                    },
                    layoutMenuMode = when {
                        layoutType == GRID && isNewToolbarEnabled -> LayoutMode.LIST
                        layoutType == LIST && isNewToolbarEnabled -> LayoutMode.GRID
                        else -> LayoutMode.HIDDEN
                    },
                )
            }

            is Selection -> {
                val areAllTabsSelected = numSelectedTabs == tabs.size
                val isSomethingSelected = numSelectedTabs > 0
                val isNtpTheOnlySelectedTab = numSelectedTabs == 1 &&
                    tabs.any { it is SelectableTab && it.isSelected && it.isNewTabPage }
                val isSelectionActionable = isSomethingSelected && !isNtpTheOnlySelectedTab
                DynamicInterface(
                    isFireButtonVisible = false,
                    isNewTabButtonVisible = false,
                    isNewTabMenuVisible = false,
                    isDuckAIButtonVisible = false,
                    isSelectAllVisible = !areAllTabsSelected,
                    isDeselectAllVisible = areAllTabsSelected,
                    isSelectionActionsDividerVisible = isSelectionActionable,
                    isShareSelectedLinksVisible = isSelectionActionable,
                    isBookmarkSelectedTabsVisible = isSelectionActionable,
                    isSelectTabsDividerVisible = false,
                    isSelectTabsVisible = false,
                    isCloseSelectedTabsVisible = isSomethingSelected,
                    isCloseOtherTabsVisible = isSomethingSelected && !areAllTabsSelected,
                    isCloseAllTabsDividerVisible = isSomethingSelected,
                    isCloseAllTabsVisible = false,
                    isMenuButtonEnabled = true,
                    isMainFabVisible = isSomethingSelected && !isNewToolbarEnabled,
                    isAIFabVisible = false,
                    mainFabType = FabType.CLOSE_TABS,
                    backButtonType = BackButtonType.CLOSE,
                    layoutButtonMode = LayoutMode.HIDDEN,
                    layoutMenuMode = LayoutMode.HIDDEN,
                )
            }
        }

        data class DynamicInterface(
            val isFireButtonVisible: Boolean,
            val isNewTabButtonVisible: Boolean,
            val isNewTabMenuVisible: Boolean,
            val isDuckAIButtonVisible: Boolean,
            val isSelectAllVisible: Boolean,
            val isDeselectAllVisible: Boolean,
            val isSelectionActionsDividerVisible: Boolean,
            val isShareSelectedLinksVisible: Boolean,
            val isBookmarkSelectedTabsVisible: Boolean,
            val isSelectTabsDividerVisible: Boolean,
            val isSelectTabsVisible: Boolean,
            val isCloseSelectedTabsVisible: Boolean,
            val isCloseOtherTabsVisible: Boolean,
            val isCloseAllTabsDividerVisible: Boolean,
            val isCloseAllTabsVisible: Boolean,
            val isMenuButtonEnabled: Boolean,
            val isMainFabVisible: Boolean,
            val isAIFabVisible: Boolean,
            val mainFabType: FabType,
            val backButtonType: BackButtonType,
            val layoutButtonMode: LayoutMode,
            val layoutMenuMode: LayoutMode,
        )

        enum class FabType {
            NEW_TAB,
            CLOSE_TABS,
        }

        enum class BackButtonType {
            ARROW,
            CLOSE,
        }

        enum class LayoutMode {
            GRID,
            LIST,
            HIDDEN,
        }

        sealed interface Mode {
            data object Normal : Mode
            data class Selection(
                val selectedTabs: List<String> = emptyList(),
            ) : Mode
        }
    }


    companion object {
        private const val BUCKET_SIZE_0 = 0
        private const val BUCKET_SIZE_1 = 1
        private const val BUCKET_SIZE_10 = 10
        private const val BUCKET_SIZE_25 = 40
        private const val BUCKET_SIZE_50 = 50
        private const val BUCKET_SIZE_75 = 75
        private const val BUCKET_SIZE_100 = 100
        private const val BUCKET_SIZE_150 = 150
        private const val BUCKET_SIZE_200 = 200
        private const val BUCKET_SIZE_500 = 500
    }
}
