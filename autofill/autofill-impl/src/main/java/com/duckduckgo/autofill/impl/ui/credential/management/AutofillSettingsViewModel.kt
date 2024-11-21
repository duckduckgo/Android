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
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.BrowserOverflow
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.BrowserSnackbar
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.DisableInSettingsPrompt
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.InternalDevSettings
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.NewTabShortcut
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.SettingsActivity
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.Sync
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.Unknown
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthConfiguration
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DELETE_LOGIN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_MANAGEMENT_SCREEN_OPENED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_MANUALLY_SAVE_CREDENTIAL
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISPLAYED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT_AVAILABLE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_DISMISSED
import com.duckduckgo.autofill.impl.reporting.AutofillBreakageReportCanShowRules
import com.duckduckgo.autofill.impl.reporting.AutofillBreakageReportSender
import com.duckduckgo.autofill.impl.reporting.AutofillSiteBreakageReportingDataStore
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitDisabledMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitListMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitLockedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.InitialiseViewAfterUnlock
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.LaunchDeviceAuth
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.OfferUserUndoDeletion
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.OfferUserUndoMassDeletion
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
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchDeleteAllPasswordsConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchImportPasswordsFromGooglePasswordManager
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchReportAutofillBreakageConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchResetNeverSaveListConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.PromptUserToAuthenticateMassDeletion
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.ReevalutePromotions
import com.duckduckgo.autofill.impl.ui.credential.management.neversaved.NeverSavedSitesViewState
import com.duckduckgo.autofill.impl.ui.credential.management.searching.CredentialListFilter
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.duckaddress.DuckAddressIdentifier
import com.duckduckgo.autofill.impl.ui.credential.repository.DuckAddressStatusRepository
import com.duckduckgo.autofill.impl.ui.credential.repository.DuckAddressStatusRepository.ActivationStatusResult
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.common.utils.DispatcherProvider
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
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class AutofillSettingsViewModel @Inject constructor(
    private val autofillStore: InternalAutofillStore,
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
    private val neverSavedSiteRepository: NeverSavedSiteRepository,
    private val urlMatcher: AutofillUrlMatcher,
    private val autofillBreakageReportSender: AutofillBreakageReportSender,
    private val autofillBreakageReportDataStore: AutofillSiteBreakageReportingDataStore,
    private val autofillBreakageReportCanShowRules: AutofillBreakageReportCanShowRules,
    private val autofillFeature: AutofillFeature,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val _neverSavedSitesViewState = MutableStateFlow(NeverSavedSitesViewState())
    val neverSavedSitesViewState: StateFlow<NeverSavedSitesViewState> = _neverSavedSitesViewState

    private val _commands = MutableStateFlow<List<Command>>(emptyList())
    val commands: StateFlow<List<Command>> = _commands

    private val _commandsCredentialView = MutableStateFlow<List<CredentialModeCommand>>(emptyList())
    val commandsCredentialView: StateFlow<List<CredentialModeCommand>> = _commandsCredentialView

    private val _commandsListView = MutableStateFlow<List<ListModeCommand>>(emptyList())
    val commandsListView: StateFlow<List<ListModeCommand>> = _commandsListView

    // after unlocking, we want to initialise the view once (next unlock, the view stack will already exist)
    private var initialStateAlreadyPresented: Boolean = false

    private var searchQueryFilter = MutableStateFlow("")

    // after unlocking, we want to know which mode to return to
    private var credentialModeBeforeLocking: CredentialMode? = null

    private var combineJob: Job? = null

    // we only want to send this once for this 'session' of being in the management screen
    private var importGooglePasswordButtonShownPixelSent = false

    fun onCopyUsername(username: String?) {
        username?.let { clipboardInteractor.copyToClipboard(it, isSensitive = false) }
        pixel.fire(AutofillPixelNames.AUTOFILL_COPY_USERNAME)
        addCommand(ShowUserUsernameCopied())
    }

    fun onCopyPassword(password: String?) {
        password?.let { clipboardInteractor.copyToClipboard(it, isSensitive = true) }
        pixel.fire(AutofillPixelNames.AUTOFILL_COPY_PASSWORD)
        addCommand(ShowUserPasswordCopied())
    }

    fun onInitialiseListMode() {
        onShowListMode()
    }

    fun onReturnToListModeFromCredentialMode() {
        onShowListMode()
    }

    private fun onShowListMode() {
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

    private suspend fun showPromotionIfEligible() {
        withContext(dispatchers.io()) {
            val userIsSearching = _viewState.value.credentialSearchQuery.isNotEmpty()

            val canShowPromo = when {
                userIsSearching -> false
                else -> true
            }

            _viewState.value = _viewState.value.copy(canShowPromo = canShowPromo)
            addCommand(ReevalutePromotions)
        }
    }

    suspend fun launchDeviceAuth() {
        if (!autofillStore.autofillAvailable()) {
            Timber.d("Can't access secure storage so can't offer autofill functionality")
            deviceUnsupported()
            return
        }

        if (deviceAuthenticator.isAuthenticationRequiredForAutofill() && !deviceAuthenticator.hasValidDeviceAuthentication()) {
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
        Timber.v("Adding command %s", command::class.simpleName)
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

    private fun addCommand(command: ListModeCommand) {
        commandsListView.value.let { commands ->
            val updatedList = commands + command
            _commandsListView.value = updatedList
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

    fun commandProcessed(command: ListModeCommand) {
        commandsListView.value.let { currentCommands ->
            val updatedList = currentCommands.filterNot { it.id == command.id }
            _commandsListView.value = updatedList
        }
    }

    fun onViewCreated() {
        if (combineJob != null) return
        combineJob = viewModelScope.launch(dispatchers.io()) {
            _viewState.value = _viewState.value.copy(autofillEnabled = autofillStore.autofillEnabled)

            val allCredentials = autofillStore.getAllCredentials().distinctUntilChanged()
            val combined = allCredentials.combine(searchQueryFilter) { credentials, filter ->
                credentialListFilter.filter(credentials, filter)
            }
            combined.collect { credentials ->
                val updatedBreakageState = _viewState.value.reportBreakageState.copy(allowBreakageReporting = isBreakageReportingAllowed())
                _viewState.value = _viewState.value.copy(
                    logins = credentials,
                    reportBreakageState = updatedBreakageState,
                )
                showPromotionIfEligible()
            }
        }

        viewModelScope.launch(dispatchers.io()) {
            neverSavedSiteRepository.neverSaveListCount().collect { count ->
                _neverSavedSitesViewState.value = NeverSavedSitesViewState(showOptionToReset = count > 0)
            }
        }

        viewModelScope.launch(dispatchers.io()) {
            val gpmImport = autofillFeature.self().isEnabled() && autofillFeature.canImportFromGooglePasswordManager().isEnabled()
            val webViewWebMessageSupport = webViewCapabilityChecker.isSupported(WebMessageListener)
            val webViewDocumentStartJavascript = webViewCapabilityChecker.isSupported(DocumentStartJavaScript)
            val canImport = gpmImport && webViewWebMessageSupport && webViewDocumentStartJavascript
            _viewState.value = _viewState.value.copy(canImportFromGooglePasswords = canImport)
        }
    }

    private suspend fun isBreakageReportingAllowed(): Boolean {
        val url = _viewState.value.reportBreakageState.currentUrl ?: return false
        return autofillBreakageReportCanShowRules.canShowForSite(url)
    }

    fun onDeleteCurrentCredentials() {
        getCurrentCredentials()?.let {
            onDeleteCredentials(it)
        }
    }

    fun onDeleteCredentials(loginCredentials: LoginCredentials) {
        pixel.fire(AUTOFILL_DELETE_LOGIN)

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

    fun reinsertCredentials(credentials: List<LoginCredentials>) {
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

        pixel.fire(AutofillPixelNames.AUTOFILL_MANUALLY_UPDATE_CREDENTIAL)
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

        pixel.fire(AUTOFILL_MANUALLY_SAVE_CREDENTIAL)
    }

    fun onEnableAutofill() {
        autofillStore.autofillEnabled = true
        _viewState.value = viewState.value.copy(autofillEnabled = true)

        pixel.fire(AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED)
    }

    fun onDisableAutofill(autofillSettingsLaunchSource: AutofillSettingsLaunchSource?) {
        autofillStore.autofillEnabled = false
        _viewState.value = viewState.value.copy(autofillEnabled = false)

        pixel.fire(AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED, mapOf("source" to autofillSettingsLaunchSource?.asString().orEmpty()))
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
     * There are multiple ways to launch this screen, so we include a source parameter to differentiate between them.
     */
    fun sendLaunchPixel(launchSource: AutofillSettingsLaunchSource) {
        Timber.v("Opened autofill management screen from from %s", launchSource)

        val source = launchSource.asString()
        pixel.fire(AUTOFILL_MANAGEMENT_SCREEN_OPENED, mapOf("source" to source))
    }

    fun onUserConfirmationToClearNeverSavedSites() {
        viewModelScope.launch(dispatchers.io()) {
            neverSavedSiteRepository.clearNeverSaveList()
            pixel.fire(AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_CONFIRMED)
        }
    }

    fun onUserCancelledFromClearNeverSavedSitesPrompt() {
        pixel.fire(AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISMISSED)
    }

    fun onResetNeverSavedSitesInitialSelection() {
        addCommand(LaunchResetNeverSaveListConfirmation)
        pixel.fire(AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISPLAYED)
    }

    fun onDeleteAllPasswordsInitialSelection() {
        val numberToDelete = viewState.value.logins.orEmpty().size
        if (numberToDelete > 0) {
            addCommand(LaunchDeleteAllPasswordsConfirmation(numberToDelete))
        }
    }

    fun onDeleteAllPasswordsConfirmed() {
        val authConfiguration = AuthConfiguration(
            requireUserAction = true,
            displayTextResource = R.string.autofill_auth_text_for_delete_all,
            displayTitleResource = R.string.autofill_title_text_for_delete_all,
        )

        addCommand(PromptUserToAuthenticateMassDeletion(authConfiguration))
    }

    fun onAuthenticatedToDeleteAllPasswords() {
        viewModelScope.launch(dispatchers.io()) {
            val removedCredentials = autofillStore.deleteAllCredentials()
            Timber.i("Removed %d credentials", removedCredentials.size)

            if (removedCredentials.isNotEmpty()) {
                addCommand(OfferUserUndoMassDeletion(removedCredentials))
            }

            pixel.fire(AutofillPixelNames.AUTOFILL_DELETE_ALL_LOGINS)
        }
    }

    fun onImportPasswordsFromGooglePasswordManager() {
        viewModelScope.launch(dispatchers.io()) {
            addCommand(LaunchImportPasswordsFromGooglePasswordManager)
        }
    }

    fun onReportBreakageClicked() {
        val currentUrl = _viewState.value.reportBreakageState.currentUrl
        val eTldPlusOne = urlMatcher.extractUrlPartsForAutofill(currentUrl).eTldPlus1
        if (eTldPlusOne != null) {
            pixel.fire(AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_DISPLAYED)
            addCommand(LaunchReportAutofillBreakageConfirmation(eTldPlusOne))
        }
    }

    fun updateCurrentSite(
        currentUrl: String?,
        privacyProtectionEnabled: Boolean?,
    ) {
        val updatedReportBreakageState = _viewState.value.reportBreakageState.copy(
            currentUrl = currentUrl,
            privacyProtectionEnabled = privacyProtectionEnabled,
        )
        _viewState.value = _viewState.value.copy(reportBreakageState = updatedReportBreakageState)
    }

    fun onReportBreakageShown() {
        if (!_viewState.value.reportBreakageState.onReportBreakageShown) {
            val updatedReportBreakageState = _viewState.value.reportBreakageState.copy(onReportBreakageShown = true)
            _viewState.value = _viewState.value.copy(reportBreakageState = updatedReportBreakageState)

            pixel.fire(AUTOFILL_SITE_BREAKAGE_REPORT_AVAILABLE)
        }
    }

    fun userConfirmedSendBreakageReport() {
        val currentUrl = _viewState.value.reportBreakageState.currentUrl
        val privacyProtectionEnabled = _viewState.value.reportBreakageState.privacyProtectionEnabled

        currentUrl?.let {
            autofillBreakageReportSender.sendBreakageReport(it, privacyProtectionEnabled)
        }

        viewModelScope.launch(dispatchers.io()) {
            urlMatcher.extractUrlPartsForAutofill(currentUrl).eTldPlus1?.let {
                autofillBreakageReportDataStore.recordFeedbackSent(it)
            }
        }

        val updatedReportBreakageState = _viewState.value.reportBreakageState.copy(allowBreakageReporting = false)
        _viewState.value = _viewState.value.copy(reportBreakageState = updatedReportBreakageState)

        addCommand(ListModeCommand.ShowUserReportSentMessage)

        pixel.fire(AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_CONFIRMED)
    }

    fun userCancelledSendBreakageReport() {
        pixel.fire(AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_DISMISSED)
    }

    private fun AutofillSettingsLaunchSource.asString(): String {
        return when (this) {
            SettingsActivity -> "settings"
            BrowserOverflow -> "overflow_menu"
            Sync -> "sync"
            DisableInSettingsPrompt -> "save_login_disable_prompt"
            NewTabShortcut -> "new_tab_page_shortcut"
            BrowserSnackbar -> "browser_snackbar"
            InternalDevSettings -> "internal_dev_settings"
            Unknown -> "unknown"
            else -> this.name
        }
    }

    fun userReturnedFromSyncSettings() {
        viewModelScope.launch(dispatchers.io()) {
            showPromotionIfEligible()
        }
    }

    fun onPromoDismissed() {
        viewModelScope.launch(dispatchers.io()) {
            showPromotionIfEligible()
        }
    }

    fun recordImportGooglePasswordButtonShown() {
        if (!importGooglePasswordButtonShownPixelSent) {
            importGooglePasswordButtonShownPixelSent = true
            pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_SHOWN)
        }
    }

    data class ViewState(
        val autofillEnabled: Boolean = true,
        val showAutofillEnabledToggle: Boolean = true,
        val logins: List<LoginCredentials>? = null,
        val credentialMode: CredentialMode? = null,
        val credentialSearchQuery: String = "",
        val reportBreakageState: ReportBreakageState = ReportBreakageState(),
        val canShowPromo: Boolean = false,
        val canImportFromGooglePasswords: Boolean = false,
    )

    data class ReportBreakageState(
        val currentUrl: String? = null,
        val allowBreakageReporting: Boolean = false,
        val privacyProtectionEnabled: Boolean? = null,
        val onReportBreakageShown: Boolean = false,
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
        class OfferUserUndoMassDeletion(val credentials: List<LoginCredentials>) : Command()

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

    sealed class ListModeCommand(val id: String = UUID.randomUUID().toString()) {
        data object LaunchResetNeverSaveListConfirmation : ListModeCommand()
        data class LaunchDeleteAllPasswordsConfirmation(val numberToDelete: Int) : ListModeCommand()
        data class PromptUserToAuthenticateMassDeletion(val authConfiguration: AuthConfiguration) : ListModeCommand()
        data object LaunchImportPasswordsFromGooglePasswordManager : ListModeCommand()
        data class LaunchReportAutofillBreakageConfirmation(val eTldPlusOne: String) : ListModeCommand()
        data object ShowUserReportSentMessage : ListModeCommand()
        data object ReevalutePromotions : ListModeCommand()
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
