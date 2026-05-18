/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.browser.ui.newtab.hatch

import android.annotation.SuppressLint
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class NewTabReturnHatchViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val dispatchers: DispatcherProvider,
    private val duckChat: DuckChat,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val ntpAfterIdleManager: NtpAfterIdleManager,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val tabTitle: String = "",
        val url: String = "",
        val tabId: String = "",
        val currentTabId: String = "",
        val shouldShow: Boolean = false,
        val isDuckChat: Boolean = false,
        val isSerp: Boolean = false,
        val tabs: Int = 0,
        val showTabsButton: Boolean = false,
    )

    sealed class Command {
        data object LaunchTabSwitcher : Command()
        data class ShowTabClosedSnackbar(val tabId: String) : Command()
    }

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands: Flow<Command> = commandChannel.receiveAsFlow()

    private val pendingClose = MutableStateFlow(false)
    private val burnTargetTabId = MutableStateFlow<String?>(null)

    init {
        // When the user burns the hatch's tab via the FireDialog, hide the hatch as soon as that
        // tab is gone from the repository. If the FireDialog is cancelled, the tab survives and the
        // hatch stays visible.
        viewModelScope.launch(dispatchers.io()) {
            combine(burnTargetTabId, tabRepository.flowTabs) { targetId, tabs ->
                targetId != null && tabs.none { it.tabId == targetId }
            }.collect { targetGone ->
                if (targetGone) {
                    pendingClose.value = true
                    burnTargetTabId.value = null
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val viewState = pendingClose.flatMapLatest { isClosed ->
        if (isClosed) {
            // Once the user closes the hatch, freeze it: stop observing upstream flows so the
            // post-deletion re-emission from flowLastAccessedTab doesn't trigger another render.
            flowOf(ViewState(shouldShow = false))
        } else {
            combine(
                tabRepository.flowLastAccessedTab,
                tabRepository.flowTabs,
                ntpAfterIdleManager.isAfterIdleReturn,
                duckChat.observeNativeInputFieldUserSettingEnabled(),
            ) { lastTab, tabs, afterIdle, nativeInputEnabled ->
                if (lastTab != null && afterIdle) {
                    val url = lastTab.url.orEmpty()
                    ViewState(
                        tabTitle = lastTab.title.orEmpty(),
                        url = url,
                        tabId = lastTab.tabId,
                        currentTabId = lastTab.tabId,
                        shouldShow = true,
                        isDuckChat = url.isNotEmpty() && duckChat.isDuckChatUrl(Uri.parse(url)),
                        isSerp = url.isNotEmpty() && duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url),
                        tabs = tabs.size,
                        showTabsButton = nativeInputEnabled,
                    )
                } else {
                    ViewState(shouldShow = false)
                }
            }
        }
    }
        .flowOn(dispatchers.io())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewState())

    fun onHatchPressed() {
        viewModelScope.launch(dispatchers.io()) {
            tabRepository.select(viewState.value.currentTabId)
        }
    }

    fun closeTab() {
        val tabId = viewState.value.currentTabId
        if (tabId.isEmpty()) return
        pendingClose.value = true
        commandChannel.trySend(Command.ShowTabClosedSnackbar(tabId))
    }

    fun onBurnTabPressed() {
        burnTargetTabId.value = viewState.value.currentTabId.takeIf { it.isNotEmpty() }
    }

    fun onUndoCloseTab(tabId: String) {
        pendingClose.value = false
    }

    fun onTabClosedSnackbarDismissed(tabId: String) {
        viewModelScope.launch(dispatchers.io()) {
            tabRepository.deleteTabs(listOf(tabId))
        }
        // pendingClose intentionally not reset: once the user commits to closing the hatch's tab,
        // the hatch should not reappear with a different last-accessed tab.
    }

    fun onTabManagerPressed() {
        commandChannel.trySend(Command.LaunchTabSwitcher)
    }
}
