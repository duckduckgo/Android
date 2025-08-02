/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.MenuProvider
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillProviderListBinding
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_PASSWORDS_SEARCH
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.AutofillToggleState
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.CredentialsLoadedState.Loaded
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialGrouper
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.InitialExtractor
import com.duckduckgo.autofill.impl.ui.credential.management.suggestion.SuggestionListBuilder
import com.duckduckgo.autofill.impl.ui.credential.management.suggestion.SuggestionMatcher
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.SearchBar
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.logcat

@InjectWith(FragmentScope::class)
class AutofillSimpleCredentialsListFragment : DuckDuckGoFragment(R.layout.fragment_autofill_provider_list) {

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
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var pixel: Pixel

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillProviderCredentialsListViewModel::class.java]
    }

    private val binding: FragmentAutofillProviderListBinding by viewBinding()
    private lateinit var adapter: AutofillManagementRecyclerAdapter

    private var searchMenuItem: MenuItem? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        configureRecyclerView()
        observeViewModel()
        configureToolbar()
        showSearchBar()
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
                    menuInflater.inflate(R.menu.autofill_simple_list_mode_menu, menu)
                    searchMenuItem = menu.findItem(R.id.searchLogins)

                    initializeSearchBar()
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false

                override fun onPrepareMenu(menu: Menu) {
                    val loginsSaved = !viewModel.viewState.value.logins.isNullOrEmpty()
                    searchMenuItem?.isVisible = loginsSaved
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    private fun initializeSearchBar() {
        searchMenuItem?.setOnMenuItemClickListener {
            pixel.fire(AUTOFILL_SERVICE_PASSWORDS_SEARCH)
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

    private fun getRequestedUrl() = arguments?.getString(ARG_CURRENT_URL, null)
    private fun getRequestedPackage() = arguments?.getString(ARG_CURRENT_PACKAGE, null)

    private fun parentBinding() = parentActivity()?.binding
    private fun parentActivity() = (activity as? AutofillProviderChooseActivity)

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { state ->
                    state.logins?.let {
                        credentialsListUpdated(
                            credentials = it,
                            credentialSearchQuery = state.credentialSearchQuery,
                        )
                        parentActivity()?.invalidateOptionsMenu()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect {
                    // we can just invalidate the menu as [onPrepareMenu] will handle the new visibility for importing passwords menu item
                    parentActivity()?.invalidateOptionsMenu()
                }
            }
        }

        viewModel.onViewCreated()
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

    private suspend fun renderCredentialList(
        credentials: List<LoginCredentials>,
    ) {
        binding.emptyStateLayout.emptyStateContainer.gone()
        binding.logins.show()

        withContext(dispatchers.io()) {
            val showSuggestionsFor = getRequestedUrl().takeUnless { it.isNullOrBlank() } ?: getRequestedPackage()
            logcat(INFO) { "DDGAutofillService showSuggestionsFor: $showSuggestionsFor" }
            val directSuggestions = suggestionMatcher.getDirectSuggestions(showSuggestionsFor, credentials)
            val shareableCredentials = suggestionMatcher.getShareableSuggestions(showSuggestionsFor)
            val directSuggestionsListItems = suggestionListBuilder.build(directSuggestions, shareableCredentials, allowBreakageReporting = false)
            val groupedCredentials = credentialGrouper.group(credentials)

            withContext(dispatchers.main()) {
                adapter.showLogins(
                    credentialsLoadedState = Loaded(
                        directSuggestionsListItems = directSuggestionsListItems,
                        groupedCredentials = groupedCredentials,
                        showGoogleImportPasswordsButton = false,
                    ),
                    autofillToggleState = AutofillToggleState(enabled = true, visible = false),
                    promotionView = null,
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
            onContextMenuItemClicked = { },
            onReportBreakageClicked = { },
            onImportFromGoogleClicked = { },
            onAutofillToggleClicked = { },
            onImportViaDesktopSyncClicked = { },
            launchHelpPageClicked = { },
        ).also { binding.logins.adapter = it }
    }

    private fun onCredentialsSelected(credentials: LoginCredentials) {
        viewModel.onCredentialSelected(credentials)
        parentActivity()?.autofillLogin(credentials) ?: run {
            activity?.finish()
        }
    }

    companion object {
        fun instance(
            url: String? = null,
            packageId: String? = null,
        ) = AutofillSimpleCredentialsListFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CURRENT_URL, url)
                putString(ARG_CURRENT_PACKAGE, packageId)
            }
        }

        private const val ARG_CURRENT_URL = "ARG_FILL_REQUEST_URL"
        private const val ARG_CURRENT_PACKAGE = "ARG_FILL_REQUEST_PACKAGE"
    }
}

private fun RecyclerView.updateTopMargin(marginPx: Int) {
    updateLayoutParams<ConstraintLayout.LayoutParams> { this.updateMargins(top = marginPx) }
}
