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
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialMode.Editing
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialMode.NotInCredentialMode
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialMode.Viewing
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
        _viewState.value = viewState.value.copy(credentialMode = Viewing(credentialsViewed = credentials))
        addCommand(ShowCredentialMode(credentials))
    }

    fun onEditCredentials(loginCredentials: LoginCredentials) {
        _viewState.value = viewState.value.copy(credentialMode = Editing(credentialsViewed = loginCredentials))
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
        _viewState.value = viewState.value.copy(isLocked = false, credentialMode = NotInCredentialMode)
        addCommand(ShowListMode)
    }

    fun disabled() {
        _viewState.value = viewState.value.copy(isLocked = true, credentialMode = NotInCredentialMode)
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

    fun onDeleteCredentials(loginCredentials: LoginCredentials) {
        val credentialsId = loginCredentials.id ?: return

        viewModelScope.launch {
            autofillStore.deleteCredentials(credentialsId)
        }
    }

    fun updateCredentials(updatedCredentials: LoginCredentials) {
        _viewState.value.credentialMode.credentialsViewed?.let {
            viewModelScope.launch {
                autofillStore.updateCredentials(updatedCredentials.copy(id = it.id))
                autofillStore.getCredentialsWithId(it.id!!)?.let { credentials ->
                    _viewState.value = viewState.value.copy(
                        credentialMode = Viewing(
                            credentialsViewed = credentials
                        )
                    )
                }
            }
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

    fun onCancelEditMode() {
        viewState.value.credentialMode.credentialsViewed?.let {
            _viewState.value = viewState.value.copy(credentialMode = Viewing(credentialsViewed = it))
        }
    }

    fun onExitViewMode() {
        _viewState.value = viewState.value.copy(credentialMode = NotInCredentialMode)
        addCommand(ShowListMode)
    }

    fun allowSaveInEditMode(saveable: Boolean) {
        _viewState.value.credentialMode.let { credentialMode ->
            if (credentialMode is Editing) {
                _viewState.value = _viewState.value.copy(credentialMode = credentialMode.copy(saveable = saveable))
            }
        }
    }

    data class ViewState(
        val autofillEnabled: Boolean = true,
        val logins: List<LoginCredentials> = emptyList(),
        val credentialMode: CredentialMode = NotInCredentialMode,
        val isLocked: Boolean = false
    )

    sealed class CredentialMode(open val credentialsViewed: LoginCredentials?) {
        data class Viewing(override val credentialsViewed: LoginCredentials) : CredentialMode(credentialsViewed)
        data class Editing(
            override val credentialsViewed: LoginCredentials,
            val saveable: Boolean = true
        ) : CredentialMode(credentialsViewed)

        object NotInCredentialMode : CredentialMode(null)
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
