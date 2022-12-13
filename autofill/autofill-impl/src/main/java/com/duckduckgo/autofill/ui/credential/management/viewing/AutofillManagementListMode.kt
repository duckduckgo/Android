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

package com.duckduckgo.autofill.ui.credential.management.viewing

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DuckDuckGoFragment
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementListModeBinding
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyPassword
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyUsername
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Delete
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Edit
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel
import com.duckduckgo.autofill.ui.credential.management.LoginCredentialTitleExtractor
import com.duckduckgo.autofill.ui.credential.management.sorting.CredentialGrouper
import com.duckduckgo.autofill.ui.credential.management.suggestion.SuggestionListBuilder
import com.duckduckgo.autofill.ui.credential.management.suggestion.SuggestionMatcher
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@InjectWith(FragmentScope::class)
class AutofillManagementListMode : DuckDuckGoFragment(R.layout.fragment_autofill_management_list_mode) {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var credentialGrouper: CredentialGrouper

    @Inject
    lateinit var titleExtractor: LoginCredentialTitleExtractor

    @Inject
    lateinit var suggestionMatcher: SuggestionMatcher

    @Inject
    lateinit var suggestionListBuilder: SuggestionListBuilder

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillSettingsViewModel::class.java]
    }

    private val binding: FragmentAutofillManagementListModeBinding by viewBinding()
    private lateinit var adapter: AutofillManagementRecyclerAdapter

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
        observeViewModel()
        configureToolbar()
    }

    private fun configureToolbar() {
        activity?.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = menuInflater.inflate(R.menu.autofill_list_mode_menu, menu)

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.addLoginManually -> {
                            viewModel.onCreateNewCredentials()
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

    private fun getCurrentUrlForSuggestions() = arguments?.getString(ARG_CURRENT_URL, null)

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { state ->
                    binding.enabledToggle.quietlySetIsChecked(state.autofillEnabled, globalAutofillToggleListener)
                    state.logins?.let {
                        credentialsListUpdated(it)
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.commands.collect { commands ->
                    commands.forEach { processCommand(it) }
                }
            }
        }

        viewModel.observeCredentials()
    }

    private fun processCommand(command: AutofillSettingsViewModel.Command) {
        var processed = true
        when (command) {
            else -> processed = false
        }
        if (processed) {
            Timber.v("Processed command $command")
            viewModel.commandProcessed(command)
        }
    }

    private fun credentialsListUpdated(credentials: List<LoginCredentials>) {
        if (credentials.isEmpty()) {
            showEmptyCredentialsPlaceholders()
        } else {
            renderCredentialList(credentials)
        }
    }

    private fun showEmptyCredentialsPlaceholders() {
        binding.emptyStateLayout.emptyStateContainer.show()
        binding.logins.gone()
    }

    private fun renderCredentialList(credentials: List<LoginCredentials>) {
        binding.emptyStateLayout.emptyStateContainer.gone()
        binding.logins.show()

        val currentUrl = getCurrentUrlForSuggestions()
        val suggestions = suggestionMatcher.getSuggestions(currentUrl, credentials)

        adapter.updateLogins(credentials, suggestions)

        Timber.v("Current url: $currentUrl. Matching suggestions: %d", suggestions.size)
    }

    private fun configureRecyclerView() {
        adapter = AutofillManagementRecyclerAdapter(
            this,
            faviconManager = faviconManager,
            grouper = credentialGrouper,
            titleExtractor = titleExtractor,
            suggestionListBuilder = suggestionListBuilder,
            onCredentialSelected = this::onCredentialsSelected,
            onContextMenuItemClicked = {
                when (it) {
                    is Edit -> viewModel.onEditCredentials(it.credentials)
                    is Delete -> viewModel.onDeleteCredentials(it.credentials)
                    is CopyUsername -> onCopyUsername(it.credentials)
                    is CopyPassword -> onCopyPassword(it.credentials)
                }
            },
        ).also { binding.logins.adapter = it }
    }

    private fun onCredentialsSelected(credentials: LoginCredentials) {
        viewModel.onViewCredentials(credentials, false)
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
