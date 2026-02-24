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

package com.duckduckgo.app.global.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.fire.ManualDataClearing
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_ANIMATION
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_CLEAR_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.clear.getPixelValue
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_ANIMATION
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DateProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.history.api.NavigationHistory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class GranularFireDialogViewModel @Inject constructor(
    tabRepository: TabRepository,
    private val fireDataStore: FireDataStore,
    private val dataClearing: ManualDataClearing,
    private val dataClearingWideEvent: DataClearingWideEvent,
    private val pixel: Pixel,
    private val settingsDataStore: SettingsDataStore,
    private val userEventsStore: UserEventsStore,
    private val fireButtonStore: FireButtonStore,
    private val dispatcherProvider: DispatcherProvider,
    private val duckChat: DuckChat,
    private val navigationHistory: NavigationHistory,
    private val dateProvider: DateProvider,
) : ViewModel() {

    data class ViewState(
        val tabCount: Int = 0,
        val siteCount: Int = 0,
        val isHistoryEnabled: Boolean = false,
        val selectedOptions: Set<FireClearOption> = emptySet(),
        val isDuckChatClearingEnabled: Boolean = false,
    ) {
        val isDeleteButtonEnabled: Boolean = selectedOptions.isNotEmpty()
        val shouldRestartAfterClearing: Boolean =
            selectedOptions.contains(FireClearOption.DATA) || selectedOptions.contains(FireClearOption.DUCKAI_CHATS)
    }

    sealed class Command {
        data object PlayAnimation : Command()
        data object ClearingComplete : Command()
        data object OnShow : Command()
        data object OnCancel : Command()
        data object OnClearStarted : Command()
    }

    private val _siteCount = MutableStateFlow(0)
    private val _isHistoryEnabled = MutableStateFlow(false)
    private val _chatClearingEnabled = MutableStateFlow(false)
    private var hasFiredDialogShownPixel: Boolean = false

    // Capacity set to 3 to handle the onDeleteClicked() command burst:
    // OnClearStarted -> PlayAnimation -> ClearingComplete
    private val command = Channel<Command>(3, BufferOverflow.DROP_OLDEST)

    val viewState: StateFlow<ViewState> = combine(
        tabRepository.flowTabs,
        fireDataStore.getManualClearOptionsFlow(),
        _siteCount,
        _isHistoryEnabled,
        _chatClearingEnabled,
    ) { tabs, selectedOptions, siteCount, isHistoryEnabled, chatClearingEnabled ->
        ViewState(
            tabCount = tabs.size,
            siteCount = siteCount,
            isHistoryEnabled = isHistoryEnabled,
            selectedOptions = selectedOptions,
            isDuckChatClearingEnabled = chatClearingEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = ViewState(),
    )

    fun commands(): Flow<Command> = command.receiveAsFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io()) {
            val isEnabled = duckChat.wasOpenedBefore()
            _chatClearingEnabled.update { isEnabled }
        }

        viewModelScope.launch(dispatcherProvider.io()) {
            val historyEnabled = navigationHistory.isHistoryUserEnabled()
            if (historyEnabled) {
                _isHistoryEnabled.update { true }
                navigationHistory.getHistory().collect { historyEntries ->
                    val uniqueDomains = historyEntries.mapNotNull { it.url.host }.toSet()
                    _siteCount.update { uniqueDomains.size }
                }
            } else {
                _isHistoryEnabled.update { false }
            }
        }
    }

    fun onOptionToggled(option: FireClearOption, isChecked: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (isChecked) {
                fireDataStore.addManualClearOption(option)
            } else {
                fireDataStore.removeManualClearOption(option)
            }
        }
    }

    fun onShow() {
        viewModelScope.launch {
            command.send(Command.OnShow)
            if (!hasFiredDialogShownPixel) {
                hasFiredDialogShownPixel = true
                pixel.fire(AppPixelName.FIRE_DIALOG_SHOWN)
            }
        }
    }

    fun onCancel() {
        viewModelScope.launch {
            command.send(Command.OnCancel)
        }
    }

    fun onDeleteClicked() {
        viewModelScope.launch {
            command.send(Command.OnClearStarted)
            trySendDailyDeleteClicked()
            pixel.enqueueFire(FIRE_DIALOG_CLEAR_PRESSED)
            pixel.enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING)
            pixel.enqueueFire(
                pixel = FIRE_DIALOG_ANIMATION,
                parameters = mapOf(FIRE_ANIMATION to settingsDataStore.selectedFireAnimation.getPixelValue()),
            )

            if (settingsDataStore.fireAnimationEnabled) {
                command.send(Command.PlayAnimation)
            }

            withContext(dispatcherProvider.io()) {
                fireButtonStore.incrementFireButtonUseCount()
                userEventsStore.registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)
                val clearOptions = fireDataStore.getManualClearOptions()
                dataClearingWideEvent.start(
                    entryPoint = DataClearingWideEvent.EntryPoint.GRANULAR_FIRE_DIALOG,
                    clearOptions = clearOptions,
                )
                try {
                    dataClearing.clearDataUsingManualFireOptions()
                    dataClearingWideEvent.finishSuccess()
                } catch (e: Exception) {
                    dataClearingWideEvent.finishFailure(e)
                    throw e
                }
            }

            command.send(Command.ClearingComplete)
        }
    }

    private fun trySendDailyDeleteClicked() {
        val now = dateProvider.getUtcIsoLocalDate()
        val timestamp = fireButtonStore.lastEventSendTime

        if (timestamp == null || now > timestamp) {
            fireButtonStore.storeLastFireButtonClearEventTime(now)
            pixel.enqueueFire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY)
        }
    }
}
