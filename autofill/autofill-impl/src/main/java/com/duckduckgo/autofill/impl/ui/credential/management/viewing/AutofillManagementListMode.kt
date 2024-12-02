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
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.toSpanned
import androidx.core.view.MenuProvider
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementListModeBinding
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthConfiguration
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementActivity
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyPassword
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyUsername
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Delete
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Edit
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchDeleteAllPasswordsConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchImportPasswordsFromGooglePasswordManager
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchReportAutofillBreakageConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchResetNeverSaveListConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.PromptUserToAuthenticateMassDeletion
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.ReevalutePromotions
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.ShowUserReportSentMessage
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ViewState
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
import com.duckduckgo.common.ui.view.addClickableLink
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.prependIconToText
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillSettingsViewModel::class.java]
    }

    private val syncActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.userReturnedFromSyncSettings()
    }

    private val binding: FragmentAutofillManagementListModeBinding by viewBinding()
    private lateinit var adapter: AutofillManagementRecyclerAdapter

    private var searchMenuItem: MenuItem? = null
    private var resetNeverSavedSitesMenuItem: MenuItem? = null
    private var deleteAllPasswordsMenuItem: MenuItem? = null
    private var syncDesktopPasswordsMenuItem: MenuItem? = null
    private var importGooglePasswordsMenuItem: MenuItem? = null

    private val globalAutofillToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@OnCheckedChangeListener
        if (isChecked) viewModel.onEnableAutofill() else viewModel.onDisableAutofill(getAutofillSettingsLaunchSource())
    }

    private fun configureToggle() {
        binding.enabledToggle.setOnCheckedChangeListener(globalAutofillToggleListener)
    }

    private fun configureInfoText() {
        binding.infoText.addClickableLink(
            annotation = "learn_more_link",
            textSequence = binding.root.context.prependIconToText(
                R.string.credentialManagementAutofillSubtitle,
                R.drawable.ic_lock_solid_12,
            ).toSpanned(),
            onClick = {
                launchHelpPage()
            },
        )
    }

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
        configureToggle()
        configureRecyclerView()
        configureImportPasswordsButton()
        configureCurrentSiteState()
        observeViewModel()
        configureToolbar()
        configureInfoText()
    }

    private fun configurePromotionsContainer() {
        lifecycleScope.launch(dispatchers.main()) {
            val state = viewModel.viewState.value

            if (!state.canShowPromo) {
                binding.promotionContainer.gone()
                return@launch
            }

            val promotionView = binding.promotionContainer.getFirstEligiblePromo(numberPasswords = state.logins?.size ?: 0)
            if (promotionView == null) {
                binding.promotionContainer.gone()
            } else {
                binding.promotionContainer.showPromotion(promotionView)
            }
        }
    }

    private fun ViewGroup.showPromotion(promotionView: View) {
        val alreadyShowing = if (this.childCount == 0) {
            false
        } else {
            (promotionView::class.qualifiedName == this.children.first()::class.qualifiedName) && (promotionView.tag == this.children.first().tag)
        }

        if (!alreadyShowing) {
            this.removeAllViews()
            this.addView(promotionView)
        }

        this.show()
    }

    private suspend fun ViewGroup.getFirstEligiblePromo(numberPasswords: Int): View? {
        val context = this.context ?: return null
        return screenPromotionPlugins.getPlugins().firstNotNullOfOrNull { it.getView(context, numberPasswords) }
    }

    private fun configureCurrentSiteState() {
        viewModel.updateCurrentSite(getCurrentSiteUrl(), getPrivacyProtectionEnabled())
    }

    override fun onStop() {
        super.onStop()
        hideSearchBar()
    }

    private fun configureImportPasswordsButton() {
        binding.emptyStateLayout.importPasswordsFromGoogleButton.setOnClickListener {
            viewModel.onImportPasswordsFromGooglePasswordManager()
            importPasswordsPixelSender.onImportPasswordsButtonTapped()
        }

        binding.emptyStateLayout.importPasswordsViaDesktopSyncButton.setOnClickListener {
            launchImportPasswordsFromDesktopSyncScreen()
            importPasswordsPixelSender.onImportPasswordsViaDesktopSyncButtonTapped()
        }
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
            Lifecycle.State.RESUMED,
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
    private fun getAutofillSettingsLaunchSource(): AutofillSettingsLaunchSource? =
        arguments?.getSerializable(ARG_AUTOFILL_SETTINGS_LAUNCH_SOURCE) as AutofillSettingsLaunchSource?

    private fun parentBinding() = parentActivity()?.binding
    private fun parentActivity() = (activity as AutofillManagementActivity?)

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { state ->
                    binding.enabledToggle.quietlySetIsChecked(state.autofillEnabled, globalAutofillToggleListener)
                    state.logins?.let {
                        credentialsListUpdated(
                            credentials = it,
                            credentialSearchQuery = state.credentialSearchQuery,
                            allowBreakageReporting = state.reportBreakageState.allowBreakageReporting,
                            canShowImportGooglePasswordsButton = state.canImportFromGooglePasswords,
                        )
                        parentActivity()?.invalidateOptionsMenu()
                    }

                    val resources = binding.logins.context.resources
                    if (state.showAutofillEnabledToggle) {
                        binding.credentialToggleGroup.show()
                        binding.logins.updateTopMargin(resources.getDimensionPixelSize(CommonR.dimen.keyline_empty))
                    } else {
                        binding.credentialToggleGroup.gone()
                        binding.logins.updateTopMargin(resources.getDimensionPixelSize(CommonR.dimen.keyline_4))
                    }

                    configurePromotionsContainer()
                }
            }
        }
        observeListModeViewModelCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.neverSavedSitesViewState.collect {
                    // we can just invalidate the menu as [onPrepareMenu] will handle the new visibility for resetting never saved sites menu item
                    parentActivity()?.invalidateOptionsMenu()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect {
                    // we can just invalidate the menu as [onPrepareMenu] will handle the new visibility for importing passwords menu item
                    parentActivity()?.invalidateOptionsMenu()

                    configureImportPasswordsButtonVisibility(it)
                }
            }
        }

        viewModel.onViewCreated()
    }

    private fun configureImportPasswordsButtonVisibility(state: ViewState) {
        if (state.canImportFromGooglePasswords) {
            binding.emptyStateLayout.importPasswordsFromGoogleButton.show()
        } else {
            binding.emptyStateLayout.importPasswordsFromGoogleButton.gone()
        }
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

    private fun processCommand(command: AutofillSettingsViewModel.ListModeCommand) {
        when (command) {
            LaunchResetNeverSaveListConfirmation -> launchResetNeverSavedSitesConfirmation()
            is LaunchDeleteAllPasswordsConfirmation -> launchDeleteAllLoginsConfirmationDialog(command.numberToDelete)
            is PromptUserToAuthenticateMassDeletion -> promptUserToAuthenticateMassDeletion(command.authConfiguration)
            is LaunchImportPasswordsFromGooglePasswordManager -> launchImportPasswordsScreen()
            is LaunchReportAutofillBreakageConfirmation -> launchReportBreakageConfirmation(command.eTldPlusOne)
            is ShowUserReportSentMessage -> showUserReportSentMessage()
            is ReevalutePromotions -> configurePromotionsContainer()
        }
        viewModel.commandProcessed(command)
    }

    private fun showUserReportSentMessage() {
        Snackbar.make(binding.root, R.string.autofillManagementReportBreakageSuccessMessage, Snackbar.LENGTH_LONG).show()
    }

    private fun launchImportPasswordsScreen() {
        context?.let {
            val dialog = ImportFromGooglePasswordsDialog.instance()
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
        credentials: List<LoginCredentials>,
        credentialSearchQuery: String,
        allowBreakageReporting: Boolean,
        canShowImportGooglePasswordsButton: Boolean,
    ) {
        if (credentials.isEmpty() && credentialSearchQuery.isEmpty()) {
            showEmptyCredentialsPlaceholders(canShowImportGooglePasswordsButton)
        } else if (credentials.isEmpty()) {
            showNoResultsPlaceholders(credentialSearchQuery)
        } else {
            renderCredentialList(credentials, allowBreakageReporting)
        }
    }

    private fun showNoResultsPlaceholders(query: String) {
        binding.emptyStateLayout.emptyStateContainer.gone()
        binding.logins.show()
        adapter.showNoMatchingSearchResults(query)
    }

    private fun showEmptyCredentialsPlaceholders(canShowImportGooglePasswordsButton: Boolean) {
        binding.emptyStateLayout.emptyStateContainer.show()
        binding.logins.gone()
        if (canShowImportGooglePasswordsButton) {
            viewModel.recordImportGooglePasswordButtonShown()
        }
    }

    private suspend fun renderCredentialList(
        credentials: List<LoginCredentials>,
        allowBreakageReporting: Boolean,
    ) {
        binding.emptyStateLayout.emptyStateContainer.gone()
        binding.logins.show()

        withContext(dispatchers.io()) {
            val currentUrl = getCurrentSiteUrl()
            val directSuggestions = suggestionMatcher.getDirectSuggestions(currentUrl, credentials)
            val shareableCredentials = suggestionMatcher.getShareableSuggestions(currentUrl)

            adapter.updateLogins(credentials, directSuggestions, shareableCredentials, allowBreakageReporting)

            val hasSuggestions = directSuggestions.isNotEmpty() || shareableCredentials.isNotEmpty()
            if (allowBreakageReporting && hasSuggestions) {
                viewModel.onReportBreakageShown()
            }
        }
    }

    private fun configureRecyclerView() {
        adapter = AutofillManagementRecyclerAdapter(
            this,
            dispatchers = dispatchers,
            faviconManager = faviconManager,
            grouper = credentialGrouper,
            initialExtractor = initialExtractor,
            suggestionListBuilder = suggestionListBuilder,
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
        ).also { binding.logins.adapter = it }
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
            source: AutofillSettingsLaunchSource? = null,
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

private fun RecyclerView.updateTopMargin(marginPx: Int) {
    updateLayoutParams<ConstraintLayout.LayoutParams> { this.updateMargins(top = marginPx) }
}
