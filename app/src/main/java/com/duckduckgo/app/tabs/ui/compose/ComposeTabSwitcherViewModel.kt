/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui.compose

import android.R.attr.mode
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_GRID_VIEW_BUTTON_CLICKED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_LIST_VIEW_BUTTON_CLICKED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.model.isBlank
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherCommand.ShowUndoDeleteTabsMessage
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherItem.NewTab
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherItem.WebTab
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherViewState.Mode.Normal
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherViewState.Mode.Selection
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val DUCKDUCKGO_TITLE_SUFFIX = "at DuckDuckGo"

@ContributesViewModel(ActivityScope::class)
class ComposeTabSwitcherViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val tabRepository: TabRepository,
    private val faviconManager: FaviconManager,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val pixel: Pixel,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ComposeTabSwitcherViewState())
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<ComposeTabSwitcherCommand>(capacity = Channel.CONFLATED)
    val commands: Flow<ComposeTabSwitcherCommand> = _commands.receiveAsFlow().distinctUntilChanged()

    init {
        combine(
            tabRepository.flowTabs,
            tabRepository.flowSelectedTab,
            tabRepository.tabSwitcherData,
        ) { tabEntities, liveSelectedTab, tabSwitcherData ->
            var selectedTabIndex = 0

            val tabSwitcherItems = tabEntities.mapIndexed { index, tabEntity ->
                selectedTabIndex = if (tabEntity.tabId == liveSelectedTab?.tabId) {
                    index
                } else {
                    selectedTabIndex
                }

                if (tabEntity.url.isNullOrBlank()) {
                    NewTab(
                        isCurrentTab = tabEntity.tabId == liveSelectedTab?.tabId,
                        selectionStatus = null,
                    )
                } else {
                    val localFaviconBitmap =
                        faviconManager.loadFromDisk(tabEntity.tabId, tabEntity.url.orEmpty())

                    val tabPreviewFile = tabEntity.tabPreviewFile?.run {
                        val previewPath =
                            webViewPreviewPersister.fullPathForFile(tabEntity.tabId, this)
                        File(previewPath).takeIf { it.exists() }
                    }

                    WebTab(
                        tabEntity = tabEntity,
                        faviconBitmap = localFaviconBitmap,
                        isUnreadIndicatorVisible = !tabEntity.viewed,
                        tabPreviewFilePath = tabPreviewFile,
                        title = extractTabTitle(tabEntity),
                        isCurrentTab = tabEntity.tabId == liveSelectedTab?.tabId,
                        selectionStatus = null,
                    )
                }
            }

            _viewState.update {
                it.copy(
                    selectedTabIndex = selectedTabIndex,
                    tabs = tabSwitcherItems,
                    layoutType = tabSwitcherData.layoutType,
                )
            }
        }
            .flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    fun onNavigationIconClicked() {
        pixel.fire(AppPixelName.TAB_MANAGER_UP_BUTTON_PRESSED)

        when (_viewState.value.mode) {
            is Normal -> _commands.trySend(ComposeTabSwitcherCommand.Close)
            is Selection -> triggerNormalMode()
        }
    }

    fun onTabClicked(tabId: String) {
        // TODO add multi-select
        viewModelScope.launch {
            tabRepository.select(tabId)
            _commands.trySend(ComposeTabSwitcherCommand.Close)
            pixel.fire(AppPixelName.TAB_MANAGER_SWITCH_TABS)
        }
    }

    fun onCloseTabClicked(tabId: String) {
        viewModelScope.launch {
            pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_CLICKED)
            processTabDeletion(tabId)
        }
    }

    fun onTabSwipedAway(tabId: String) {
        viewModelScope.launch {
            pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_SWIPED)
            processTabDeletion(tabId)
        }
    }

    private suspend fun processTabDeletion(tabId: String) {
        if (_viewState.value.tabs.size == 1) {
            // mark the tab as deletable, the undo snackbar will be shown after tab switcher is closed
            tabRepository.markDeletable(tabId)
            _commands.trySend(
                ComposeTabSwitcherCommand.CloseAndShowUndoMessage(
                    listOf(tabId),
                ),
            )
        } else {
            tabRepository.markDeletable(tabId)
            _commands.trySend(ShowUndoDeleteTabsMessage(listOf(tabId)))
        }
    }

    fun onClearDataButtonClicked() {
        pixel.fire(AppPixelName.FORGET_ALL_PRESSED_TABSWITCHING)
        // TODO show dialog
    }

    fun onLayoutTypeToggled() {
        viewModelScope.launch(dispatcherProvider.io()) {
            when (_viewState.value.layoutType) {
                GRID -> {
                    pixel.fire(TAB_MANAGER_LIST_VIEW_BUTTON_CLICKED)
                    tabRepository.setTabLayoutType(LIST)
                }

                LIST -> {
                    pixel.fire(TAB_MANAGER_GRID_VIEW_BUTTON_CLICKED)
                    tabRepository.setTabLayoutType(GRID)
                }
            }
        }
    }

    fun onMenuClicked() {
        if (_viewState.value.mode is Selection) {
            pixel.fire(AppPixelName.TAB_MANAGER_SELECT_MODE_MENU_PRESSED)
        } else {
            pixel.fire(AppPixelName.TAB_MANAGER_MENU_PRESSED)
        }

        // TODO show popup menu
    }

    private fun extractTabTitle(tabEntity: TabEntity): String {
        val resolvedUrl = if (tabEntity.isBlank) AppUrl.Url.HOME else tabEntity.url ?: ""
        var title = tabEntity.title ?: Uri.parse(resolvedUrl).host ?: ""
        title = title.removeSuffix(DUCKDUCKGO_TITLE_SUFFIX)
        return title
    }

    private fun triggerNormalMode() {
        _viewState.update { it.copy(mode = Normal) }
    }

    private suspend fun deleteTabs(tabIds: List<String>) {
        tabRepository.deleteTabs(tabIds)
    }
}
