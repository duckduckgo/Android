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
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode.Browser
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode.CustomTab
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode.NewTab
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyAutofillButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyBookmarksButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyFireButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyMenuButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyNewTabButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyTabsButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyTabsButtonLongClicked
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ViewScope::class)
class BrowserNavigationBarViewModel @Inject constructor(
    private val visualDesignExperimentDataStore: VisualDesignExperimentDataStore,
    private val tabRepository: TabRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {
    private val _commands = Channel<Command>(capacity = Channel.CONFLATED)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = combine(
        _viewState.asStateFlow(),
        tabRepository.flowTabs,
        visualDesignExperimentDataStore.navigationBarState,
    ) { state, tabs, navigationBarState ->
        state.copy(
            isVisible = navigationBarState.isEnabled,
            tabsCount = tabs.size,
            shouldUpdateTabsCount = tabs.size != state.tabsCount && tabs.isNotEmpty(),
        )
    }.flowOn(dispatcherProvider.io()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ViewState())

    fun onFireButtonClicked() {
        _commands.trySend(NotifyFireButtonClicked)
    }

    fun onTabsButtonClicked() {
        _commands.trySend(NotifyTabsButtonClicked)
    }

    fun onTabsButtonLongClicked() {
        _commands.trySend(NotifyTabsButtonLongClicked)
    }

    fun onMenuButtonClicked() {
        _commands.trySend(NotifyMenuButtonClicked)
    }

    fun onNewTabButtonClicked() {
        _commands.trySend(NotifyNewTabButtonClicked)
    }

    fun onAutofillButtonClicked() {
        _commands.trySend(NotifyAutofillButtonClicked)
    }

    fun onBookmarksButtonClicked() {
        _commands.trySend(NotifyBookmarksButtonClicked)
    }

    fun setViewMode(viewMode: ViewMode) {
        when (viewMode) {
            NewTab -> {
                _viewState.update {
                    it.copy(
                        newTabButtonVisible = false,
                        autofillButtonVisible = true,
                        fireButtonVisible = true,
                        tabsButtonVisible = true,
                    )
                }
            }

            Browser -> {
                _viewState.update {
                    it.copy(
                        newTabButtonVisible = true,
                        autofillButtonVisible = false,
                        fireButtonVisible = true,
                        tabsButtonVisible = true,
                    )
                }
            }

            CustomTab -> {
                _viewState.update {
                    it.copy(
                        newTabButtonVisible = false,
                        autofillButtonVisible = false,
                        fireButtonVisible = false,
                        tabsButtonVisible = false,
                    )
                }
            }
        }
    }

    sealed class Command {
        data object NotifyFireButtonClicked : Command()
        data object NotifyTabsButtonClicked : Command()
        data object NotifyTabsButtonLongClicked : Command()
        data object NotifyMenuButtonClicked : Command()
        data object NotifyBackButtonClicked : Command()
        data object NotifyBackButtonLongClicked : Command()
        data object NotifyForwardButtonClicked : Command()
        data object NotifyNewTabButtonClicked : Command()
        data object NotifyAutofillButtonClicked : Command()
        data object NotifyBookmarksButtonClicked : Command()
    }

    data class ViewState(
        val isVisible: Boolean = false,
        val newTabButtonVisible: Boolean = false,
        val autofillButtonVisible: Boolean = true,
        val bookmarksButtonVisible: Boolean = true,
        val fireButtonVisible: Boolean = true,
        val tabsButtonVisible: Boolean = true,
        val tabsCount: Int = 0,
        val shouldUpdateTabsCount: Boolean = false,
    )
}
