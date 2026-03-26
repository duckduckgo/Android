/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource
import com.duckduckgo.autofill.impl.asString
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.importing.capability.ImportGooglePasswordsCapabilityChecker
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISPLAYED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SETTINGS_OPENED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SYNC_DESKTOP_PASSWORDS_CTA_BUTTON
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.autofill.impl.ui.settings.AutofillSettingsViewModel.Command.ImportPasswordsFromGoogle
import com.duckduckgo.autofill.impl.ui.settings.AutofillSettingsViewModel.Command.NavigatePasswordList
import com.duckduckgo.autofill.impl.ui.settings.AutofillSettingsViewModel.Command.NavigateToHowToSyncWithDesktop
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toBinaryString
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AutofillSettingsViewModel @Inject constructor(
    private val autofillStore: InternalAutofillStore,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val neverSavedSiteRepository: NeverSavedSiteRepository,
    private val autofillFeature: AutofillFeature,
    private val importGooglePasswordsCapabilityChecker: ImportGooglePasswordsCapabilityChecker,
    private val deviceAuthenticator: DeviceAuthenticator,
) : ViewModel() {

    data class ViewState(
        val autofillUnsupported: Boolean = false,
        val autofillDisabled: Boolean = false,
        val autofillEnabled: Boolean = true,
        val showAutofillEnabledToggle: Boolean = true,
        val loginsCount: Int = 0,
        val canImportFromGooglePasswords: Boolean = false,
        val canResetExcludedSites: Boolean = false,
    )

    sealed class Command {
        data object NavigatePasswordList : Command()
        data object ImportPasswordsFromGoogle : Command()
        data object NavigateToHowToSyncWithDesktop : Command()
        data object AskToConfirmResetExcludedSites : Command()
    }

    private val _commands = Channel<Command>(capacity = Channel.CONFLATED)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    // we only want to send this once for this 'session' of being in the management screen
    private var importGooglePasswordButtonShownPixelSent = false

    private var launchSource: AutofillScreenLaunchSource? = null

    private val _viewState = MutableStateFlow(ViewState())
    private val viewState: Flow<ViewState> = _viewState.onStart {
        _viewState.value = ViewState(
            autofillUnsupported = autofillStore.autofillAvailable().not(),
            autofillDisabled = deviceAuthenticator.isAuthenticationRequiredForAutofill() && !deviceAuthenticator.hasValidDeviceAuthentication(),
            autofillEnabled = autofillStore.autofillEnabled,
        )
        onViewStateFlowStart()
    }

    fun viewState(source: AutofillScreenLaunchSource): Flow<ViewState> {
        launchSource = source
        return viewState
    }

    fun sendLaunchPixel(autofillScreenLaunchSource: AutofillScreenLaunchSource) {
        viewModelScope.launch {
            val hasCredentialsSaved = (autofillStore.getCredentialCount().firstOrNull() ?: 0) > 0
            pixel.fire(
                AUTOFILL_SETTINGS_OPENED,
                parameters = mapOf(
                    "source" to autofillScreenLaunchSource.asString(),
                    "has_credentials_saved" to hasCredentialsSaved.toBinaryString(),
                ),
            )
        }
    }

    private fun onViewStateFlowStart() {
        viewModelScope.launch(dispatchers.io()) {
            autofillStore.getCredentialCount().collect { count ->
                _viewState.value = _viewState.value.copy(loginsCount = count)
            }
        }

        viewModelScope.launch(dispatchers.io()) {
            neverSavedSiteRepository.neverSaveListCount().map { count -> count > 0 }
                .distinctUntilChanged().collect { canResetExcludedSites ->
                    _viewState.value =
                        _viewState.value.copy(canResetExcludedSites = canResetExcludedSites)
                }
        }

        viewModelScope.launch(dispatchers.io()) {
            val canImport = kotlin.runCatching {
                val gpmImport = autofillFeature.self().isEnabled() && autofillFeature.canImportFromGooglePasswordManager().isEnabled()
                val webViewSupportsImportingPasswords = importGooglePasswordsCapabilityChecker.webViewCapableOfImporting()
                return@runCatching gpmImport && webViewSupportsImportingPasswords
            }.getOrDefault(false)
            _viewState.value = _viewState.value.copy(canImportFromGooglePasswords = canImport)
            if (canImport && !importGooglePasswordButtonShownPixelSent) {
                importGooglePasswordButtonShownPixelSent = true
                pixel.fire(
                    pixel = AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_SHOWN,
                    parameters = mapOf("source" to AutofillImportLaunchSource.AutofillSettings.value),
                )
            }
        }
    }

    fun checkDeviceRequirements() {
        viewModelScope.launch(dispatchers.io()) {
            _viewState.value = _viewState.value.copy(
                autofillUnsupported = autofillStore.autofillAvailable().not(),
                autofillDisabled = deviceAuthenticator.isAuthenticationRequiredForAutofill() && !deviceAuthenticator.hasValidDeviceAuthentication(),
            )
        }
    }

    fun onEnableAutofill(autofillScreenLaunchSource: AutofillScreenLaunchSource?) {
        autofillStore.autofillEnabled = true
        _viewState.value = _viewState.value.copy(autofillEnabled = true)

        pixel.fire(AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED, mapOf("source" to autofillScreenLaunchSource?.asString().orEmpty()))
    }

    fun onDisableAutofill(autofillScreenLaunchSource: AutofillScreenLaunchSource?) {
        autofillStore.autofillEnabled = false
        _viewState.value = _viewState.value.copy(autofillEnabled = false)

        pixel.fire(AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED, mapOf("source" to autofillScreenLaunchSource?.asString().orEmpty()))
    }

    fun onPasswordListClicked() {
        viewModelScope.launch {
            _commands.send(NavigatePasswordList)
        }
    }

    fun onImportFromDesktopWithSyncClicked(autofillScreenLaunchSource: AutofillScreenLaunchSource?) {
        viewModelScope.launch {
            _commands.send(NavigateToHowToSyncWithDesktop)
            // we use AUTOFILL_SYNC_DESKTOP_PASSWORDS_CTA_BUTTON for consistency with iOS
            pixel.fire(AUTOFILL_SYNC_DESKTOP_PASSWORDS_CTA_BUTTON, mapOf("source" to autofillScreenLaunchSource?.asString().orEmpty()))
        }
    }

    fun onImportPasswordsClicked(launchSource: AutofillImportLaunchSource) {
        viewModelScope.launch {
            _commands.send(ImportPasswordsFromGoogle)
            // we use AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED for consistency with iOS
            pixel.fire(
                AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED,
                mapOf("source" to launchSource.value),
            )
        }
    }

    fun onResetExcludedSitesClicked(autofillScreenLaunchSource: AutofillScreenLaunchSource?) {
        viewModelScope.launch {
            _commands.send(Command.AskToConfirmResetExcludedSites)
            pixel.fire(
                AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISPLAYED,
                mapOf("source" to autofillScreenLaunchSource?.asString().orEmpty()),
            )
        }
    }

    fun onResetExcludedSitesConfirmed() {
        viewModelScope.launch {
            neverSavedSiteRepository.clearNeverSaveList()
            pixel.fire(AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_CONFIRMED)
        }
    }

    fun onResetExcludedSitesCancelled() {
        viewModelScope.launch {
            pixel.fire(AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISMISSED)
        }
    }
}
