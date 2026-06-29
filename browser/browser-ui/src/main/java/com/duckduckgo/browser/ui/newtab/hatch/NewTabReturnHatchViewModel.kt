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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.newtabpage.api.EscapeHatchTarget
import com.duckduckgo.newtabpage.api.EscapeHatchTargetResolver
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@OptIn(ExperimentalCoroutinesApi::class)
@ContributesViewModel(ViewScope::class)
class NewTabReturnHatchViewModel @Inject constructor(
    private val currentTabRepository: TabRepository,
    private val tabRepositoryProvider: BrowserModeDataProvider<TabRepository>,
    private val dispatchers: DispatcherProvider,
    private val duckChat: DuckChat,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val ntpAfterIdleManager: NtpAfterIdleManager,
    private val escapeHatchTargetResolver: EscapeHatchTargetResolver,
    private val pixel: Pixel,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val tabTitle: String = "",
        val url: String = "",
        val tabId: String = "",
        val currentTabId: String = "",
        val shouldShow: Boolean = false,
        val mode: BrowserMode = BrowserMode.REGULAR,
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

    // The target the hatch offers to return to, captured once when the app returns from idle. Driving
    // the hatch from this snapshot (instead of the live last-accessed flow) keeps the displayed tab
    // stable: it never switches to another tab while up, and survives the close/undo toggle.
    private val snapshotTarget = MutableStateFlow<EscapeHatchTarget?>(null)

    // The mode of the tab being closed, captured when close begins so undo/commit route to the same
    // repo even if the active snapshot changes (e.g. a fresh idle-return) while the snackbar is up.
    private var pendingCloseMode: BrowserMode = BrowserMode.REGULAR

    init {
        // Capture the target once per idle-return; reset on a fresh return so the hatch
        // can re-appear for the next one.
        viewModelScope.launch(dispatchers.io()) {
            ntpAfterIdleManager.isAfterIdleReturn.collect { afterIdle ->
                if (afterIdle) {
                    snapshotTarget.value = escapeHatchTargetResolver.resolve()
                    pendingClose.value = false
                } else {
                    snapshotTarget.value = null
                }
            }
        }
    }

    // Driven by the captured [snapshotTarget] (not the live last-accessed flow) so the displayed tab
    // is stable across the close/undo toggle. For a Regular target, flowTabs both supplies the live
    // tabs count and gates visibility: the hatch hides as soon as the snapshot tab leaves the
    // repository (burned, closed, or purged), so every live ViewModel instance stays in sync without
    // per-instance burn tracking. For a Fire target, visibility tracks the fire repo's flowTabs,
    // while the activity-mode repo supplies the tabs count shown in the tab button.
    val viewState = snapshotTarget.flatMapLatest { target ->
        if (target == null) {
            combine(currentTabRepository.flowTabs, duckChat.observeNativeInputFieldUserSettingEnabled()) { tabs, nativeInputEnabled ->
                ViewState(shouldShow = false, tabs = tabs.size, showTabsButton = nativeInputEnabled)
            }
        } else {
            val isFireTarget = target.mode == BrowserMode.FIRE
            combine(
                pendingClose,
                tabRepositoryProvider.forMode(target.mode).flowTabs,
                currentTabRepository.flowTabs,
                duckChat.observeNativeInputFieldUserSettingEnabled(),
                ntpAfterIdleManager.returnToLastTabEnabled,
            ) { closed, targetTabs, activityTabs, nativeInputEnabled, returnToLastTabEnabled ->
                val tab = targetTabs.firstOrNull { it.tabId == target.tabId }
                if (!closed && tab != null && returnToLastTabEnabled) {
                    val url = if (isFireTarget) "" else tab.url.orEmpty()
                    ViewState(
                        tabTitle = if (isFireTarget) "" else tab.title.orEmpty(),
                        url = url,
                        tabId = tab.tabId,
                        currentTabId = tab.tabId,
                        shouldShow = true,
                        mode = target.mode,
                        isDuckChat = url.isNotEmpty() && duckChat.isDuckChatUrl(Uri.parse(url)),
                        isSerp = url.isNotEmpty() && duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url),
                        tabs = activityTabs.size,
                        showTabsButton = nativeInputEnabled,
                    )
                } else {
                    ViewState(shouldShow = false, tabs = activityTabs.size, showTabsButton = nativeInputEnabled)
                }
            }
        }
    }
        .flowOn(dispatchers.io())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewState())

    private val targetRepository: TabRepository
        get() = tabRepositoryProvider.forMode(pendingCloseMode)

    fun onHatchPressed() {
        pixel.fire(NewTabReturnHatchPixelName.OPTION_SELECTED_RETURN_TAB, type = Count)
        pixel.fire(NewTabReturnHatchPixelName.OPTION_SELECTED_RETURN_TAB_DAILY, type = Daily())
    }

    fun closeTab() {
        pixel.fire(NewTabReturnHatchPixelName.OPTION_SELECTED_CLOSE_TAB, type = Count)
        pixel.fire(NewTabReturnHatchPixelName.OPTION_SELECTED_CLOSE_TAB_DAILY, type = Daily())
        val tabId = viewState.value.currentTabId
        if (tabId.isEmpty()) return
        pendingCloseMode = snapshotTarget.value?.mode ?: BrowserMode.REGULAR
        val repo = targetRepository
        // Mark the tab deletable now so it disappears from the tab list/switcher immediately
        // (recoverable via undo); the actual delete is committed when the snackbar is dismissed.
        viewModelScope.launch(dispatchers.io()) {
            repo.markDeletable(listOf(tabId))
        }
        pendingClose.value = true
        commandChannel.trySend(Command.ShowTabClosedSnackbar(tabId))
    }

    fun onBurnTabPressed() {
        pixel.fire(NewTabReturnHatchPixelName.OPTION_SELECTED_BURN_TAB, type = Count)
        pixel.fire(NewTabReturnHatchPixelName.OPTION_SELECTED_BURN_TAB_DAILY, type = Daily())
    }

    fun onUndoCloseTab(tabId: String) {
        // Restore the tab that was marked deletable on close, and re-show the hatch with the same
        // snapshot (no recompute, so it doesn't jump to a different tab).
        val repo = targetRepository
        viewModelScope.launch(dispatchers.io()) {
            repo.undoDeletable(listOf(tabId))
        }
        pendingClose.value = false
    }

    fun onTabClosedSnackbarDismissed(tabId: String) {
        // The tab was already marked deletable on close; commit the deletion now.
        val repo = targetRepository
        viewModelScope.launch(dispatchers.io()) {
            repo.purgeDeletableTabs()
        }
        // pendingClose intentionally not reset: once the user commits to closing the hatch's tab,
        // the hatch should not reappear until a fresh idle-return.
    }

    fun onTabManagerPressed() {
        pixel.fire(NewTabReturnHatchPixelName.OPTION_SELECTED_TAB_SWITCHER, type = Count)
        pixel.fire(NewTabReturnHatchPixelName.OPTION_SELECTED_TAB_SWITCHER_DAILY, type = Daily())
        ntpAfterIdleManager.onTabSwitcherSelected()
        commandChannel.trySend(Command.LaunchTabSwitcher)
    }

    fun onAfterInactivityPressed() {
        pixel.fire(NewTabReturnHatchPixelName.OPTION_SELECTED_AFTER_INACTIVITY, type = Count)
        pixel.fire(NewTabReturnHatchPixelName.OPTION_SELECTED_AFTER_INACTIVITY_DAILY, type = Daily())
    }

    fun onDontShowThisPressed() {
        pixel.fire(NewTabReturnHatchPixelName.HIDDEN_FROM_MENU, type = Count)
        pixel.fire(NewTabReturnHatchPixelName.HIDDEN_FROM_MENU_DAILY, type = Daily())
        // Disable the hatch for future idle returns.
        viewModelScope.launch(dispatchers.io()) {
            ntpAfterIdleManager.setReturnToLastTabEnabled(false)
        }
    }
}
