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

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DeleteBrowsingData
import com.duckduckgo.app.fire.ManualDataClearing
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.view.FireDialogProvider.FireDialogOrigin
import com.duckduckgo.app.global.view.FireDialogProvider.FireDialogOrigin.BROWSER
import com.duckduckgo.app.global.view.FireDialogProvider.FireDialogOrigin.DUCK_AI_CONTEXTUAL_CHAT
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_ANIMATION
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_CLEAR_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.clear.getPixelValue
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_ANIMATION
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DateProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.downloads.store.DownloadStatus
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class SingleTabFireDialogViewModel @Inject constructor(
    private val fireDataStore: FireDataStore,
    private val dataClearing: ManualDataClearing,
    private val dataClearingWideEvent: DataClearingWideEvent,
    private val pixel: Pixel,
    private val settingsDataStore: SettingsDataStore,
    private val userEventsStore: UserEventsStore,
    private val fireButtonStore: FireButtonStore,
    private val dispatcherProvider: DispatcherProvider,
    private val dateProvider: DateProvider,
    private val tabRepository: TabRepository,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    private val downloadsRepository: DownloadsRepository,
    private val duckChat: DuckChat,
) : ViewModel() {

    var shouldRestartAfterClearing: Boolean = true
        private set

    private val origin = MutableStateFlow<FireDialogOrigin?>(null)
    val viewState: StateFlow<ViewState> = origin
        .filterNotNull()
        .map { mapToViewState(it) }
        .flowOn(dispatcherProvider.io())
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewState.Loading)

    // Capacity set to 3 to handle the onDeleteAllClicked() command burst:
    // OnClearStarted -> PlayAnimation -> ClearingComplete
    private val command = Channel<Command>(3, BufferOverflow.DROP_OLDEST)

    fun commands(): Flow<Command> = command.receiveAsFlow()

    init {
        viewModelScope.launch {
            val state = viewState.filterIsInstance<ViewState.Loaded>().firstOrNull()
            onShow(state?.isSiteDataSubtitleVisible ?: false)
        }
    }

    fun setOrigin(dialogOrigin: FireDialogOrigin) {
        origin.update { dialogOrigin }
    }

    fun onCancel() {
        viewModelScope.launch {
            command.send(Command.OnCancel)
        }
    }

    fun onDeleteAllClicked() {
        viewModelScope.launch {
            command.send(Command.OnClearStarted)
            trySendDailyDeleteClicked()
            pixel.enqueueFire(FIRE_DIALOG_CLEAR_PRESSED)
            pixel.enqueueFire(AppPixelName.FIRE_DIALOG_CLEAR_PRESSED_DAILY, type = Daily())
            pixel.enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING)

            val (selectedFireAnimation, fireAnimationEnabled) = withContext(dispatcherProvider.io()) {
                settingsDataStore.selectedFireAnimation to settingsDataStore.fireAnimationEnabled
            }

            pixel.enqueueFire(
                pixel = FIRE_DIALOG_ANIMATION,
                parameters = mapOf(FIRE_ANIMATION to selectedFireAnimation.getPixelValue()),
            )

            if (fireAnimationEnabled) {
                command.send(Command.PlayAnimation)
            }

            withContext(dispatcherProvider.io()) {
                fireButtonStore.incrementFireButtonUseCount()
                userEventsStore.registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)
                val clearOptions = fireDataStore.getManualClearOptions()
                dataClearingWideEvent.start(
                    entryPoint = DataClearingWideEvent.EntryPoint.SINGLE_TAB_FIRE_DIALOG,
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

    fun onDeleteThisTabClicked() {
        viewModelScope.launch {
            shouldRestartAfterClearing = false

            pixel.enqueueFire(AppPixelName.FIRE_DIALOG_CLEAR_SINGLE_TAB_PRESSED)
            pixel.enqueueFire(AppPixelName.FIRE_DIALOG_CLEAR_SINGLE_TAB_PRESSED_DAILY, type = Daily())

            command.send(Command.OnClearStarted)

            val (selectedFireAnimation, fireAnimationEnabled) = withContext(dispatcherProvider.io()) {
                settingsDataStore.selectedFireAnimation to settingsDataStore.fireAnimationEnabled
            }

            pixel.enqueueFire(
                pixel = FIRE_DIALOG_ANIMATION,
                parameters = mapOf(FIRE_ANIMATION to selectedFireAnimation.getPixelValue()),
            )

            if (fireAnimationEnabled) {
                command.send(Command.PlayAnimation)
            }

            val originalTabId = withContext(dispatcherProvider.io()) {
                tabRepository.getSelectedTab()?.tabId
            }

            val result = withContext(dispatcherProvider.io()) {
                if (originalTabId != null) {
                    if (origin.value == DUCK_AI_CONTEXTUAL_CHAT) {
                        dataClearing.clearTabContextualChat(originalTabId)
                    } else {
                        dataClearing.clearSingleTabData(originalTabId)
                    }
                } else {
                    null
                }
            }

            when (result) {
                is ClearDataResult.FeatureNotSupported -> command.send(Command.OnSingleTabClearFeatureNotSupported)
                is ClearDataResult.Success -> {
                    if (origin.value != DUCK_AI_CONTEXTUAL_CHAT) {
                        // in case of contextual chat the origin tab is never closed, don't need this
                        waitForTabsToUpdate(originalTabId)
                    }
                    command.send(Command.OnSingleTabClearComplete)
                }
                else -> command.send(Command.OnSingleTabClearError)
            }
        }
    }

    private suspend fun onShow(isDataSubtitleVisible: Boolean) {
        command.send(Command.OnShow)
        pixel.fire(AppPixelName.FIRE_DIALOG_SHOWN)

        if (isDataSubtitleVisible) {
            withContext(dispatcherProvider.io()) {
                settingsDataStore.singleTabFireDialogShownCount++
            }
        }
    }

    private suspend fun mapToViewState(dialogOrigin: FireDialogOrigin): ViewState.Loaded {
        val isDuckAiChatsSelected =
            fireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS)
        val isDeleteBrowsingDataSupported = webViewCapabilityChecker.isSupported(DeleteBrowsingData)
        val shownCount = settingsDataStore.singleTabFireDialogShownCount
        val downloads = downloadsRepository.getDownloads()
        val selectedTabUrl = tabRepository.getSelectedTab()?.url
        val isDuckAiTab = dialogOrigin == DUCK_AI_CONTEXTUAL_CHAT ||
            selectedTabUrl?.let { duckChat.isDuckChatUrl(it.toUri()) } ?: false
        val tabCount = tabRepository.getOpenTabCount()
        return ViewState.Loaded(
            stateData = ViewState.Loaded.StateData(
                isDuckAiChatsSelected = isDuckAiChatsSelected,
                isSingleTabEnabled = isDeleteBrowsingDataSupported,
                isDuckAiTab = isDuckAiTab,
                tabCount = tabCount,
                isSiteDataSubtitleEligible = shownCount < DIALOG_WARNING_MESSAGE_SHOWN_LIMIT,
                isDownloadsSubtitleEligible = downloads.any { download -> download.downloadStatus == DownloadStatus.STARTED },
                isFirePictogramVisible = settingsDataStore.fireAnimationEnabled,
            ),
            origin = dialogOrigin,
        )
    }

    private suspend fun waitForTabsToUpdate(originalTabId: String?) {
        // wait for the tab selection to change before signaling completion
        withTimeoutOrNull(TAB_UPDATE_TIMEOUT_MS) {
            tabRepository.flowSelectedTab.firstOrNull { it?.tabId != originalTabId }
        }
        delay(500)
    }

    private suspend fun trySendDailyDeleteClicked() {
        withContext(dispatcherProvider.io()) {
            val now = dateProvider.getUtcIsoLocalDate()
            val timestamp = fireButtonStore.lastEventSendTime

            if (timestamp == null || now > timestamp) {
                fireButtonStore.storeLastFireButtonClearEventTime(now)
                pixel.enqueueFire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY)
            }
        }
    }

    sealed class ViewState {
        data object Loading : ViewState()

        data class Loaded(
            val stateData: StateData,
            val origin: FireDialogOrigin,
        ) : ViewState() {
            val isDuckAiTabInBrowser: Boolean
                get() = (stateData.isDuckAiTab && origin == BROWSER) || origin == DUCK_AI_CONTEXTUAL_CHAT

            val isDeleteThisTabButtonVisible: Boolean
                get() = (stateData.isSingleTabEnabled && origin == BROWSER) || origin == DUCK_AI_CONTEXTUAL_CHAT

            val isDeleteAllButtonVisible: Boolean
                get() = origin != DUCK_AI_CONTEXTUAL_CHAT && !(isDuckAiTabInBrowser && stateData.isSingleTabEnabled)

            val isSiteDataSubtitleVisible: Boolean
                get() = stateData.isSiteDataSubtitleEligible && !isDuckAiTabInBrowser

            val isDownloadsSubtitleVisible: Boolean
                get() = stateData.isDownloadsSubtitleEligible && !isDuckAiTabInBrowser

            data class StateData(
                val isDuckAiChatsSelected: Boolean = false,
                val isSingleTabEnabled: Boolean = false,
                val isDuckAiTab: Boolean = false,
                val tabCount: Int = 0,
                val isSiteDataSubtitleEligible: Boolean = false,
                val isDownloadsSubtitleEligible: Boolean = false,
                val isFirePictogramVisible: Boolean = true,
            )
        }
    }

    sealed class Command {
        data object PlayAnimation : Command()
        data object ClearingComplete : Command()
        data object OnShow : Command()
        data object OnCancel : Command()
        data object OnClearStarted : Command()
        data object OnSingleTabClearComplete : Command()
        data object OnSingleTabClearFeatureNotSupported : Command()
        data object OnSingleTabClearError : Command()
    }

    companion object {
        private const val DIALOG_WARNING_MESSAGE_SHOWN_LIMIT = 2
        private const val TAB_UPDATE_TIMEOUT_MS = 5000L
    }
}
