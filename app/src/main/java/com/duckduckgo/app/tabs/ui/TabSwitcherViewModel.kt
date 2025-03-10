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
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.SwipingTabsFeatureProvider
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.FabType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Normal
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Selection
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.common.utils.extensions.toBinaryString
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.DuckChatPixelName
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {

    val activeTab = tabRepository.liveSelectedTab
    val deletableTabs: LiveData<List<TabEntity>> = tabRepository.flowDeletableTabs.asLiveData(
        context = viewModelScope.coroutineContext,
    )

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val _selectionViewState = MutableStateFlow(SelectionViewState())
    val selectionViewState = combine(
        _selectionViewState,
        tabRepository.flowTabs,
        tabRepository.flowSelectedTab,
    ) { viewState, tabs, selectedTab ->
        viewState.copy(
            items = tabs.map { TabSwitcherItem.Tab(it, false) },
            selectedTab = selectedTab,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SelectionViewState())

    val tabSwitcherItems: LiveData<List<TabSwitcherItem>> = if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
        tabRepository.flowTabs.combine(_selectionViewState) { tabEntities, viewState ->
            tabEntities.map {
                TabSwitcherItem.Tab(it, viewState.mode is Selection && it.tabId in viewState.mode.selectedTabs)
            }
        }.asLiveData()
    } else {
        tabRepository.flowTabs
            .debounce(100.milliseconds)
            .conflate()
            .combine(_selectionViewState) { tabEntities, viewState ->
                tabEntities.map {
                    TabSwitcherItem.Tab(it, viewState.mode is SelectionViewState.Mode.Selection && it.tabId in viewState.mode.selectedTabs)
                }
            }.asLiveData()
    }

    val layoutType = tabRepository.tabSwitcherData
        .map { it.layoutType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    sealed class Command {
        data object Close : Command()
        data object CloseAllTabsRequest : Command()
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
                    it.copy(mode = Selection(selectionMode.selectedTabs - tab.tabId))
                } else {
                    it.copy(mode = Selection(selectionMode.selectedTabs + tab.tabId))
                }
            }
        } else {
            tabRepository.select(tab.tabId)
            command.value = Command.Close
            pixel.fire(AppPixelName.TAB_MANAGER_SWITCH_TABS)
        }
    }

    suspend fun onTabDeleted(tab: TabEntity) {
        tabRepository.delete(tab)
        adClickManager.clearTabId(tab.tabId)
        webViewSessionStorage.deleteSession(tab.tabId)
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
                    it.copy(mode = Selection(selectionMode.selectedTabs - tab.tabId))
                }
            }
        }
    }

    suspend fun undoDeletableTab(tab: TabEntity) {
        tabRepository.undoDeletable(tab)

        (_selectionViewState.value.mode as? Selection)?.let { selectionMode ->
            _selectionViewState.update {
                it.copy(mode = Selection(selectionMode.selectedTabs + tab.tabId))
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
        _selectionViewState.update { it.copy(mode = Selection(tabSwitcherItems.value?.map { it.id } ?: emptyList())) }
    }

    fun onDeselectAllTabs() {
        _selectionViewState.update { it.copy(mode = Selection(emptyList())) }
    }

    fun onShareSelectedTabs() {
    }

    fun onBookmarkSelectedTabs() {
    }

    fun onSelectionModeRequested() {
        _selectionViewState.update { it.copy(mode = Selection(emptyList())) }
    }

    fun onCloseSelectedTabs() {
    }

    fun onCloseOtherTabs() {
    }

    fun onCloseAllTabsConfirmed() {
        viewModelScope.launch(dispatcherProvider.io()) {
            tabSwitcherItems.value?.forEach { tabSwitcherItem ->
                when (tabSwitcherItem) {
                    is TabSwitcherItem.Tab -> onTabDeleted(tabSwitcherItem.tabEntity)
                }
            }
            // Make sure all exemptions are removed as all tabs are deleted.
            adClickManager.clearAll()
            pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED)

            // Trigger a normal mode when there are no tabs
            _selectionViewState.update { it.copy(mode = SelectionViewState.Mode.Normal) }
        }
    }

    fun onEmptyAreaClicked() {
        if (tabManagerFeatureFlags.multiSelection().isEnabled() && _selectionViewState.value.mode is Selection) {
            _selectionViewState.update { it.copy(mode = SelectionViewState.Mode.Normal) }
        }
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
            FabType.NEW_TAB -> {
                viewModelScope.launch {
                    onNewTabRequested(fromOverflowMenu = false)
                }
            }
            FabType.CLOSE_TABS -> {
                onCloseSelectedTabs()
            }
        }
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
        val items: List<TabSwitcherItem> = emptyList(),
        val selectedTab: TabEntity? = null,
        val mode: Mode = Mode.Normal,
    ) {
        val dynamicInterface: DynamicInterface
            get() = when (mode) {
                is Normal -> {
                    val isThereNotJustNewTabPage = items.size != 1 || (items.first() as? TabSwitcherItem.Tab)?.tabEntity?.url != null
                    DynamicInterface(
                        isLayoutTypeButtonVisible = true,
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
                        isCloseAllTabsVisible = true,
                        isMoreMenuItemEnabled = isThereNotJustNewTabPage,
                        isFabVisible = isThereNotJustNewTabPage,
                        fabType = FabType.NEW_TAB,
                    )
                }
                is Mode.Selection -> {
                    val areNoTabsSelected = mode.selectedTabs.isNotEmpty()
                    val areAllTabsSelected = mode.selectedTabs.size == items.size
                    DynamicInterface(
                        isLayoutTypeButtonVisible = false,
                        isFireButtonVisible = false,
                        isNewTabVisible = false,
                        isSelectAllVisible = !areAllTabsSelected,
                        isDeselectAllVisible = areAllTabsSelected,
                        isSelectionActionsDividerVisible = areNoTabsSelected,
                        isShareSelectedLinksVisible = areNoTabsSelected,
                        isBookmarkSelectedTabsVisible = areNoTabsSelected,
                        isSelectTabsDividerVisible = areNoTabsSelected,
                        isSelectTabsVisible = false,
                        isCloseSelectedTabsVisible = areNoTabsSelected,
                        isCloseOtherTabsVisible = areNoTabsSelected,
                        isCloseAllTabsVisible = false,
                        isMoreMenuItemEnabled = true,
                        isFabVisible = areNoTabsSelected,
                        fabType = FabType.CLOSE_TABS,
                    )
                }
            }

        data class DynamicInterface(
            val isLayoutTypeButtonVisible: Boolean,
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
            val isCloseAllTabsVisible: Boolean,
            val isMoreMenuItemEnabled: Boolean,
            val isFabVisible: Boolean,
            val fabType: FabType,
        )

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
