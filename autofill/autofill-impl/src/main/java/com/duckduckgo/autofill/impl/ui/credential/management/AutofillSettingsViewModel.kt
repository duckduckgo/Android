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

package com.duckduckgo.autofill.impl.ui.credential.management

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.MENU_ACTION_AUTOFILL_PRESSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.SETTINGS_AUTOFILL_MANAGEMENT_OPENED
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitDisabledMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitListMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitLockedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.InitialiseViewAfterUnlock
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.LaunchDeviceAuth
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.OfferUserUndoDeletion
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowDeviceUnsupportedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowDisabledMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowListMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowLockedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowUserPasswordCopied
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowUserUsernameCopied
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.Disabled
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.EditingExisting
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.EditingNewEntry
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.ListMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.Locked
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.Viewing
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialModeCommand.ShowEditCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialModeCommand.ShowManualCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.DuckAddressStatus.Activated
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.DuckAddressStatus.Deactivated
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.DuckAddressStatus.FailedToObtainStatus
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.DuckAddressStatus.FetchingActivationStatus
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.DuckAddressStatus.NotADuckAddress
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.DuckAddressStatus.NotManageable
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.DuckAddressStatus.SettingActivationStatus
import com.duckduckgo.autofill.impl.ui.credential.management.searching.CredentialListFilter
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.duckaddress.DuckAddressIdentifier
import com.duckduckgo.autofill.impl.ui.credential.repository.DuckAddressStatusRepository
import com.duckduckgo.autofill.impl.ui.credential.repository.DuckAddressStatusRepository.ActivationStatusResult
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.squareup.anvil.annotations.ContributesBinding
import java.util.*
import javax.inject.Inject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class AutofillSettingsViewModel @Inject constructor(
    private val autofillStore: AutofillStore,
    private val clipboardInteractor: AutofillClipboardInteractor,
    private val deviceAuthenticator: DeviceAuthenticator,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val credentialListFilter: CredentialListFilter,
    private val faviconManager: FaviconManager,
    private val webUrlIdentifier: WebUrlIdentifier,
    private val duckAddressStatusRepository: DuckAddressStatusRepository,
    private val emailManager: EmailManager,
    private val duckAddressIdentifier: DuckAddressIdentifier,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = MutableStateFlow<List<Command>>(emptyList())
    val commands: StateFlow<List<Command>> = _commands

    private val _commandsCredentialView = MutableStateFlow<List<CredentialModeCommand>>(emptyList())
    val commandsCredentialView: StateFlow<List<CredentialModeCommand>> = _commandsCredentialView

    // after unlocking, we want to initialise the view once (next unlock, the view stack will already exist)
    private var initialStateAlreadyPresented: Boolean = false

    private var searchQueryFilter = MutableStateFlow("")

    // after unlocking, we want to know which mode to return to
    private var credentialModeBeforeLocking: CredentialMode? = null

    private var combineJob: Job? = null

    fun onCopyUsername(username: String?) {
        username?.let { clipboardInteractor.copyToClipboard(it, isSensitive = false) }
        addCommand(ShowUserUsernameCopied())
    }

    fun onCopyPassword(password: String?) {
        password?.let { clipboardInteractor.copyToClipboard(it, isSensitive = true) }
        addCommand(ShowUserPasswordCopied())
    }

    fun onShowListMode() {
        _viewState.value = _viewState.value.copy(credentialMode = ListMode)
        addCommand(ShowListMode)
    }

    fun onViewCredentials(
        credentials: LoginCredentials,
    ) {
        _viewState.value = viewState.value.copy(
            credentialMode = Viewing(credentialsViewed = credentials, showLinkButton = credentials.shouldShowLinkButton()),
        )
        addCommand(ShowCredentialMode)

        updateDuckAddressStatus(credentials.username)
    }

    fun onCreateNewCredentials() {
        addCommand(ShowCredentialMode)
        _viewState.value = viewState.value.copy(credentialMode = EditingNewEntry())
        addCommand(ShowManualCredentialMode)
    }

    fun onEditCurrentCredentials() {
        val credentials = getCurrentCredentials() ?: return
        _viewState.value = viewState.value.copy(
            credentialMode = EditingExisting(
                credentialsViewed = credentials,
                startedCredentialModeWithEdit = false,
            ),
        )
        addCommand(ShowEditCredentialMode(credentials))
    }

    // if edit is opened but not from view mode, it means that we should show the credentials view, and we need to set hasPopulatedFields to false
    // to force credential view to prefill the fields.
    fun onEditCredentials(credentials: LoginCredentials) {
        addCommand(ShowCredentialMode)

        _viewState.value = viewState.value.copy(
            credentialMode = EditingExisting(
                credentialsViewed = credentials,
                startedCredentialModeWithEdit = true,
                hasPopulatedFields = false,
            ),
        )

        addCommand(ShowEditCredentialMode(credentials))
    }

    private fun getCurrentCredentials(): LoginCredentials? {
        return when (val viewMode = _viewState.value.credentialMode) {
            is Viewing -> viewMode.credentialsViewed
            is EditingExisting -> viewMode.credentialsViewed
            else -> null
        }
    }

    fun onCredentialEditModePopulated() {
        _viewState.value.credentialMode.let { credentialMode ->
            if (credentialMode is EditingExisting) {
                _viewState.value = _viewState.value.copy(credentialMode = credentialMode.copy(hasPopulatedFields = true))
            }
        }
    }

    fun onCancelEditMode() {
        viewState.value.let { value ->
            value.credentialMode.let {
                // if credential mode started with edit, it means that we need to exit credential mode right away instead of going
                // back to view mode.
                if (it is EditingExisting && !it.startedCredentialModeWithEdit) {
                    onViewCredentials(it.credentialsViewed)
                } else {
                    onExitCredentialMode()
                }
            }
        }
    }

    fun onCancelManualCreation() {
        onExitCredentialMode()
    }

    fun onExitCredentialMode() {
        addCommand(ExitCredentialMode)
    }

    fun allowSaveInEditMode(saveable: Boolean) {
        when (val credentialMode = _viewState.value.credentialMode) {
            is EditingExisting -> _viewState.value = _viewState.value.copy(credentialMode = credentialMode.copy(saveable = saveable))
            is EditingNewEntry -> _viewState.value = _viewState.value.copy(credentialMode = credentialMode.copy(saveable = saveable))
            else -> {}
        }
    }

    fun onViewStarted() {
        viewModelScope.launch(dispatchers.io()) {
            syncEngine.triggerSync(FEATURE_READ)
        }
    }

    suspend fun launchDeviceAuth() {
        if (!autofillStore.autofillAvailable) {
            Timber.d("Can't access secure storage so can't offer autofill functionality")
            deviceUnsupported()
            return
        }

        if (!deviceAuthenticator.hasValidDeviceAuthentication()) {
            Timber.d("Can't show device auth as there is no valid device authentication")
            disabled()
            return
        }

        if (autofillStore.getCredentialCount().first() == 0) {
            Timber.d("No credentials; can skip showing device auth")
            unlock()
        } else {
            addCommand(LaunchDeviceAuth)
        }
    }

    fun lock() {
        Timber.v("Locking autofill settings")
        trackCurrentModeBeforeLocking()
        addCommand(ShowLockedMode)
        _viewState.value = _viewState.value.copy(credentialMode = Locked)
    }

    fun unlock() {
        Timber.v("Unlocking autofill settings")
        addCommand(ExitDisabledMode)
        addCommand(ExitLockedMode)

        if (!initialStateAlreadyPresented) {
            initialStateAlreadyPresented = true
            addCommand(InitialiseViewAfterUnlock)
        }

        credentialModeBeforeLocking?.let { mode ->
            Timber.v("Will return view state to ${mode.javaClass.name}")
            _viewState.value = _viewState.value.copy(credentialMode = mode)
        }
    }

    private fun trackCurrentModeBeforeLocking() {
        _viewState.value.credentialMode.let { currentMode ->
            if (currentMode !is Locked && currentMode !is Disabled) {
                credentialModeBeforeLocking = currentMode
            }
        }
    }

    fun disabled() {
        // Remove backstack modes if they are present
        addCommand(ExitListMode)
        addCommand(ExitCredentialMode)
        addCommand(ExitLockedMode)
        addCommand(ShowDisabledMode)
        _viewState.value = _viewState.value.copy(credentialMode = Disabled)
    }

    private fun deviceUnsupported() {
        // Remove backstack modes if they are present
        addCommand(ExitListMode)
        addCommand(ExitCredentialMode)
        addCommand(ExitLockedMode)
        addCommand(ShowDeviceUnsupportedMode)
    }

    private fun addCommand(command: Command) {
        Timber.v("Adding command %s", command)
        commands.value.let { commands ->
            val updatedList = commands + command
            _commands.value = updatedList
        }
    }

    private fun addCommand(command: CredentialModeCommand) {
        commandsCredentialView.value.let { commands ->
            val updatedList = commands + command
            _commandsCredentialView.value = updatedList
        }
    }

    fun commandProcessed(command: Command) {
        commands.value.let { currentCommands ->
            val updatedList = currentCommands.filterNot { it.id == command.id }
            _commands.value = updatedList
        }
    }

    fun commandProcessed(command: CredentialModeCommand) {
        commandsCredentialView.value.let { currentCommands ->
            val updatedList = currentCommands.filterNot { it.id == command.id }
            _commandsCredentialView.value = updatedList
        }
    }

    fun observeCredentials() {
        if (combineJob != null) return
        combineJob = viewModelScope.launch(dispatchers.io()) {
            _viewState.value = _viewState.value.copy(autofillEnabled = autofillStore.autofillEnabled)
            val allCredentials = autofillStore.getAllCredentials().distinctUntilChanged()
            val combined = allCredentials.combine(searchQueryFilter) { credentials, filter ->
                credentialListFilter.filter(credentials, filter)
            }
            combined.collect { credentials ->
                _viewState.value = _viewState.value.copy(
                    logins = credentials,
                )
            }
        }
    }

    fun onDeleteCurrentCredentials() {
        getCurrentCredentials()?.let {
            onDeleteCredentials(it)
        }
    }

    fun onDeleteCredentials(loginCredentials: LoginCredentials) {
        val credentialsId = loginCredentials.id ?: return

        viewModelScope.launch(dispatchers.io()) {
            loginCredentials.domain?.let {
                faviconManager.deletePersistedFavicon(it)
            }
            val existingCredentials = autofillStore.deleteCredentials(credentialsId)
            addCommand(OfferUserUndoDeletion(existingCredentials))

            Timber.i("Deleted $existingCredentials")
        }
    }

    fun saveOrUpdateCredentials(credentials: LoginCredentials) {
        viewModelScope.launch(dispatchers.io()) {
            val credentialMode = _viewState.value.credentialMode

            if (credentialMode is EditingExisting) {
                updateExistingCredential(credentialMode, credentials)
            } else if (credentialMode is EditingNewEntry) {
                saveNewCredential(credentials)
            }

            updateDuckAddressStatus(credentials.username)
        }
    }

    fun reinsertCredentials(credentials: LoginCredentials) {
        viewModelScope.launch(dispatchers.io()) {
            autofillStore.reinsertCredentials(credentials)
        }
    }

    private fun LoginCredentials.shouldShowLinkButton(): Boolean {
        return webUrlIdentifier.isLikelyAUrl(domain)
    }

    private suspend fun updateExistingCredential(
        credentialMode: EditingExisting,
        updatedCredentials: LoginCredentials,
    ) {
        val existingCredentials = credentialMode.credentialsViewed
        autofillStore.updateCredentials(updatedCredentials.copy(id = existingCredentials.id))?.let { updated ->
            _viewState.value = viewState.value.copy(
                credentialMode = Viewing(
                    credentialsViewed = updated,
                    showLinkButton = updated.shouldShowLinkButton(),
                ),
            )
        }
    }

    private suspend fun saveNewCredential(updatedCredentials: LoginCredentials) {
        autofillStore.saveCredentials(
            rawUrl = updatedCredentials.domain ?: "",
            credentials = updatedCredentials,
        )?.let { savedCredentials ->
            _viewState.value = viewState.value.copy(
                credentialMode = Viewing(
                    credentialsViewed = savedCredentials,
                    showLinkButton = savedCredentials.shouldShowLinkButton(),
                ),
            )
        }
    }

    fun onEnableAutofill() {
        autofillStore.autofillEnabled = true
        _viewState.value = viewState.value.copy(autofillEnabled = true)

        pixel.fire(AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED)
    }

    fun onDisableAutofill() {
        autofillStore.autofillEnabled = false
        _viewState.value = viewState.value.copy(autofillEnabled = false)

        pixel.fire(AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED)
    }

    fun onSearchQueryChanged(searchText: String) {
        Timber.v("Search query changed: %s", searchText)
        searchQueryFilter.value = searchText
        val showAutofillEnabledToggle = searchText.isEmpty()
        _viewState.value = _viewState.value.copy(credentialSearchQuery = searchText, showAutofillEnabledToggle = showAutofillEnabledToggle)
    }

    @OptIn(ExperimentalContracts::class)
    private fun isPrivateDuckAddress(
        username: String?,
        mainDuckAddress: String?,
    ): Boolean {
        contract {
            returns(true) implies (username != null)
            returns(true) implies (mainDuckAddress != null)
        }

        if (username == null) return false

        return duckAddressIdentifier.isPrivateDuckAddress(username, mainDuckAddress)
    }

    private fun updateDuckAddressStatus(username: String?) {
        Timber.d("Determining duck address status for %s", username)

        viewModelScope.launch(dispatchers.io()) {
            val mainAddress = emailManager.getEmailAddress()
            if (!isPrivateDuckAddress(username, mainAddress)) {
                Timber.d("Not a private duck address: %s", username)
            } else {
                val credMode = viewState.value.credentialMode
                if (credMode is Viewing) {
                    Timber.d("Fetching duck address status from the network for %s", username)
                    retrieveStatusFromNetwork(credMode, username)
                }
            }
        }
    }

    private fun retrieveStatusFromNetwork(
        credMode: Viewing,
        duckAddress: String,
    ) {
        _viewState.value = viewState.value.copy(credentialMode = credMode.copy(duckAddressStatus = FetchingActivationStatus(duckAddress)))

        viewModelScope.launch(dispatchers.io()) {
            when (duckAddressStatusRepository.getActivationStatus(duckAddress)) {
                ActivationStatusResult.Activated -> {
                    _viewState.value = viewState.value.copy(credentialMode = credMode.copy(duckAddressStatus = Activated(duckAddress)))
                }

                ActivationStatusResult.Deactivated -> {
                    _viewState.value = viewState.value.copy(credentialMode = credMode.copy(duckAddressStatus = Deactivated(duckAddress)))
                }

                ActivationStatusResult.NotSignedIn -> {
                    Timber.d("Not signed into email protection; can't manage %s", duckAddress)
                    _viewState.value = viewState.value.copy(credentialMode = credMode.copy(duckAddressStatus = DuckAddressStatus.NotSignedIn))
                }

                ActivationStatusResult.Unmanageable -> {
                    Timber.w("Can't manage %s from this account", duckAddress)
                    _viewState.value = viewState.value.copy(credentialMode = credMode.copy(duckAddressStatus = NotManageable))
                }

                ActivationStatusResult.GeneralError -> {
                    Timber.w("General error when querying status for %s", duckAddress)
                    _viewState.value = viewState.value.copy(credentialMode = credMode.copy(duckAddressStatus = FailedToObtainStatus))
                }
            }
        }
    }

    fun activationStatusChanged(
        checked: Boolean,
        duckAddress: String,
    ) {
        val credMode = viewState.value.credentialMode
        if (credMode is Viewing) {
            _viewState.value = viewState.value.copy(credentialMode = credMode.copy(duckAddressStatus = SettingActivationStatus(checked)))

            viewModelScope.launch {
                val success = duckAddressStatusRepository.setActivationStatus(duckAddress, checked)
                if (success) {
                    if (checked) {
                        _viewState.value = viewState.value.copy(credentialMode = credMode.copy(duckAddressStatus = Activated(duckAddress)))
                    } else {
                        _viewState.value = viewState.value.copy(credentialMode = credMode.copy(duckAddressStatus = Deactivated(duckAddress)))
                    }
                } else {
                    _viewState.value = viewState.value.copy(credentialMode = credMode.copy(duckAddressStatus = FailedToObtainStatus))
                }
            }
        }
    }

    /**
     * Responsible for sending pixels which were previously managed in the app module.
     *
     * There are multiple ways to launch this screen, which should map to existing pixels where they exist.
     */
    fun sendLaunchPixel(launchedFromBrowser: Boolean, directLinkToCredentials: Boolean) {
        // no existing pixel for this scenario; don't want it to inflate other existing pixels
        if (directLinkToCredentials) return

        // map scenario onto existing pixels
        val pixelName = if (launchedFromBrowser) {
            MENU_ACTION_AUTOFILL_PRESSED
        } else {
            SETTINGS_AUTOFILL_MANAGEMENT_OPENED
        }

        pixel.fire(pixelName)
    }

    data class ViewState(
        val autofillEnabled: Boolean = true,
        val showAutofillEnabledToggle: Boolean = true,
        val logins: List<LoginCredentials>? = null,
        val credentialMode: CredentialMode? = null,
        val credentialSearchQuery: String = "",
    )

    /**
     * Supported credentials modes, each of which might have a different UI and different subtypes.
     *
     * ListMode: Shows the list of all credentials. The default mode, shown when the user is not viewing or editing a credential
     * Viewing: viewing a single credential
     * Editing: there are two subtypes of editing:
     *     EditingNewEntry is used when the user is creating a new credential.
     *     EditingExisting is used when the user is editing an existing credential.
     */
    sealed class CredentialMode {
        object ListMode : CredentialMode()

        data class Viewing(
            val credentialsViewed: LoginCredentials,
            val showLinkButton: Boolean,
            val duckAddressStatus: DuckAddressStatus = NotADuckAddress,
        ) : CredentialMode()

        abstract class Editing(
            open val saveable: Boolean = false,
        ) : CredentialMode()

        data class EditingExisting(
            val credentialsViewed: LoginCredentials,
            val hasPopulatedFields: Boolean = true,
            override val saveable: Boolean = true,
            val startedCredentialModeWithEdit: Boolean,
        ) : Editing(saveable)

        data class EditingNewEntry(
            override val saveable: Boolean = true,
        ) : Editing(saveable)

        object Disabled : CredentialMode()
        object Locked : CredentialMode()
    }

    sealed class Command(val id: String = UUID.randomUUID().toString()) {
        class ShowUserUsernameCopied : Command()
        class ShowUserPasswordCopied : Command()

        class OfferUserUndoDeletion(val credentials: LoginCredentials?) : Command()

        object ShowListMode : Command()
        object ShowCredentialMode : Command()
        object ShowDisabledMode : Command()
        object ShowDeviceUnsupportedMode : Command()
        object ShowLockedMode : Command()
        object LaunchDeviceAuth : Command()
        object ExitCredentialMode : Command()
        object ExitListMode : Command()
        object ExitLockedMode : Command()
        object InitialiseViewAfterUnlock : Command()
        object ExitDisabledMode : Command()
    }

    sealed class CredentialModeCommand(val id: String = UUID.randomUUID().toString()) {
        data class ShowEditCredentialMode(val credentials: LoginCredentials) : CredentialModeCommand()
        object ShowManualCredentialMode : CredentialModeCommand()
    }

    sealed class DuckAddressStatus {
        object NotADuckAddress : DuckAddressStatus()
        data class FetchingActivationStatus(val address: String) : DuckAddressStatus()
        data class SettingActivationStatus(val activating: Boolean) : DuckAddressStatus()
        data class Activated(val address: String) : DuckAddressStatus()
        data class Deactivated(val address: String) : DuckAddressStatus()
        object NotManageable : DuckAddressStatus()
        object FailedToObtainStatus : DuckAddressStatus()
        object NotSignedIn : DuckAddressStatus()
    }
}

interface WebUrlIdentifier {
    fun isLikelyAUrl(domain: String?): Boolean
}

@ContributesBinding(ActivityScope::class)
class RegexBasedUrlIdentifier @Inject constructor() : WebUrlIdentifier {

    override fun isLikelyAUrl(domain: String?): Boolean {
        if (domain.isNullOrBlank()) {
            return false
        }

        return Patterns.WEB_URL.matcher(domain).matches()
    }
}
