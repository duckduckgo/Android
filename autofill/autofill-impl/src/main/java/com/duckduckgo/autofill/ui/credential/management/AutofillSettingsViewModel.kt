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

    fun onViewCredentials(credentials: LoginCredentials, isStartingMode: Boolean) {
        addCommand(ShowCredentialMode(credentials, isStartingMode))
        _viewState.value = viewState.value.copy(credentialMode = Viewing(credentialsViewed = credentials))
    }

    fun onEditCredentials(
        credentials: LoginCredentials,
        isFromViewMode: Boolean
    ) {
        if (!isFromViewMode) {
            addCommand(ShowCredentialMode(credentials, !isFromViewMode))
        }
        _viewState.value = viewState.value.copy(credentialMode = Editing(credentialsViewed = credentials, isFromViewMode = isFromViewMode))
    }

    fun onCancelEditMode() {
        viewState.value.let { value ->
            value.credentialMode.let {
                // if not from view mode, it means edit mode was opened directly so we need to exit credential mode instead.
                if (it is Editing && it.isFromViewMode) {
                    _viewState.value = value.copy(credentialMode = Viewing(credentialsViewed = it.credentialsViewed))
                } else {
                    onExitCredentialMode()
                }
            }
        }
    }

    fun onExitCredentialMode() {
        addCommand(ExitCredentialMode)
        _viewState.value = viewState.value.copy(credentialMode = NotInCredentialMode)
    }

    fun allowSaveInEditMode(saveable: Boolean) {
        _viewState.value.credentialMode.let { credentialMode ->
            if (credentialMode is Editing) {
                _viewState.value = _viewState.value.copy(credentialMode = credentialMode.copy(saveable = saveable))
            }
        }
    }

    fun launchDeviceAuth() {
        addCommand(LaunchDeviceAuth)
    }

    fun lock() {
        if (!viewState.value.isLocked) {
            _viewState.value = viewState.value.copy(isLocked = true)
            addCommand(ShowLockedMode)
        }
    }

    fun unlock() {
        addCommand(ExitLockedMode)
        _viewState.value = viewState.value.copy(isLocked = false)
    }

    fun disabled() {
        _viewState.value = viewState.value.copy(isLocked = true)
        // Remove backstack modes if they are present
        addCommand(ExitCredentialMode)
        addCommand(ExitLockedMode)
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
        _viewState.value.credentialMode.credentialsViewed?.let { originalCredentials ->
            viewModelScope.launch {
                autofillStore.updateCredentials(updatedCredentials.copy(id = originalCredentials.id))
                autofillStore.getCredentialsWithId(originalCredentials.id!!)?.let { credentialsWithLastUpdatedTimeUpdated ->
                    _viewState.value = viewState.value.copy(
                        credentialMode = Viewing(
                            credentialsViewed = credentialsWithLastUpdatedTimeUpdated
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
            val saveable: Boolean = true,
            val isFromViewMode: Boolean
        ) : CredentialMode(credentialsViewed)

        object NotInCredentialMode : CredentialMode(null)
    }

    sealed class Command(val id: String = UUID.randomUUID().toString()) {
        class ShowUserUsernameCopied : Command()
        class ShowUserPasswordCopied : Command()
        data class ShowCredentialMode(val credentials: LoginCredentials, val isStartingMode: Boolean) : Command()
        object ShowDisabledMode : Command()
        object ShowLockedMode : Command()
        object LaunchDeviceAuth : Command()
        object ExitCredentialMode : Command()
        object ExitLockedMode : Command()
    }
}
