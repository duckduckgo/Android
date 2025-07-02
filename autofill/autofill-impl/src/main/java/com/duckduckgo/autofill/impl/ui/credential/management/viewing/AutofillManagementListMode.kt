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

package com.duckduckgo.autofill.impl.ui.credential.management.viewing

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementListModeBinding
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthConfiguration
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.autofill.impl.importing.AutofillImportLaunchSource.PasswordManagementEmpty
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementActivity
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.AutofillToggleState
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyPassword
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyUsername
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Delete
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Edit
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.CredentialsLoadedState.Loaded
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.CredentialsLoadedState.Loading
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.ListModeCommand.LaunchDeleteAllPasswordsConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.ListModeCommand.LaunchImportGooglePasswords
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.ListModeCommand.LaunchReportAutofillBreakageConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.ListModeCommand.LaunchResetNeverSaveListConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.ListModeCommand.PromptUserToAuthenticateMassDeletion
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.ListModeCommand.ReevalutePromotions
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.ListModeCommand.ShowUserReportSentMessage
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.ViewState
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordActivityParams
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordsPixelSender
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialGrouper
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.InitialExtractor
import com.duckduckgo.autofill.impl.ui.credential.management.suggestion.SuggestionListBuilder
import com.duckduckgo.autofill.impl.ui.credential.management.suggestion.SuggestionMatcher
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.SearchBar
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.logcat

@InjectWith(FragmentScope::class)
class AutofillManagementListMode : DuckDuckGoFragment(R.layout.fragment_autofill_management_list_mode) {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var credentialGrouper: CredentialGrouper

    @Inject
    lateinit var suggestionMatcher: SuggestionMatcher

    @Inject
    lateinit var suggestionListBuilder: SuggestionListBuilder

    @Inject
    lateinit var initialExtractor: InitialExtractor

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var deviceAuthenticator: DeviceAuthenticator

    @Inject
    lateinit var stringBuilder: AutofillManagementStringBuilder

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var screenPromotionPlugins: PluginPoint<PasswordsScreenPromotionPlugin>

    @Inject
    lateinit var importPasswordsPixelSender: ImportPasswordsPixelSender

    @Inject
    lateinit var autofillFeature: AutofillFeature

    @Inject
    lateinit var grouper: CredentialGrouper

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillPasswordsManagementViewModel::class.java]
    }

    private val binding: FragmentAutofillManagementListModeBinding by viewBinding()
    private lateinit var adapter: AutofillManagementRecyclerAdapter

    private var searchMenuItem: MenuItem? = null
    private var resetNeverSavedSitesMenuItem: MenuItem? = null
    private var deleteAllPasswordsMenuItem: MenuItem? = null
    private var syncDesktopPasswordsMenuItem: MenuItem? = null
    private var importGooglePasswordsMenuItem: MenuItem? = null

    private fun launchHelpPage() {
        activity?.let {
            globalActivityStarter.start(
                it,
                WebViewActivityWithParams(
                    url = LEARN_MORE_LINK,
                    screenTitle = getString(R.string.credentialManagementAutofillHelpPageTitle),
                ),
            )
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        configureRecyclerView()
        configureCurrentSiteState()
        observeViewModel()
        configureToolbar()
        logcat(VERBOSE) { "${this::class.java.simpleName} created" }
    }

    private suspend fun getPromotionView(): View? {
        return withContext(dispatchers.main()) {
            val state = viewModel.viewState.value

            if (!state.canShowPromo) {
                return@withContext null
            }

            val promotionView = context?.let { ctx ->
                screenPromotionPlugins.getPlugins().firstNotNullOfOrNull { it.getView(ctx, numberSavedPasswords = state.logins?.size ?: 0) }
            }

            return@withContext promotionView
        }
    }

    private fun configureCurrentSiteState() {
        viewModel.updateCurrentSite(getCurrentSiteUrl(), getPrivacyProtectionEnabled())
    }

    override fun onStop() {
        super.onStop()
        hideSearchBar()
    }

    private fun configureToolbar() {
        activity?.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    menuInflater.inflate(R.menu.autofill_list_mode_menu, menu)
                    searchMenuItem = menu.findItem(R.id.searchLogins)
                    resetNeverSavedSitesMenuItem = menu.findItem(R.id.resetNeverSavedSites)
                    deleteAllPasswordsMenuItem = menu.findItem(R.id.deleteAllPasswords)
                    syncDesktopPasswordsMenuItem = menu.findItem(R.id.syncDesktopPasswords)
                    importGooglePasswordsMenuItem = menu.findItem(R.id.importGooglePasswords)

                    initializeSearchBar()
                }

                override fun onPrepareMenu(menu: Menu) {
                    val loginsSaved = !viewModel.viewState.value.logins.isNullOrEmpty()
                    searchMenuItem?.isVisible = loginsSaved
                    deleteAllPasswordsMenuItem?.isVisible = loginsSaved
                    resetNeverSavedSitesMenuItem?.isVisible = viewModel.neverSavedSitesViewState.value.showOptionToReset
                    syncDesktopPasswordsMenuItem?.isVisible = loginsSaved
                    importGooglePasswordsMenuItem?.isVisible = loginsSaved && viewModel.viewState.value.canImportFromGooglePasswords
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.addLoginManually -> {
                            viewModel.onCreateNewCredentials()
                            true
                        }

                        R.id.resetNeverSavedSites -> {
                            viewModel.onResetNeverSavedSitesInitialSelection()
                            true
                        }

                        R.id.deleteAllPasswords -> {
                            viewModel.onDeleteAllPasswordsInitialSelection()
                            true
                        }

                        R.id.importGooglePasswords -> {
                            viewModel.onImportPasswordsFromGooglePasswordManager()
                            importPasswordsPixelSender.onImportPasswordsOverflowMenuTapped()
                            true
                        }

                        R.id.syncDesktopPasswords -> {
                            launchImportPasswordsFromDesktopSyncScreen()
                            importPasswordsPixelSender.onImportPasswordsViaDesktopSyncOverflowMenuTapped()
                            true
                        }

                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            State.RESUMED,
        )
    }

    private fun initializeSearchBar() {
        searchMenuItem?.setOnMenuItemClickListener {
            showSearchBar()
            return@setOnMenuItemClickListener true
        }

        parentBinding()?.let { parentBinding ->
            parentBinding.searchBar.onAction {
                when (it) {
                    is SearchBar.Action.PerformUpAction -> hideSearchBar()
                    is SearchBar.Action.PerformSearch -> viewModel.onSearchQueryChanged(it.searchText)
                }
            }
        }
    }

    private fun showSearchBar() = parentActivity()?.showSearchBar()
    private fun hideSearchBar() = parentActivity()?.hideSearchBar()

    private fun getCurrentSiteUrl() = arguments?.getString(ARG_CURRENT_URL, null)
    private fun getPrivacyProtectionEnabled() = arguments?.getBoolean(ARG_PRIVACY_PROTECTION_STATUS)
    private fun getAutofillSettingsLaunchSource(): AutofillScreenLaunchSource? =
        arguments?.getSerializable(ARG_AUTOFILL_SETTINGS_LAUNCH_SOURCE) as AutofillScreenLaunchSource?

    private fun parentBinding() = parentActivity()?.binding
    private fun parentActivity() = (activity as AutofillManagementActivity?)

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(State.STARTED) {
                viewModel.viewState.collect { state ->
                    updateWithViewState(state)
                }
            }
        }
        observeListModeViewModelCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(State.STARTED) {
                viewModel.neverSavedSitesViewState.collect {
                    // we can just invalidate the menu as [onPrepareMenu] will handle the new visibility for resetting never saved sites menu item
                    parentActivity()?.invalidateOptionsMenu()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(State.STARTED) {
                viewModel.viewState.collect {
                    // we can just invalidate the menu as [onPrepareMenu] will handle the new visibility for importing passwords menu item
                    parentActivity()?.invalidateOptionsMenu()
                }
            }
        }

        viewModel.onViewCreated()
    }

    private suspend fun updateWithViewState(state: ViewState) {
        val promotionView = getPromotionView()
        credentialsListUpdated(
            credentials = state.logins,
            credentialSearchQuery = state.credentialSearchQuery,
            allowBreakageReporting = state.reportBreakageState.allowBreakageReporting,
            canShowImportGooglePasswordsButton = state.canImportFromGooglePasswords,
            showAutofillToggle = state.showAutofillEnabledToggle,
            promotionView = promotionView,
        )
        parentActivity()?.invalidateOptionsMenu()
    }

    private fun observeListModeViewModelCommands() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(State.STARTED) {
                viewModel.commandsListView.collect { commands ->
                    commands.forEach { processCommand(it) }
                }
            }
        }
    }

    private fun processCommand(command: AutofillPasswordsManagementViewModel.ListModeCommand) {
        when (command) {
            LaunchResetNeverSaveListConfirmation -> launchResetNeverSavedSitesConfirmation()
            is LaunchDeleteAllPasswordsConfirmation -> launchDeleteAllLoginsConfirmationDialog(command.numberToDelete)
            is PromptUserToAuthenticateMassDeletion -> promptUserToAuthenticateMassDeletion(command.authConfiguration)
            is LaunchImportGooglePasswords -> launchImportPasswordsScreen(showInitialInstructionalPrompt = command.showImportInstructions)
            is LaunchReportAutofillBreakageConfirmation -> launchReportBreakageConfirmation(command.eTldPlusOne)
            is ShowUserReportSentMessage -> showUserReportSentMessage()
            is ReevalutePromotions -> evaluatePromotions()
        }
        viewModel.commandProcessed(command)
    }

    private fun evaluatePromotions() {
        lifecycleScope.launch {
            updateWithViewState(viewModel.viewState.value)
        }
    }

    private fun showUserReportSentMessage() {
        Snackbar.make(binding.root, R.string.autofillManagementReportBreakageSuccessMessage, Snackbar.LENGTH_LONG).show()
    }

    private fun launchImportPasswordsScreen(showInitialInstructionalPrompt: Boolean) {
        context?.let {
            val dialog = ImportFromGooglePasswordsDialog.instance(showInitialInstructionalPrompt = showInitialInstructionalPrompt)
            dialog.show(parentFragmentManager, IMPORT_FROM_GPM_DIALOG_TAG)
        }
    }

    private fun launchImportPasswordsFromDesktopSyncScreen() {
        context?.let {
            globalActivityStarter.start(it, ImportPasswordActivityParams)
        }
    }

    private fun launchReportBreakageConfirmation(eTldPlusOne: String) {
        this.context?.let {
            lifecycleScope.launch(dispatchers.io()) {
                val dialogTitle = getString(R.string.autofillManagementReportBreakageDialogTitle, eTldPlusOne)
                val dialogMessage = getString(R.string.autofillManagementReportBreakageDialogMessage)

                withContext(dispatchers.main()) {
                    TextAlertDialogBuilder(it)
                        .setTitle(dialogTitle)
                        .setMessage(dialogMessage)
                        .setPositiveButton(R.string.autofillManagementReportBreakageDialogPositiveButton)
                        .setNegativeButton(R.string.autofillDeleteLoginDialogCancel)
                        .setCancellable(true)
                        .addEventListener(
                            object : TextAlertDialogBuilder.EventListener() {
                                override fun onPositiveButtonClicked() {
                                    viewModel.userConfirmedSendBreakageReport()
                                }

                                override fun onNegativeButtonClicked() {
                                    viewModel.userCancelledSendBreakageReport()
                                }

                                override fun onDialogCancelled() {
                                    viewModel.userCancelledSendBreakageReport()
                                }
                            },
                        )
                        .show()
                }
            }
        }
    }

    private suspend fun credentialsListUpdated(
        credentials: List<LoginCredentials>?,
        credentialSearchQuery: String,
        allowBreakageReporting: Boolean,
        canShowImportGooglePasswordsButton: Boolean,
        showAutofillToggle: Boolean,
        promotionView: View?,
    ) {
        if (credentials == null) {
            logcat(VERBOSE) { "Credentials is null, meaning we haven't retrieved them yet. Don't know if empty or not yet" }
            renderCredentialList(
                credentials = null,
                allowBreakageReporting = allowBreakageReporting,
                showAutofillToggle = showAutofillToggle,
                autofillEnabled = viewModel.viewState.value.autofillEnabled,
                promotionView = promotionView,
                showGoogleImportPasswordsButton = canShowImportGooglePasswordsButton,
            )
        } else if (credentials.isEmpty() && credentialSearchQuery.isEmpty()) {
            showEmptyCredentialsPlaceholders(
                canShowImportGooglePasswordsButton = canShowImportGooglePasswordsButton,
                showAutofillToggle = showAutofillToggle,
                promotionView = promotionView,
            )
        } else if (credentials.isEmpty()) {
            showNoResultsPlaceholders(credentialSearchQuery)
        } else {
            renderCredentialList(
                credentials = credentials,
                allowBreakageReporting = allowBreakageReporting,
                showAutofillToggle = showAutofillToggle,
                autofillEnabled = viewModel.viewState.value.autofillEnabled,
                promotionView = promotionView,
                showGoogleImportPasswordsButton = canShowImportGooglePasswordsButton,
            )
        }
    }

    private fun showNoResultsPlaceholders(query: String) {
        adapter.showNoMatchingSearchResults(query)
    }

    private suspend fun showEmptyCredentialsPlaceholders(
        canShowImportGooglePasswordsButton: Boolean,
        showAutofillToggle: Boolean,
        promotionView: View?,
    ) {
        renderCredentialList(
            credentials = emptyList(),
            allowBreakageReporting = false,
            showAutofillToggle = showAutofillToggle,
            autofillEnabled = viewModel.viewState.value.autofillEnabled,
            promotionView = promotionView,
            showGoogleImportPasswordsButton = canShowImportGooglePasswordsButton,
        )

        if (canShowImportGooglePasswordsButton) {
            viewModel.recordImportGooglePasswordButtonShown()
        }
    }

    private suspend fun renderCredentialList(
        credentials: List<LoginCredentials>?,
        allowBreakageReporting: Boolean,
        showAutofillToggle: Boolean,
        autofillEnabled: Boolean,
        promotionView: View?,
        showGoogleImportPasswordsButton: Boolean,
    ) {
        withContext(dispatchers.io()) {
            val currentUrl = getCurrentSiteUrl()

            val credentialLoadingState = if (credentials == null) {
                Loading
            } else {
                val directSuggestions = suggestionMatcher.getDirectSuggestions(currentUrl, credentials)
                val shareableCredentials = suggestionMatcher.getShareableSuggestions(currentUrl)
                val directSuggestionsListItems = suggestionListBuilder.build(directSuggestions, shareableCredentials, allowBreakageReporting)
                val groupedCredentials = grouper.group(credentials)

                val hasSuggestions = directSuggestions.isNotEmpty() || shareableCredentials.isNotEmpty()
                if (allowBreakageReporting && hasSuggestions) {
                    viewModel.onReportBreakageShown()
                }

                Loaded(
                    directSuggestionsListItems = directSuggestionsListItems,
                    groupedCredentials = groupedCredentials,
                    showGoogleImportPasswordsButton = showGoogleImportPasswordsButton,
                )
            }

            withContext(dispatchers.main()) {
                adapter.showLogins(
                    autofillToggleState = AutofillToggleState(enabled = autofillEnabled, visible = showAutofillToggle),
                    credentialsLoadedState = credentialLoadingState,
                    promotionView = promotionView,
                )
            }
        }
    }

    private fun configureRecyclerView() {
        adapter = AutofillManagementRecyclerAdapter(
            this,
            faviconManager = faviconManager,
            initialExtractor = initialExtractor,
            onCredentialSelected = this::onCredentialsSelected,
            onContextMenuItemClicked = {
                when (it) {
                    is Edit -> viewModel.onEditCredentials(it.credentials)
                    is Delete -> launchDeleteLoginConfirmationDialog(it.credentials)
                    is CopyUsername -> onCopyUsername(it.credentials)
                    is CopyPassword -> onCopyPassword(it.credentials)
                }
            },
            onReportBreakageClicked = { viewModel.onReportBreakageClicked() },
            launchHelpPageClicked = this::launchHelpPage,
            onAutofillToggleClicked = this::onAutofillToggledChanged,
            onImportFromGoogleClicked = this::onImportFromGoogleClicked,
            onImportViaDesktopSyncClicked = this::onImportViaDesktopSyncClicked,
        ).also { binding.logins.adapter = it }
    }

    private fun onAutofillToggledChanged(isChecked: Boolean) {
        if (isChecked) {
            viewModel.onEnableAutofill()
        } else {
            viewModel.onDisableAutofill(getAutofillSettingsLaunchSource())
        }
    }

    private fun onImportFromGoogleClicked() {
        viewModel.onImportPasswordsFromGooglePasswordManager()
        importPasswordsPixelSender.onImportPasswordsButtonTapped(PasswordManagementEmpty)
    }

    private fun onImportViaDesktopSyncClicked() {
        launchImportPasswordsFromDesktopSyncScreen()
        importPasswordsPixelSender.onImportPasswordsViaDesktopSyncButtonTapped()
    }

    private fun launchDeleteLoginConfirmationDialog(loginCredentials: LoginCredentials) {
        this.context?.let {
            lifecycleScope.launch(dispatchers.io()) {
                val dialogTitle = stringBuilder.stringForDeletePasswordDialogConfirmationTitle(numberToDelete = 1)
                val dialogMessage = stringBuilder.stringForDeletePasswordDialogConfirmationMessage(numberToDelete = 1)

                withContext(dispatchers.main()) {
                    TextAlertDialogBuilder(it)
                        .setTitle(dialogTitle)
                        .setMessage(dialogMessage)
                        .setPositiveButton(R.string.autofillDeleteLoginDialogDelete, DESTRUCTIVE)
                        .setNegativeButton(R.string.autofillDeleteLoginDialogCancel, GHOST_ALT)
                        .addEventListener(
                            object : TextAlertDialogBuilder.EventListener() {
                                override fun onPositiveButtonClicked() {
                                    viewModel.onDeleteCredentials(loginCredentials)
                                }
                            },
                        )
                        .show()
                }
            }
        }
    }

    private fun launchDeleteAllLoginsConfirmationDialog(numberToDelete: Int) {
        this.context?.let {
            lifecycleScope.launch(dispatchers.io()) {
                val dialogTitle = stringBuilder.stringForDeletePasswordDialogConfirmationTitle(numberToDelete)
                val dialogMessage = stringBuilder.stringForDeletePasswordDialogConfirmationMessage(numberToDelete)

                withContext(dispatchers.main()) {
                    TextAlertDialogBuilder(it)
                        .setTitle(dialogTitle)
                        .setMessage(dialogMessage)
                        .setPositiveButton(R.string.autofillDeleteLoginDialogDelete, DESTRUCTIVE)
                        .setNegativeButton(R.string.autofillDeleteLoginDialogCancel, GHOST_ALT)
                        .setCancellable(true)
                        .addEventListener(
                            object : TextAlertDialogBuilder.EventListener() {
                                override fun onPositiveButtonClicked() {
                                    viewModel.onDeleteAllPasswordsConfirmed()
                                }
                            },
                        )
                        .show()
                }
            }
        }
    }

    private fun promptUserToAuthenticateMassDeletion(authConfiguration: AuthConfiguration) {
        deviceAuthenticator.authenticate(this, config = authConfiguration) {
            when (it) {
                Success -> viewModel.onAuthenticatedToDeleteAllPasswords()
                else -> {}
            }
        }
    }

    private fun launchResetNeverSavedSitesConfirmation() {
        this.context?.let {
            TextAlertDialogBuilder(it)
                .setTitle(R.string.credentialManagementClearNeverForThisSiteDialogTitle)
                .setMessage(R.string.credentialManagementInstructionNeverForThisSite)
                .setPositiveButton(R.string.credentialManagementClearNeverForThisSiteDialogPositiveButton, DESTRUCTIVE)
                .setNegativeButton(R.string.credentialManagementClearNeverForThisSiteDialogNegativeButton, GHOST_ALT)
                .setCancellable(true)
                .addEventListener(
                    object : TextAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked() {
                            viewModel.onUserConfirmationToClearNeverSavedSites()
                        }

                        override fun onNegativeButtonClicked() {
                            viewModel.onUserCancelledFromClearNeverSavedSitesPrompt()
                        }

                        override fun onDialogCancelled() {
                            viewModel.onUserCancelledFromClearNeverSavedSitesPrompt()
                        }
                    },
                )
                .show()
        }
    }

    private fun onCredentialsSelected(credentials: LoginCredentials) {
        viewModel.onViewCredentials(credentials)
    }

    private fun onCopyUsername(credentials: LoginCredentials) {
        viewModel.onCopyUsername(credentials.username)
    }

    private fun onCopyPassword(credentials: LoginCredentials) {
        viewModel.onCopyPassword(credentials.password)
    }

    companion object {
        fun instance(
            currentUrl: String? = null,
            privacyProtectionEnabled: Boolean?,
            source: AutofillScreenLaunchSource? = null,
        ) =
            AutofillManagementListMode().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENT_URL, currentUrl)

                    if (privacyProtectionEnabled != null) {
                        putBoolean(ARG_PRIVACY_PROTECTION_STATUS, privacyProtectionEnabled)
                    }

                    if (source != null) {
                        putSerializable(ARG_AUTOFILL_SETTINGS_LAUNCH_SOURCE, source)
                    }
                }
            }

        private const val ARG_CURRENT_URL = "ARG_CURRENT_URL"
        private const val ARG_PRIVACY_PROTECTION_STATUS = "ARG_PRIVACY_PROTECTION_STATUS"
        private const val ARG_AUTOFILL_SETTINGS_LAUNCH_SOURCE = "ARG_AUTOFILL_SETTINGS_LAUNCH_SOURCE"
        private const val LEARN_MORE_LINK = "https://duckduckgo.com/duckduckgo-help-pages/sync-and-backup/password-manager-security/"
        private const val IMPORT_FROM_GPM_DIALOG_TAG = "IMPORT_FROM_GPM_DIALOG_TAG"
    }
}
