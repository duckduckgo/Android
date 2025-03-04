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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.BookmarkTabsRequest
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShareLink
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShareLinks
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShowBookmarkToast
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val layoutType = tabRepository.tabSwitcherData
        .map { it.layoutType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val _selectionViewState = MutableStateFlow(SelectionViewState())
    val selectionViewState = combine(
        _selectionViewState,
        tabRepository.flowSelectedTab,
    ) { viewState, activeTab ->
        val fabType = if (viewState.mode is Selection && viewState.mode.selectedTabs.isNotEmpty()) {
            SelectionViewState.FabType.CLOSE_TABS
        } else {
            SelectionViewState.FabType.NEW_TAB
        }

        viewState.copy(
            activeTab = activeTab,
            fabType = fabType,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SelectionViewState())

    val tabSwitcherItems: LiveData<List<TabSwitcherItem>> = tabRepository.flowTabs.combine(_selectionViewState) { tabEntities, viewState ->
        tabEntities.map {
            TabSwitcherItem.Tab(tabEntity = it, isSelected = viewState.mode is Selection && it.tabId in viewState.mode.selectedTabs)
        }
    }.asLiveData()

    sealed class Command {
        data object Close : Command()
        data class CloseTabsRequest(val tabIds: List<String>) : Command()
        data object CloseAllTabsRequest : Command()
        data class ShareLink(val link: String, val title: String) : Command()
        data class ShareLinks(val links: List<String>) : Command()
        data class BookmarkTabsRequest(val tabIds: List<String>) : Command()
        data class ShowBookmarkToast(val numBookmarks: Int) : Command()
    }

    suspend fun onNewTabRequested(fromOverflowMenu: Boolean) {
        if (swipingTabsFeature.isEnabled) {
            val tabItemList = tabSwitcherItems.value?.filterIsInstance<TabSwitcherItem.Tab>()
            val emptyTabItem = tabItemList?.firstOrNull { tabItem -> tabItem.tabEntity.url.isNullOrBlank() }
            val emptyTabId = emptyTabItem?.tabEntity?.tabId

            if (emptyTabId != null) {
                tabRepository.select(tabId = emptyTabId)
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

    suspend fun onTabSelected(tab: TabEntity) {
        if (tabManagerFeatureFlags.multiSelection().isEnabled() && _selectionViewState.value.mode is Selection) {
            _selectionViewState.update {
                val selectionMode = it.mode as Selection
                if (tab.tabId in selectionMode.selectedTabs) {
                    it.copy(mode = Selection(selectedTabs = selectionMode.selectedTabs - tab.tabId))
                } else {
                    it.copy(mode = Selection(selectedTabs = selectionMode.selectedTabs + tab.tabId))
                }
            }
        } else {
            tabRepository.select(tab.tabId)
            command.value = Command.Close
            pixel.fire(AppPixelName.TAB_MANAGER_SWITCH_TABS)
        }
    }

    suspend fun onTabDeleted(tabId: String) {
        tabRepository.getTab(tabId)?.let { tab ->
            tabRepository.delete(tab)
            adClickManager.clearTabId(tabId)
            webViewSessionStorage.deleteSession(tabId)
        }
    }

    suspend fun onMarkTabAsDeletable(tab: TabEntity, swipeGestureUsed: Boolean) {
        tabRepository.markDeletable(tab)
        if (swipeGestureUsed) {
            pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_SWIPED)
        } else {
            pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_CLICKED)
        }

        (_selectionViewState.value.mode as? Selection)?.let { selectionMode ->
            if (tab.tabId in selectionMode.selectedTabs) {
                _selectionViewState.update {
                    it.copy(mode = Selection(selectedTabs = selectionMode.selectedTabs - tab.tabId))
                }
            }
        }
    }

    suspend fun undoDeletableTab(tab: TabEntity) {
        tabRepository.undoDeletable(tab)

        (_selectionViewState.value.mode as? Selection)?.let { selectionMode ->
            _selectionViewState.update {
                it.copy(mode = Selection(selectedTabs = selectionMode.selectedTabs + tab.tabId))
            }
        }
    }

    suspend fun purgeDeletableTabs() {
        tabRepository.getDeletableTabIds().forEach {
            adClickManager.clearTabId(it)
        }
        tabRepository.purgeDeletableTabs()
    }

    fun onCloseAllTabsRequested() {
        command.value = Command.CloseAllTabsRequest
        pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_PRESSED)
    }

    fun onSelectAllTabs() {
        _selectionViewState.update { it.copy(mode = Selection(selectedTabs = tabSwitcherItems.value?.map { tab -> tab.id }.orEmpty())) }
    }

    fun onDeselectAllTabs() {
        _selectionViewState.update { it.copy(mode = SelectionViewState.Mode.Selection(emptyList())) }
    }

    fun onShareSelectedTabs() {
        when (val mode = selectionViewState.value.mode) {
            is Selection -> {
                if (mode.selectedTabs.size == 1) {
                    val entity = (tabSwitcherItems.value?.firstOrNull { it.id == mode.selectedTabs.first() } as? TabSwitcherItem.Tab)?.tabEntity
                    command.value = ShareLink(
                        link = entity?.url.orEmpty(),
                        title = entity?.title.orEmpty(),
                    )
                } else if (mode.selectedTabs.size > 1) {
                    val links = tabSwitcherItems.value
                        ?.filter { it.id in mode.selectedTabs }
                        ?.mapNotNull { (it as? TabSwitcherItem.Tab)?.tabEntity?.url }
                    command.value = ShareLinks(links.orEmpty())
                }
            }
            Normal -> {
                val entity = activeTab.value
                command.value = ShareLink(
                    link = entity?.url.orEmpty(),
                    title = entity?.title.orEmpty(),
                )
            }
        }
    }

    fun onBookmarkSelectedTabs() {
        when (val mode = selectionViewState.value.mode) {
            is Normal -> {
                activeTab.value?.tabId?.let { tabId ->
                    command.value = BookmarkTabsRequest(listOf(tabId))
                }
            }
            is Selection -> {
                command.value = BookmarkTabsRequest(mode.selectedTabs)
            }
        }
    }

    fun onBookmarkAllTabs() {
        tabSwitcherItems.value?.map { it.id }?.let { tabIds ->
            command.value = BookmarkTabsRequest(tabIds)
        }
    }

    fun onSelectionModeRequested() {
        _selectionViewState.update { it.copy(mode = Selection(emptyList())) }
    }

    fun onCloseSelectedTabs() {
        (selectionViewState.value.mode as? Selection)?.selectedTabs?.let { selectedTabs ->
            val allTabsCount = tabSwitcherItems.value?.size ?: 0
            command.value = if (allTabsCount == selectedTabs.size) {
                Command.CloseAllTabsRequest
            } else {
                Command.CloseTabsRequest(selectedTabs)
            }
        }
    }

    fun onCloseOtherTabs() {
        (selectionViewState.value.mode as? Selection)?.selectedTabs?.let { selectedTabs ->
            val otherTabsIds = (tabSwitcherItems.value?.map { it.id }.orEmpty()) - selectedTabs.toSet()
            if (otherTabsIds.isNotEmpty()) {
                command.value = Command.CloseTabsRequest(otherTabsIds)
            }
        }
    }

    fun onCloseTabsConfirmed(tabIds: List<String>) {
        val allTabsCount = tabSwitcherItems.value?.size ?: 0

        viewModelScope.launch(dispatcherProvider.io()) {
            tabIds.forEach {
                onTabDeleted(it)
            }

            if (allTabsCount == tabIds.size) {
                // Make sure all exemptions are removed as all tabs are deleted.
                adClickManager.clearAll()
                pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED)

                // Close the tab switcher when there are no tabs
                command.value = Command.Close
            } else {
                // Remove the deleted tab from the selection
                (_selectionViewState.value.mode as? Selection)?.let { selectionMode ->
                    _selectionViewState.update {
                        it.copy(mode = Selection(selectedTabs = selectionMode.selectedTabs - tabIds.toSet()))
                    }
                }
            }
        }
    }

    fun onBookmarkTabsConfirmed(tabIds: List<String>) {
        viewModelScope.launch {
            val numBookmarkedTabs = bookmarkTabs(tabIds)
            command.value = ShowBookmarkToast(numBookmarkedTabs)
        }
    }

    private suspend fun bookmarkTabs(tabIds: List<String>): Int {
        val results = tabIds.map { tabId ->
            viewModelScope.async {
                saveSiteBookmark(tabId)
            }
        }
        return results.awaitAll().count { it != null }
    }

    fun onCloseAllTabsConfirmed() {
        onCloseTabsConfirmed(tabSwitcherItems.value?.map { it.id }.orEmpty())
    }

    fun onEmptyAreaClicked() {
        if (tabManagerFeatureFlags.multiSelection().isEnabled() && _selectionViewState.value.mode is Selection) {
            triggerNormalMode()
        }
    }

    private fun triggerNormalMode() {
        _selectionViewState.update { it.copy(mode = Normal) }
    }

    fun onUpButtonPressed() {
        pixel.fire(AppPixelName.TAB_MANAGER_UP_BUTTON_PRESSED)
    }

    fun onBackButtonPressed() {
        pixel.fire(AppPixelName.TAB_MANAGER_BACK_BUTTON_PRESSED)
    }

    fun onMenuOpened() {
        pixel.fire(AppPixelName.TAB_MANAGER_MENU_PRESSED)
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
            val newLayoutType = when (layoutType.value) {
                GRID -> {
                    pixel.fire(AppPixelName.TAB_MANAGER_LIST_VIEW_BUTTON_CLICKED)
                    LIST
                }
                LIST -> {
                    pixel.fire(AppPixelName.TAB_MANAGER_GRID_VIEW_BUTTON_CLICKED)
                    GRID
                }
                else -> null
            }
            newLayoutType?.let { tabRepository.setTabLayoutType(it) }
        }
    }

    fun onFabClicked() {
        when (selectionViewState.value.fabType) {
            SelectionViewState.FabType.NEW_TAB -> {
                viewModelScope.launch {
                    onNewTabRequested(fromOverflowMenu = false)
                }
            }
            SelectionViewState.FabType.CLOSE_TABS -> {
                onCloseSelectedTabs()
            }
        }
    }

    private suspend fun saveSiteBookmark(tabId: String) = withContext(dispatcherProvider.io()) {
        var bookmark: Bookmark? = null
        (tabSwitcherItems.value?.firstOrNull { it.id == tabId } as? TabSwitcherItem.Tab)?.let { tab ->
            tab.tabEntity.url?.let { url ->
                if (url.isNotBlank()) {
                    // Only bookmark new sites
                    if (savedSitesRepository.getBookmark(url) == null) {
                        faviconManager.persistCachedFavicon(tabId, url)
                        bookmark = savedSitesRepository.insertBookmark(url, tab.tabEntity.title.orEmpty())
                    }
                }
            }
        }
        return@withContext bookmark
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
        val activeTab: TabEntity? = null,
        val fabType: FabType = FabType.NEW_TAB,
        val mode: Mode = Normal,
    ) {
        enum class FabType {
            NEW_TAB,
            CLOSE_TABS,
        }

        sealed interface Mode {
            data object Normal : Mode
            data class Selection(
                val selectedTabs: List<String> = emptyList(),
            ) : Mode
        }
    }
}
