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
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.DAILY
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData.UserState.EXISTING
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class TabSwitcherViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val adClickManager: AdClickManager,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
    private val statisticsDataStore: StatisticsDataStore,
) : ViewModel() {
    companion object {
        const val MAX_ANNOUNCEMENT_DISPLAY_COUNT = 3
        const val REINSTALL_VARIANT = "ru"
    }

    val tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    val activeTab = tabRepository.liveSelectedTab
    val deletableTabs: LiveData<List<TabEntity>> = tabRepository.flowDeletableTabs.asLiveData(
        context = viewModelScope.coroutineContext,
    )

    private var announcementDisplayCount: Int = 0
    private var isBannerAlreadyVisible: Boolean = false
    val isFeatureAnnouncementVisible = combine(tabRepository.tabSwitcherData, tabRepository.flowTabs) { data, tabs ->
        val isVisible =
            announcementDisplayCount < MAX_ANNOUNCEMENT_DISPLAY_COUNT &&
                !data.wasAnnouncementDismissed &&
                (data.userState == EXISTING || statisticsDataStore.variant == REINSTALL_VARIANT) &&
                (tabs.size > 1 || isBannerAlreadyVisible)
        isBannerAlreadyVisible = isVisible
        isVisible
    }
        .onStart { announcementDisplayCount = tabRepository.tabSwitcherData.first().announcementDisplayCount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val _layoutType = MutableStateFlow(LayoutType.GRID)
    val layoutType = _layoutType.asStateFlow()

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        data object Close : Command()
        data object CloseAllTabsRequest : Command()
    }

    suspend fun onNewTabRequested(fromOverflowMenu: Boolean) {
        tabRepository.add()
        command.value = Command.Close
        if (fromOverflowMenu) {
            pixel.fire(AppPixelName.TAB_MANAGER_MENU_NEW_TAB_PRESSED)
        } else {
            pixel.fire(AppPixelName.TAB_MANAGER_NEW_TAB_CLICKED)
        }
    }

    suspend fun onTabSelected(tab: TabEntity) {
        tabRepository.select(tab.tabId)
        command.value = Command.Close
        pixel.fire(AppPixelName.TAB_MANAGER_SWITCH_TABS)
    }

    suspend fun onTabDeleted(tab: TabEntity) {
        tabRepository.delete(tab)
        adClickManager.clearTabId(tab.tabId)
        webViewSessionStorage.deleteSession(tab.tabId)
    }

    suspend fun onMarkTabAsDeletable(tab: TabEntity, swipeGestureUsed: Boolean) {
        tabRepository.markDeletable(tab)
        adClickManager.clearTabId(tab.tabId)
        if (swipeGestureUsed) {
            pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_SWIPED)
        } else {
            pixel.fire(AppPixelName.TAB_MANAGER_CLOSE_TAB_CLICKED)
        }
    }

    suspend fun undoDeletableTab(tab: TabEntity) {
        tabRepository.undoDeletable(tab)
    }

    suspend fun purgeDeletableTabs() {
        tabRepository.purgeDeletableTabs()
    }

    fun onCloseAllTabsRequested() {
        command.value = Command.CloseAllTabsRequest
        pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_PRESSED)
    }

    fun onCloseAllTabsConfirmed() {
        viewModelScope.launch(dispatcherProvider.io()) {
            tabs.value?.forEach {
                onTabDeleted(it)
            }
            pixel.fire(AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED)
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

    fun onTabFeatureAnnouncementDisplayed() {
        viewModelScope.launch(dispatcherProvider.io()) {
            val data = tabRepository.tabSwitcherData.first()
            tabRepository.setAnnouncementDisplayCount(data.announcementDisplayCount + 1)
        }

        pixel.fire(AppPixelName.TAB_MANAGER_REARRANGE_BANNER_DISPLAYED)
    }

    fun onFeatureAnnouncementCloseButtonTapped() {
        dismissFeatureAnnouncementBanner()

        pixel.fire(AppPixelName.TAB_MANAGER_REARRANGE_BANNER_MANUAL_CLOSED)
    }

    fun onTabDraggingStarted() {
        if (isBannerAlreadyVisible) {
            dismissFeatureAnnouncementBanner()

            pixel.fire(AppPixelName.TAB_MANAGER_REARRANGE_BANNER_AUTODISMISSED)
        }

        viewModelScope.launch(dispatcherProvider.io()) {
            val params = mapOf("userState" to tabRepository.tabSwitcherData.first().userState.name)
            pixel.fire(AppPixelName.TAB_MANAGER_REARRANGE_TABS, params)
            pixel.fire(AppPixelName.TAB_MANAGER_REARRANGE_TABS_DAILY, parameters = params, encodedParameters = emptyMap(), DAILY)
        }
    }

    fun onLayoutTypeToggled() {
        _layoutType.value = when (_layoutType.value) {
            LayoutType.GRID -> LayoutType.LIST
            LayoutType.LIST -> LayoutType.GRID
        }
    }

    private fun dismissFeatureAnnouncementBanner() {
        viewModelScope.launch(dispatcherProvider.io()) {
            tabRepository.setWasAnnouncementDismissed(true)
        }
    }

    enum class LayoutType {
        GRID, LIST
    }
}
