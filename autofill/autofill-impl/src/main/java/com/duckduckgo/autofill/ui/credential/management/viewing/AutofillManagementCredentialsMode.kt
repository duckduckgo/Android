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

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementEditModeBinding
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialModeState.Editing
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialModeState.Viewing
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.OutLinedTextInputView
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class AutofillManagementCredentialsMode : Fragment(), MenuProvider {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var lastUpdatedDateFormatter: LastUpdatedDateFormatter

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillSettingsViewModel::class.java]
    }

    private lateinit var binding: FragmentAutofillManagementEditModeBinding
    private lateinit var credentials: LoginCredentials

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAutofillManagementEditModeBinding.inflate(inflater, container, false)
        requireActivity().addMenuProvider(this)
        return binding.root
    }

    override fun onPrepareMenu(menu: Menu) {
        if (viewModel.viewState.value.credentialModeState == Editing) {
            menu.findItem(R.id.view_menu_save).isVisible = true
            menu.findItem(R.id.view_menu_delete).isVisible = false
            menu.findItem(R.id.view_menu_edit).isVisible = false
        } else if (viewModel.viewState.value.credentialModeState == Viewing) {
            menu.findItem(R.id.view_menu_save).isVisible = false
            menu.findItem(R.id.view_menu_delete).isVisible = true
            menu.findItem(R.id.view_menu_edit).isVisible = true
        }
    }

    override fun onCreateMenu(
        menu: Menu,
        menuInflater: MenuInflater
    ) {
        menuInflater.inflate(R.menu.autofill_view_mode_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.view_menu_edit -> {
                viewModel.onEditCredentials()
                true
            }
            R.id.view_menu_delete -> {
                viewModel.onDeleteCredentials(credentials)
                true
            }
            R.id.view_menu_save -> {
                saveCredentials()
                true
            }
            else -> false
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        credentials = requireArguments().getParcelable(EXTRA_KEY_CREDENTIALS)!!
        observeViewModel()
        populateFields()
        configureUiEventHandlers()
        loadDomainFavicon()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        resetToolbarOnExit()
    }

    private fun resetToolbarOnExit() {
        getActionBar()?.apply {
            title = getString(R.string.managementScreenTitle)
            setDisplayUseLogoEnabled(false)
        }

        requireActivity().removeMenuProvider(this)
    }

    private fun configureUiEventHandlers() {
        binding.usernameEditText.onAction {
            viewModel.onCopyUsername(binding.usernameEditText.text)
        }
        binding.passwordEditText.onAction {
            viewModel.onCopyPassword(binding.passwordEditText.text)
        }
    }

    private fun saveCredentials() {
        val updatedCredentials = credentials.copy(
            username = binding.usernameEditText.text.convertBlankToNull(),
            password = binding.passwordEditText.text.convertBlankToNull(),
        )
        viewModel.updateCredentials(updatedCredentials)
        viewModel.onExitEditMode()
    }

    private fun populateFields() {
        binding.apply {
            usernameEditText.setText(credentials.username)
            passwordEditText.setText(credentials.password)
            domainEditText.setText(credentials.domain)
            notesEditText.setText(credentials.notes)
            credentials.lastUpdatedMillis?.let {
                lastUpdatedView.text = getString(R.string.credentialManagementEditLastUpdated, lastUpdatedDateFormatter.format(it))
            }
        }
    }

    private fun showViewMode() {
        updateToolbarForView()
        binding.apply {
            usernameEditText.isEditable = false
            passwordEditText.isEditable = false
            domainEditText.isEditable = false
            notesEditText.isEditable = false
        }
    }

    private fun showEditMode() {
        updateToolbarForEdit()
        binding.apply {
            usernameEditText.isEditable = true
            passwordEditText.isEditable = true
            domainEditText.isEditable = true
            notesEditText.isEditable = true
        }
    }

    private fun OutLinedTextInputView.setText(text: String?) {
        text?.let { this.text = it }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { state ->
                    if (state.credentialModeState == Viewing) {
                        showViewMode()
                    } else if (state.credentialModeState == Editing) {
                        showEditMode()
                    }
                }
            }
        }
    }

    private fun String.convertBlankToNull(): String? = this.ifBlank { null }

    private fun updateToolbarForEdit() {
        getActionBar()?.apply {
            setHomeAsUpIndicator(com.duckduckgo.mobile.android.R.drawable.ic_close)
            title = getString(R.string.credentialManagementEditTitle)
            setDisplayUseLogoEnabled(false)
        }
        invalidateMenu()
    }

    private fun updateToolbarForView() {
        getActionBar()?.apply {
            setHomeAsUpIndicator(com.duckduckgo.mobile.android.R.drawable.ic_back_24)
            title = credentials.domainTitle ?: credentials.domain
            setDisplayUseLogoEnabled(true)
        }
        invalidateMenu()
    }

    private fun loadDomainFavicon() {
        lifecycleScope.launch {
            credentials.domain?.let {
                getActionBar()?.setLogo(
                    BitmapDrawable(
                        resources,
                        faviconManager.loadFromDiskWithParams(
                            tabId = null,
                            url = it,
                            width = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.toolbarIconSize),
                            height = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.toolbarIconSize),
                            cornerRadius = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_0),
                        )
                    )
                )
            }
        }
    }

    private fun getActionBar(): ActionBar? = (activity as AppCompatActivity).supportActionBar
    private fun invalidateMenu() = (activity as AppCompatActivity).invalidateMenu()

    companion object {

        private const val EXTRA_KEY_CREDENTIALS = "credentials"

        fun instance(credentials: LoginCredentials) =
            AutofillManagementCredentialsMode().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_KEY_CREDENTIALS, credentials)
                }
            }
    }
}
