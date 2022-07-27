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

package com.duckduckgo.autofill.ui.credential.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.store.AutofillStore
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command.*
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialModeState.Editing
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialModeState.NotInCredentialMode
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialModeState.Viewing
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AutofillSettingsViewModel @Inject constructor(
    private val autofillStore: AutofillStore,
    private val clipboardInteractor: AutofillClipboardInteractor
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = MutableStateFlow<List<Command>>(emptyList())
    val commands: StateFlow<List<Command>> = _commands

    fun onCopyUsername(username: String?) {
        username?.let { clipboardInteractor.copyToClipboard(it) }
        addCommand(ShowUserUsernameCopied())
    }

    fun onCopyPassword(password: String?) {
        password?.let { clipboardInteractor.copyToClipboard(it) }
        addCommand(ShowUserPasswordCopied())
    }

    fun onViewCredentials(credentials: LoginCredentials) {
        _viewState.value = viewState.value.copy(credentialModeState = Viewing())
        addCommand(ShowCredentialMode(credentials))
    }

    fun onEditCredentials() {
        _viewState.value = viewState.value.copy(credentialModeState = Editing)
    }

    fun launchDeviceAuth() {
        addCommand(LaunchDeviceAuth)
    }

    fun lock() {
        if (!viewState.value.isLocked) {
            addCommand(ShowLockedMode)
        }
    }

    fun unlock() {
        _viewState.value = viewState.value.copy(isLocked = false, credentialModeState = NotInCredentialMode)
        addCommand(ShowListMode)
    }

    fun disabled() {
        _viewState.value = viewState.value.copy(isLocked = true, credentialModeState = NotInCredentialMode)
        addCommand(ShowDisabledMode)
    }

    private fun addCommand(command: Command) {
        Timber.v("Adding command %s", command)
        commands.value.let { commands ->
            val updatedList = commands + command
            _commands.value = updatedList
        }
    }

    fun commandProcessed(command: Command) {
        commands.value.let { currentCommands ->
            val updatedList = currentCommands.filterNot { it.id == command.id }
            _commands.value = updatedList
        }
    }

    fun observeCredentials() {
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(autofillEnabled = autofillStore.autofillEnabled)

            autofillStore.getAllCredentials().collect { credentials ->
                _viewState.value = _viewState.value.copy(
                    logins = credentials
                )
            }
        }
    }

    fun onDeleteCredentials(credentials: LoginCredentials) {
        val credentialsId = credentials.id ?: return

        viewModelScope.launch {
            autofillStore.deleteCredentials(credentialsId)
        }
        onExitViewMode()
    }

    fun updateCredentials(updatedCredentials: LoginCredentials) {
        viewModelScope.launch {
            autofillStore.updateCredentials(updatedCredentials)
        }
    }

    fun onEnableAutofill() {
        autofillStore.autofillEnabled = true
        _viewState.value = viewState.value.copy(autofillEnabled = true)
    }

    fun onDisableAutofill() {
        autofillStore.autofillEnabled = false
        _viewState.value = viewState.value.copy(autofillEnabled = false)
    }

    fun onExitEditMode(shouldReset: Boolean) {
        _viewState.value = viewState.value.copy(credentialModeState = Viewing(reset = shouldReset))
    }

    fun onExitViewMode() {
        _viewState.value = viewState.value.copy(credentialModeState = NotInCredentialMode)
        addCommand(ShowListMode)
    }

    data class ViewState(
        val autofillEnabled: Boolean = true,
        val logins: List<LoginCredentials> = emptyList(),
        val credentialModeState: CredentialModeState = NotInCredentialMode,
        val isLocked: Boolean = false
    )

    sealed class CredentialModeState {
        data class Viewing(val reset: Boolean = false) : CredentialModeState()
        object Editing : CredentialModeState()
        object NotInCredentialMode : CredentialModeState()
    }

    sealed class Command(val id: String = UUID.randomUUID().toString()) {
        class ShowUserUsernameCopied : Command()
        class ShowUserPasswordCopied : Command()
        data class ShowCredentialMode(val credentials: LoginCredentials) : Command()
        object ShowListMode : Command()
        object ShowDisabledMode : Command()
        object ShowLockedMode : Command()
        object LaunchDeviceAuth : Command()
    }
}
