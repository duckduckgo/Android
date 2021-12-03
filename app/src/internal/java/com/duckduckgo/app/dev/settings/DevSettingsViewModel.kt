/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.dev.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.useragent.UAOverride
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.dev.settings.db.DevSettingsDataStore
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

class DevSettingsViewModel @Inject constructor(
    private val devSettingsDataStore: DevSettingsDataStore,
    private val userAgentProvider: UserAgentProvider
) : ViewModel() {

    data class ViewState(
        val nextTdsEnabled: Boolean = false,
        val overrideUA: Boolean = false,
        val userAgent: String = ""
    )

    sealed class Command {
        object SendTdsIntent : Command()
        data class GoToUrl(val url: String) : Command()
        object OpenUASelector : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun start() {
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    nextTdsEnabled = devSettingsDataStore.nextTdsEnabled,
                    overrideUA = devSettingsDataStore.overrideUA,
                    userAgent = userAgentProvider.userAgent("", false)
                )
            )
        }
    }

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onNextTdsToggled(nextTds: Boolean) {
        Timber.i("User toggled next tds, is now enabled: $nextTds")
        devSettingsDataStore.nextTdsEnabled = nextTds
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(nextTdsEnabled = nextTds))
            command.send(Command.SendTdsIntent)
        }
    }

    fun onOverrideUAToggled(enabled: Boolean) {
        devSettingsDataStore.overrideUA = enabled
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(overrideUA = enabled))
        }
    }

    fun goToPrivacyTest1() {
        viewModelScope.launch { command.send(Command.GoToUrl(PRIVACY_TEST_URL_1)) }
    }

    fun goToPrivacyTest2() {
        viewModelScope.launch { command.send(Command.GoToUrl(PRIVACY_TEST_URL_2)) }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }

    fun onUserAgentSelectorClicked() {
        viewModelScope.launch { command.send(Command.OpenUASelector) }
    }

    fun onUserAgentSelected(userAgent: UAOverride) {
        devSettingsDataStore.selectedUA = userAgent
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(userAgent = userAgentProvider.userAgent("", false)))
        }
    }

    companion object {
        private const val PRIVACY_TEST_URL_1 = "https://privacy-test-pages.glitch.me/tracker-reporting/1major-via-script.html"
        private const val PRIVACY_TEST_URL_2 = "https://privacy-test-pages.glitch.me/tracker-reporting/1major-via-fetch.html"
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class SettingsViewModelFactory @Inject constructor(
    private val devSettingsDataStore: Provider<DevSettingsDataStore>,
    private val userAgentProvider: Provider<UserAgentProvider>,
    private val defaultWebBrowserCapability: Provider<DefaultBrowserDetector>,
    private val variantManager: Provider<VariantManager>,
    private val emailManager: Provider<EmailManager>,
    private val fireAnimationLoader: Provider<FireAnimationLoader>,
    private val pixel: Provider<Pixel>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(DevSettingsViewModel::class.java) -> (DevSettingsViewModel(devSettingsDataStore.get(), userAgentProvider.get()) as T)
                else -> null
            }
        }
    }
}
