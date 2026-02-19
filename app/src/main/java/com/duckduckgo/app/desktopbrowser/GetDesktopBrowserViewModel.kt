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

package com.duckduckgo.app.desktopbrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.app.desktopbrowser.GetDesktopBrowserActivityParams.Source
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class GetDesktopBrowserViewModel @AssistedInject constructor(
    @Assisted private val params: GetDesktopBrowserActivityParams,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
    private val clipboardInteractor: ClipboardInteractor,
) : ViewModel() {

    private val _viewState = MutableStateFlow(
        ViewState(showNoThanksButton = params.source == Source.COMPLETE_SETUP),
    )
    private val _command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    val commands: Flow<Command> = _command.receiveAsFlow()
    val viewState: Flow<ViewState> = _viewState.asStateFlow()

    fun onShareDownloadLinkClicked() {
        viewModelScope.launch {
            _command.send(
                Command.ShareDownloadLink(
                    url = DESKTOP_BROWSER_URL,
                ),
            )
        }
    }

    fun onNoThanksClicked() {
        viewModelScope.launch {
            settingsDataStore.getDesktopBrowserSettingDismissed = true
            _command.send(Command.Dismissed)
        }
    }

    fun onBackPressed() {
        viewModelScope.launch {
            _command.send(Command.Close)
        }
    }

    fun onLinkClicked() {
        viewModelScope.launch(dispatchers.io()) {
            if (!clipboardInteractor.copyToClipboard(DESKTOP_BROWSER_URL, isSensitive = false)) {
                _command.send(Command.ShowCopiedNotification)
            }
        }
    }

    data class ViewState(
        val showNoThanksButton: Boolean = false,
    )

    sealed class Command {
        object Close : Command()
        object Dismissed : Command()

        object ShowCopiedNotification : Command()
        data class ShareDownloadLink(val url: String) : Command()
    }

    @AssistedFactory
    interface Factory {
        fun create(params: GetDesktopBrowserActivityParams): GetDesktopBrowserViewModel
    }

    companion object {
        private const val DESKTOP_BROWSER_URL = "https://duckduckgo.com/browser?origin=funnel_browser_settings_android"
    }
}
