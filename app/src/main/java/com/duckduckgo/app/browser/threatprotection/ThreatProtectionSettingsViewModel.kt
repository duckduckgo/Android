/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.threatprotection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class ThreatProtectionSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val maliciousSiteProtection: MaliciousSiteProtection,
) : ViewModel() {

    data class ViewState(
        val scamProtectionUserEnabled: Boolean,
        val scamProtectionRCEnabled: Boolean,
    )

    sealed class Command {
        data object OpenThreatProtectionLearnMore : Command()
        data object OpenScamProtectionLearnMore : Command()
        data object OpenSmarterEncryptionLearnMore : Command()
    }

    private val _viewState = MutableStateFlow<ViewState?>(null)
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    val commands = _commands.receiveAsFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io()) {
            _viewState.value = ViewState(
                scamProtectionUserEnabled = settingsDataStore.maliciousSiteProtectionEnabled,
                scamProtectionRCEnabled = androidBrowserConfigFeature.enableMaliciousSiteProtection().isEnabled() &&
                    maliciousSiteProtection.isFeatureEnabled(),
            )
        }
    }

    fun onScamProtectionSettingChanged(enabled: Boolean) {
        logcat { "User changed scam protection setting, is now enabled: $enabled" }
        viewModelScope.launch(dispatcherProvider.io()) {
            settingsDataStore.maliciousSiteProtectionEnabled = enabled
            pixel.fire(
                AppPixelName.MALICIOUS_SITE_PROTECTION_SETTING_TOGGLED,
                mapOf(NEW_STATE to enabled.toString()),
            )
            _viewState.value = _viewState.value?.copy(
                scamProtectionUserEnabled = enabled,
            )
        }
    }

    fun threatProtectionLearnMoreClicked() {
        sendCommand(Command.OpenThreatProtectionLearnMore)
    }

    fun scamProtectionLearnMoreClicked() {
        sendCommand(Command.OpenScamProtectionLearnMore)
    }

    fun smarterEncryptionLearnMoreClicked() {
        sendCommand(Command.OpenSmarterEncryptionLearnMore)
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            _commands.send(newCommand)
        }
    }

    companion object {
        private const val NEW_STATE = "newState"
    }
}
