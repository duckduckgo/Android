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
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.SwipingTabsFeatureProvider
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_GRID_VIEW_BUTTON_CLICKED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_LIST_VIEW_BUTTON_CLICKED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.SelectableTab
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.BookmarkTabsRequest
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShareLink
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShareLinks
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShowUndoBookmarkMessage
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.BackButtonType.ARROW
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.BackButtonType.CLOSE
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.FabType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Normal
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Selection
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.common.utils.extensions.toBinaryString
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.DuckChatPixelName
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
@ContributesViewModel(ActivityScope::class)
class TabSwitcherViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val adClickManager: AdClickManager,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
    private val swipingTabsFeature: SwipingTabsFeatureProvider,
    private val duckChat: DuckChat,
    private val tabManagerFeatureFlags: TabManagerFeatureFlags,
    private val faviconManager: FaviconManager,
    private val savedSitesRepository: SavedSitesRepository,
) : ViewModel() {

    val activeTab = tabRepository.liveSelectedTab
    val deletableTabs: LiveData<List<TabEntity>> = tabRepository.flowDeletableTabs.asLiveData(
        context = viewModelScope.coroutineContext,
    )

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val tabsFlow = tabRepository.flowTabs
        .debounce(100.milliseconds)
        .conflate()

    private val _selectionViewState = MutableStateFlow(SelectionViewState())
    val selectionViewState = combine(
        _selectionViewState,
        tabsFlow,
        tabRepository.flowSelectedTab,
        tabRepository.tabSwitcherData,
    ) { viewState, tabs, activeTab, tabSwitcherData ->
        viewState.copy(
            tabItems = tabs.map {
                if (viewState.mode is Selection) {
                    SelectableTab(it, isSelected = it.tabId in viewState.mode.selectedTabs)
                } else {
                    NormalTab(it, isActive = it.tabId == activeTab?.tabId)
                }
            },
            layoutType = tabSwitcherData.layoutType,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SelectionViewState())

    val tabSwitcherItems: LiveData<List<TabSwitcherItem>> = tabsFlow
        .map { tabEntities ->
            val activeTabId = activeTab.value?.tabId
            tabEntities.map {
                NormalTab(it, isActive = it.tabId == activeTabId)
            }
        }.asLiveData()

    val layoutType = tabRepository.tabSwitcherData
        .map { it.layoutType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val tabItems: List<TabSwitcherItem>
        get() {
            return if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
                selectionViewState.value.tabItems
            } else {
                tabSwitcherItems.value.orEmpty()
            }
        }

    private val recentlySavedBookmarks = mutableListOf<Bookmark>()

    sealed class Command {
        data object Close : Command()
        data class CloseTabsRequest(val tabIds: List<String>, val isClosingOtherTabs: Boolean = false) : Command()
        data class CloseAllTabsRequest(val numTabs: Int) : Command()
        data class ShareLink(val link: String, val title: String) : Command()
        data class ShareLinks(val links: List<String>) : Command()
        data class BookmarkTabsRequest(val tabIds: List<String>) : Command()
        data class ShowUndoBookmarkMessage(val numBookmarks: Int) : Command()
        data class ShowUndoDeleteTabsMessage(val tabIds: List<String>) : Command()
    }

    fun onNewTabRequested(fromOverflowMenu: Boolean = false) = viewModelScope.launch {
        if (swipingTabsFeature.isEnabled) {
            val newTab = tabItems
                .filterIsInstance<Tab>()
                .firstOrNull { tabItem -> tabItem.isNewTabPage }

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

    private suspend fun deleteTabs(tabIds: List<String>) = withContext(dispatcherProvider.io()) {
        tabRepository.deleteTabs(tabIds)

        tabIds.forEach { tabId ->
            adClickManager.clearTabId(tabId)
            webViewSessionStorage.deleteSession(tabId)
        }
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

        _selectionViewState.update { it.copy(mode = Selection(selectedTabs = tabItems.map { tab -> tab.id })) }
    }

    fun onDeselectAllTabs() {
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_DESELECT_ALL)
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_DESELECT_ALL_DAILY, type = Daily())

        triggerEmptySelectionMode()
    }

    fun onShareSelectedTabs() {
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SHARE_LINKS)
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_SHARE_LINKS_DAILY, type = Daily())

        val selectedTabs = tabItems
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
        command.value = Command.CloseAllTabsRequest(tabItems.size)

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

        (selectionViewState.value.mode as? Selection)?.selectedTabs?.let { selectedTabs ->
            val allTabsCount = tabItems.size
            command.value = if (allTabsCount == selectedTabs.size) {
                Command.CloseAllTabsRequest(allTabsCount)
            } else {
                Command.CloseTabsRequest(selectedTabs)
            }
        }
    }

    // user has indicated they want to close all tabs except the selected ones
    fun onCloseOtherTabsRequested() {
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_CLOSE_OTHER_TABS)
        pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_CLOSE_OTHER_TABS_DAILY, type = Daily())

        (selectionViewState.value.mode as? Selection)?.selectedTabs?.let { selectedTabs ->
            val otherTabsIds = (tabItems.map { it.id }) - selectedTabs.toSet()
            if (otherTabsIds.isNotEmpty()) {
                command.value = Command.CloseTabsRequest(otherTabsIds, isClosingOtherTabs = true)
            }
        }
    }

    // user has confirmed they want to close tabs
    fun onCloseTabsConfirmed(tabIds: List<String>) {
        viewModelScope.launch {
            if (selectionViewState.value.mode is Selection) {
                unselectTabs(tabIds)
            }

            if (tabItems.size == tabIds.size) {
                pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED)
                pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED_DAILY, type = Daily())

                // all tabs can be deleted immediately because no snackbar is needed and the tab switcher will be closed
                deleteTabs(tabIds)
                command.value = Command.Close
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
        onCloseTabsConfirmed(tabItems.map { it.id })
    }

    fun onTabCloseInNormalModeRequested(tab: Tab, swipeGestureUsed: Boolean = false) {
        viewModelScope.launch {
            if (tabItems.size == 1) {
                // the last tab can be deleted immediately because no snackbar is needed and the tab switcher will be closed
                deleteTabs(listOf(tab.id))
                command.value = Command.Close
            } else {
                markTabAsDeletable(tab, swipeGestureUsed)

                // when the feature flag is disabled, the undo snackbar is shown via deletable tabs observer
                if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
                    command.value = Command.ShowUndoDeleteTabsMessage(listOf(tab.id))
                }
            }
        }
    }

    private suspend fun markTabAsDeletable(tab: Tab, swipeGestureUsed: Boolean) {
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
    suspend fun undoDeletableTab(tab: TabEntity) {
        tabRepository.undoDeletable(tab)
    }

    // user has tapped the Undo action -> restore the closed tabs
    fun undoDeletableTabs(tabIds: List<String>) {
        viewModelScope.launch(dispatcherProvider.io()) {
            tabRepository.undoDeletable(tabIds)
        }
    }

    // user has not tapped the Undo action -> purge the deletable tabs and remove all data
    suspend fun purgeDeletableTabs() {
        tabRepository.getDeletableTabIds().forEach {
            adClickManager.clearTabId(it)
        }
        tabRepository.purgeDeletableTabs()
    }

    private fun unselectTab(tabId: String) = unselectTabs(listOf(tabId))

    private fun selectTab(tabId: String) = selectTabs(listOf(tabId))

    private fun unselectTabs(tabIds: List<String>) {
        (selectionViewState.value.mode as? Selection)?.let { selectionMode ->
            _selectionViewState.update {
                it.copy(mode = Selection(selectedTabs = selectionMode.selectedTabs - tabIds.toSet()))
            }
        }
    }

    private fun selectTabs(tabIds: List<String>) {
        (selectionViewState.value.mode as? Selection)?.let { selectionMode ->
            _selectionViewState.update {
                it.copy(mode = Selection(selectedTabs = selectionMode.selectedTabs + tabIds.toSet()))
            }
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

    fun onTabMoved(fromIndex: Int, toIndex: Int) {
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
        viewModelScope.launch(dispatcherProvider.io()) {
            when (layoutType.value) {
                GRID -> {
                    pixel.fire(TAB_MANAGER_LIST_VIEW_BUTTON_CLICKED)
                    tabRepository.setTabLayoutType(LIST)
                }
                LIST -> {
                    pixel.fire(TAB_MANAGER_GRID_VIEW_BUTTON_CLICKED)
                    tabRepository.setTabLayoutType(GRID)
                }
                else -> Unit
            }
        }
    }

    fun onFabClicked() {
        when (selectionViewState.value.dynamicInterface.fabType) {
            FabType.NEW_TAB -> onNewTabRequested()
            FabType.CLOSE_TABS -> onCloseSelectedTabsRequested()
        }
    }

    private suspend fun saveSiteBookmark(tabId: String) = withContext(dispatcherProvider.io()) {
        val targetTabItem = tabItems.firstOrNull { it.id == tabId }
        val targetTab = targetTabItem?.takeIf { it is Tab }?.let { it as Tab }

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

    fun onDuckChatMenuClicked() {
        viewModelScope.launch {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_OPEN)

            val wasUsedBefore = duckChat.wasOpenedBefore()
            val params = mapOf("was_used_before" to wasUsedBefore.toBinaryString())
            pixel.fire(DuckChatPixelName.DUCK_CHAT_OPEN_NEW_TAB_MENU, parameters = params)

            duckChat.openDuckChat()
        }
    }

    data class SelectionViewState(
        val tabItems: List<TabSwitcherItem> = emptyList(),
        val mode: Mode = Normal,
        val layoutType: LayoutType? = null,
    ) {
        val numSelectedTabs: Int = (mode as? Selection)?.selectedTabs?.size ?: 0

        val dynamicInterface = when (mode) {
            is Normal -> {
                val isThereOnlyNewTabPage = tabItems.size == 1 && tabItems.any { it is Tab && it.isNewTabPage }
                DynamicInterface(
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
                    isMoreMenuItemEnabled = !isThereOnlyNewTabPage,
                    isFabVisible = true,
                    fabType = FabType.NEW_TAB,
                    backButtonType = ARROW,
                    layoutButtonType = when (layoutType) {
                        GRID -> LayoutButtonType.LIST
                        LIST -> LayoutButtonType.GRID
                        else -> LayoutButtonType.HIDDEN
                    },
                )
            }

            is Selection -> {
                val areAllTabsSelected = numSelectedTabs == tabItems.size
                val isSomethingSelected = numSelectedTabs > 0
                val isNtpTheOnlySelectedTab = numSelectedTabs == 1 &&
                    tabItems.any { it is SelectableTab && it.isSelected && it.isNewTabPage }
                val isSelectionActionable = isSomethingSelected && !isNtpTheOnlySelectedTab
                DynamicInterface(
                    isFireButtonVisible = false,
                    isNewTabVisible = false,
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
                    isMoreMenuItemEnabled = true,
                    isFabVisible = isSomethingSelected,
                    fabType = FabType.CLOSE_TABS,
                    backButtonType = CLOSE,
                    layoutButtonType = LayoutButtonType.HIDDEN,
                )
            }
        }

        data class DynamicInterface(
            val isFireButtonVisible: Boolean,
            val isNewTabVisible: Boolean,
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
            val isMoreMenuItemEnabled: Boolean,
            val isFabVisible: Boolean,
            val fabType: FabType,
            val backButtonType: BackButtonType,
            val layoutButtonType: LayoutButtonType,
        )

        enum class FabType {
            NEW_TAB,
            CLOSE_TABS,
        }

        enum class BackButtonType {
            ARROW,
            CLOSE,
        }

        enum class LayoutButtonType {
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
}
