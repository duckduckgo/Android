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
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialMode.Editing
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialMode.Viewing
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
        if (viewModel.viewState.value.credentialMode is Editing) {
            menu.findItem(R.id.view_menu_save).isVisible = true
            menu.findItem(R.id.view_menu_delete).isVisible = false
            menu.findItem(R.id.view_menu_edit).isVisible = false
        } else if (viewModel.viewState.value.credentialMode is Viewing) {
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
                viewModel.viewState.value.credentialMode.credentialsViewed?.let {
                    viewModel.onEditCredentials(it)
                }
                true
            }
            R.id.view_menu_delete -> {
                viewModel.viewState.value.credentialMode.credentialsViewed?.let {
                    viewModel.onDeleteCredentials(it)
                }
                viewModel.onExitViewMode()
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
        observeViewModel()
        configureUiEventHandlers()
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
        val updatedCredentials = LoginCredentials(
            username = binding.usernameEditText.text.convertBlankToNull(),
            password = binding.passwordEditText.text.convertBlankToNull(),
            domain = binding.domainEditText.text.convertBlankToNull(),
            domainTitle = binding.domainTitleEditText.text.convertBlankToNull(),
            notes = binding.notesEditText.text.convertBlankToNull()
        )
        viewModel.updateCredentials(updatedCredentials)
    }

    private fun populateFields(credentials: LoginCredentials) {
        loadDomainFavicon(credentials)
        binding.apply {
            domainTitleEditText.setText(credentials.domainTitle)
            usernameEditText.setText(credentials.username)
            passwordEditText.setText(credentials.password)
            domainEditText.setText(credentials.domain)
            notesEditText.setText(credentials.notes)
            credentials.lastUpdatedMillis?.let {
                lastUpdatedView.text = getString(R.string.credentialManagementEditLastUpdated, lastUpdatedDateFormatter.format(it))
            }
        }
    }

    private fun showViewMode(credentials: LoginCredentials) {
        updateToolbarForView(credentials)
        binding.apply {
            domainTitleEditText.visibility = View.GONE
            domainTitleEditText.isEditable = false
            usernameEditText.isEditable = false
            passwordEditText.isEditable = false
            domainEditText.isEditable = false
            notesEditText.isEditable = false
        }
    }

    private fun showEditMode() {
        updateToolbarForEdit()
        binding.apply {
            domainTitleEditText.visibility = View.VISIBLE
            domainTitleEditText.isEditable = true
            usernameEditText.isEditable = true
            passwordEditText.isEditable = true
            domainEditText.isEditable = true
            notesEditText.isEditable = true
        }
    }

    private fun OutLinedTextInputView.setText(text: String?) {
        this.text = text ?: ""
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { state ->
                    when (state.credentialMode) {
                        is Viewing -> {
                            populateFields(state.credentialMode.credentialsViewed)
                            showViewMode(state.credentialMode.credentialsViewed)
                        }
                        is Editing -> showEditMode()
                        else -> {
                        }
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

    private fun updateToolbarForView(credentials: LoginCredentials) {
        getActionBar()?.apply {
            setHomeAsUpIndicator(com.duckduckgo.mobile.android.R.drawable.ic_back_24)
            title = credentials.domainTitle ?: credentials.domain
            setDisplayUseLogoEnabled(true)
        }
        invalidateMenu()
    }

    private fun loadDomainFavicon(credentials: LoginCredentials) {
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

        fun instance() = AutofillManagementCredentialsMode()
    }
}
