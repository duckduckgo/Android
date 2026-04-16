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

package com.duckduckgo.app.browser.navigation.bar.view

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.menu.BrowserMenuHighlight
import com.duckduckgo.app.browser.menu.BrowserViewMode
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode.Browser
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode.CustomTab
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode.DuckAI
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode.NewTab
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode.TabManager
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyAutofillButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyBookmarksButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyFireButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyMenuButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyNewTabButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyTabsButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyTabsButtonLongClicked
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_BUTTON_STATE
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ViewScope::class)
class BrowserNavigationBarViewModel @Inject constructor(
    private val pixel: Pixel,
    tabRepository: TabRepository,
    dispatcherProvider: DispatcherProvider,
    browserMenuHighlight: BrowserMenuHighlight,
) : ViewModel(), DefaultLifecycleObserver {
    private val _commands = Channel<Command>(capacity = Channel.CONFLATED)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.map { it.viewMode.toBrowserViewMode() }.distinctUntilChanged().flatMapLatest { mode ->
        combine(
            _viewState.asStateFlow(),
            tabRepository.flowTabs,
            browserMenuHighlight.shouldShowHighlightForMode(mode),
        ) { state, tabs, showHighlight ->
            state.copy(
                tabsCount = tabs.size,
                hasUnreadTabs = tabs.firstOrNull { !it.viewed } != null,
                showBrowserMenuHighlight = showHighlight,
            )
        }
    }.flowOn(dispatcherProvider.io()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ViewState())

    private fun ViewMode.toBrowserViewMode(): BrowserViewMode = when (this) {
        Browser -> BrowserViewMode.Browser
        NewTab, TabManager -> BrowserViewMode.NewTab
        CustomTab -> BrowserViewMode.CustomTab
        DuckAI -> BrowserViewMode.DuckAi
    }

    fun onFireButtonClicked() {
        pixel.fire(
            AppPixelName.BROWSER_NAV_FIRE_PRESSED.pixelName,
            mapOf(FIRE_BUTTON_STATE to _viewState.value.fireButtonHighlighted.toString()),
        )
        _commands.trySend(NotifyFireButtonClicked)
    }

    fun onTabsButtonClicked() {
        pixel.fire(AppPixelName.BROWSER_NAV_TABS_PRESSED.pixelName)
        _commands.trySend(NotifyTabsButtonClicked)
    }

    fun onTabsButtonLongClicked() {
        pixel.fire(AppPixelName.BROWSER_NAV_TABS_LONG_PRESSED.pixelName)
        _commands.trySend(NotifyTabsButtonLongClicked)
    }

    fun onMenuButtonClicked() {
        pixel.fire(AppPixelName.BROWSER_NAV_MENU_PRESSED.pixelName)
        _commands.trySend(NotifyMenuButtonClicked)
    }

    fun onNewTabButtonClicked() {
        pixel.fire(AppPixelName.BROWSER_NAV_NEW_TAB_PRESSED.pixelName)
        _commands.trySend(NotifyNewTabButtonClicked)
    }

    fun onAutofillButtonClicked() {
        pixel.fire(AppPixelName.BROWSER_NAV_PASSWORDS_PRESSED.pixelName)
        _commands.trySend(NotifyAutofillButtonClicked)
    }

    fun onBookmarksButtonClicked() {
        pixel.fire(AppPixelName.BROWSER_NAV_BOOKMARKS_PRESSED.pixelName)
        _commands.trySend(NotifyBookmarksButtonClicked)
    }

    fun setViewMode(viewMode: ViewMode) {
        when (viewMode) {
            NewTab -> {
                _viewState.update {
                    it.copy(
                        newTabButtonVisible = false,
                        autofillButtonVisible = true,
                        isBrowserMode = false,
                        viewMode = viewMode,
                    )
                }
            }

            Browser -> {
                _viewState.update {
                    it.copy(
                        newTabButtonVisible = true,
                        autofillButtonVisible = false,
                        isBrowserMode = true,
                        viewMode = viewMode,
                    )
                }
            }

            DuckAI -> {
                _viewState.update {
                    it.copy(
                        newTabButtonVisible = true,
                        autofillButtonVisible = false,
                        isBrowserMode = false,
                        viewMode = viewMode,
                    )
                }
            }

            TabManager -> {
                _viewState.update {
                    it.copy(
                        newTabButtonVisible = true,
                        autofillButtonVisible = false,
                        tabsButtonVisible = false,
                        bookmarksButtonVisible = false,
                        showShadow = false,
                        isBrowserMode = false,
                        viewMode = viewMode,
                    )
                }
            }
            CustomTab -> {
                _viewState.update {
                    it.copy(
                        isVisible = false,
                        isBrowserMode = false,
                        viewMode = viewMode,
                    )
                }
            }
        }
    }

    fun setFireButtonHighlight(highlighted: Boolean) {
        _viewState.update {
            it.copy(
                fireButtonHighlighted = highlighted,
            )
        }
    }

    sealed class Command {
        data object NotifyFireButtonClicked : Command()
        data object NotifyTabsButtonClicked : Command()
        data object NotifyTabsButtonLongClicked : Command()
        data object NotifyMenuButtonClicked : Command()
        data object NotifyNewTabButtonClicked : Command()
        data object NotifyAutofillButtonClicked : Command()
        data object NotifyBookmarksButtonClicked : Command()
    }

    data class ViewState(
        val isVisible: Boolean = true,
        val newTabButtonVisible: Boolean = true,
        val autofillButtonVisible: Boolean = false,
        val bookmarksButtonVisible: Boolean = true,
        val fireButtonVisible: Boolean = true,
        val fireButtonHighlighted: Boolean = false,
        val tabsButtonVisible: Boolean = true,
        val tabsCount: Int = 0,
        val hasUnreadTabs: Boolean = false,
        val showBrowserMenuHighlight: Boolean = false,
        val isBrowserMode: Boolean = true,
        val viewMode: ViewMode = Browser,
        val showShadow: Boolean = true,
    )
}
