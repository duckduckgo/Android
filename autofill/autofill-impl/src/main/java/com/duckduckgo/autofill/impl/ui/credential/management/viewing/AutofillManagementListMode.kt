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
import android.widget.CompoundButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.MenuProvider
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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementListModeBinding
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthConfiguration
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_CTA_BUTTON
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_OVERFLOW_MENU
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementActivity
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyPassword
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyUsername
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Delete
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Edit
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchDeleteAllPasswordsConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchImportPasswords
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchResetNeverSaveListConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.PromptUserToAuthenticateMassDeletion
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordActivityParams
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialGrouper
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.InitialExtractor
import com.duckduckgo.autofill.impl.ui.credential.management.suggestion.SuggestionListBuilder
import com.duckduckgo.autofill.impl.ui.credential.management.suggestion.SuggestionMatcher
import com.duckduckgo.autofill.impl.ui.credential.management.survey.SurveyDetails
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.SearchBar
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@InjectWith(FragmentScope::class)
class AutofillManagementListMode : DuckDuckGoFragment(R.layout.fragment_autofill_management_list_mode) {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var browserNav: BrowserNav

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
    lateinit var pixel: Pixel

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillSettingsViewModel::class.java]
    }

    private val binding: FragmentAutofillManagementListModeBinding by viewBinding()
    private lateinit var adapter: AutofillManagementRecyclerAdapter

    private var searchMenuItem: MenuItem? = null
    private var resetNeverSavedSitesMenuItem: MenuItem? = null
    private var deleteAllPasswordsMenuItem: MenuItem? = null
    private var importPasswordsMenuItem: MenuItem? = null

    private val globalAutofillToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@OnCheckedChangeListener
        if (isChecked) viewModel.onEnableAutofill() else viewModel.onDisableAutofill()
    }

    private fun configureToggle() {
        binding.enabledToggle.setOnCheckedChangeListener(globalAutofillToggleListener)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        configureToggle()
        configureRecyclerView()
        configureImportPasswordsButton()
        observeViewModel()
        configureToolbar()
    }

    override fun onStop() {
        super.onStop()
        hideSearchBar()
    }

    private fun configureImportPasswordsButton() {
        binding.emptyStateLayout.importPasswordsButton.setOnClickListener {
            viewModel.onImportPasswords()
            pixel.fire(AUTOFILL_IMPORT_PASSWORDS_CTA_BUTTON)
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
                    importPasswordsMenuItem = menu.findItem(R.id.importPasswords)

                    initializeSearchBar()
                }

                override fun onPrepareMenu(menu: Menu) {
                    val loginsSaved = !viewModel.viewState.value.logins.isNullOrEmpty()
                    searchMenuItem?.isVisible = loginsSaved
                    deleteAllPasswordsMenuItem?.isVisible = loginsSaved
                    resetNeverSavedSitesMenuItem?.isVisible = viewModel.neverSavedSitesViewState.value.showOptionToReset
                    importPasswordsMenuItem?.isVisible = loginsSaved
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

                        R.id.importPasswords -> {
                            viewModel.onImportPasswords()
                            pixel.fire(AUTOFILL_IMPORT_PASSWORDS_OVERFLOW_MENU)
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

    private fun getCurrentUrlForSuggestions() = arguments?.getString(ARG_CURRENT_URL, null)

    private fun parentBinding() = parentActivity()?.binding
    private fun parentActivity() = (activity as AutofillManagementActivity?)

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { state ->
                    binding.enabledToggle.quietlySetIsChecked(state.autofillEnabled, globalAutofillToggleListener)
                    state.logins?.let {
                        credentialsListUpdated(it, state.credentialSearchQuery)
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

                    if (state.survey == null) {
                        hideSurvey()
                    } else {
                        showSurvey(state.survey)
                    }
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

        viewModel.onViewCreated()
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
            LaunchImportPasswords -> launchImportPasswordsScreen()
        }
        viewModel.commandProcessed(command)
    }

    private fun hideSurvey() {
        binding.autofillSurveyMessage.gone()
    }

    private fun showSurvey(survey: SurveyDetails) {
        with(binding.autofillSurveyMessage) {
            setMessage(
                Message(
                    topIllustration = R.drawable.ic_passwords_ddg_96,
                    title = getString(R.string.autofillManagementSurveyPromptTitle),
                    subtitle = getString(R.string.autofillManagementSurveyPromptMessage),
                    action = getString(R.string.autofillManagementSurveyPromptAcceptButtonText),
                ),
            )
            onPrimaryActionClicked {
                startActivity(browserNav.openInNewTab(binding.root.context, survey.url))
                viewModel.onSurveyShown(survey.id)
            }
            onCloseButtonClicked {
                viewModel.onSurveyPromptDismissed(survey.id)
            }
            show()
        }
    }

    private fun launchImportPasswordsScreen() {
        context?.let {
            globalActivityStarter.start(it, ImportPasswordActivityParams)
        }
    }

    private suspend fun credentialsListUpdated(
        credentials: List<LoginCredentials>,
        credentialSearchQuery: String,
    ) {
        if (credentials.isEmpty() && credentialSearchQuery.isEmpty()) {
            showEmptyCredentialsPlaceholders()
        } else if (credentials.isEmpty()) {
            showNoResultsPlaceholders(credentialSearchQuery)
        } else {
            renderCredentialList(credentials)
        }
    }

    private fun showNoResultsPlaceholders(query: String) {
        binding.emptyStateLayout.emptyStateContainer.gone()
        binding.logins.show()
        adapter.showNoMatchingSearchResults(query)
    }

    private fun showEmptyCredentialsPlaceholders() {
        binding.emptyStateLayout.emptyStateContainer.show()

        binding.logins.gone()
    }

    private suspend fun renderCredentialList(credentials: List<LoginCredentials>) {
        binding.emptyStateLayout.emptyStateContainer.gone()
        binding.logins.show()

        val currentUrl = getCurrentUrlForSuggestions()
        val directSuggestions = suggestionMatcher.getDirectSuggestions(currentUrl, credentials)
        val shareableCredentials = suggestionMatcher.getShareableSuggestions(currentUrl)

        adapter.updateLogins(credentials, directSuggestions, shareableCredentials)
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
                        .setDestructiveButtons(true)
                        .setPositiveButton(R.string.autofillDeleteLoginDialogDelete)
                        .setNegativeButton(R.string.autofillDeleteLoginDialogCancel)
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
                        .setDestructiveButtons(true)
                        .setPositiveButton(R.string.autofillDeleteLoginDialogDelete)
                        .setNegativeButton(R.string.autofillDeleteLoginDialogCancel)
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
                .setDestructiveButtons(true)
                .setPositiveButton(R.string.credentialManagementClearNeverForThisSiteDialogPositiveButton)
                .setNegativeButton(R.string.credentialManagementClearNeverForThisSiteDialogNegativeButton)
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
        fun instance(currentUrl: String? = null) =
            AutofillManagementListMode().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENT_URL, currentUrl)
                }
            }

        private const val ARG_CURRENT_URL = "ARG_CURRENT_URL"
    }
}

private fun RecyclerView.updateTopMargin(marginPx: Int) {
    updateLayoutParams<ConstraintLayout.LayoutParams> { this.updateMargins(top = marginPx) }
}
