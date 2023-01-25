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

import android.graphics.drawable.BitmapDrawable
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DuckDuckGoFragment
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementEditModeBinding
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.Editing
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.EditingExisting
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.EditingNewEntry
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.Viewing
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialModeCommand
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialModeCommand.ShowEditCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialModeCommand.ShowManualCredentialMode
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.R.dimen
import com.duckduckgo.mobile.android.ui.view.text.DaxTextInput
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

@InjectWith(FragmentScope::class)
class AutofillManagementCredentialsMode : DuckDuckGoFragment(R.layout.fragment_autofill_management_edit_mode), MenuProvider {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var lastUpdatedDateFormatter: LastUpdatedDateFormatter

    @Inject
    lateinit var saveStateWatcher: SaveStateWatcher

    // we need to revert the toolbar title when this fragment is destroyed, so will track its initial value
    private var initialActionBarTitle: String? = null

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillSettingsViewModel::class.java]
    }

    private val binding: FragmentAutofillManagementEditModeBinding by viewBinding()

    override fun onPrepareMenu(menu: Menu) {
        var saveButtonVisible = false
        var deleteButtonVisible = false
        var editButtonVisible = false
        when (viewModel.viewState.value.credentialMode) {
            is Editing -> {
                saveButtonVisible = (viewModel.viewState.value.credentialMode as Editing).saveable
            }

            is Viewing -> {
                deleteButtonVisible = true
                editButtonVisible = true
            }

            else -> {}
        }
        menu.findItem(R.id.view_menu_save).isVisible = saveButtonVisible
        menu.findItem(R.id.view_menu_delete).isVisible = deleteButtonVisible
        menu.findItem(R.id.view_menu_edit).isVisible = editButtonVisible
    }

    override fun onCreateMenu(
        menu: Menu,
        menuInflater: MenuInflater,
    ) {
        menuInflater.inflate(R.menu.autofill_view_mode_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.view_menu_edit -> {
                viewModel.onEditCurrentCredentials()
                true
            }

            R.id.view_menu_delete -> {
                viewModel.onDeleteCurrentCredentials()
                viewModel.onExitCredentialMode()
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
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this)
        observeViewModel()
        configureUiEventHandlers()
        disableSystemAutofillServiceOnPasswordField()
        initialiseToolbar()
    }

    private fun startEditTextWatchers() {
        val initialState = binding.currentTextState()
        viewModel.allowSaveInEditMode(false)
        binding.watchSaveState(saveStateWatcher) {
            val currentState = binding.currentTextState()

            val changed = currentState != initialState
            val empty = currentState.isEmpty()

            viewModel.allowSaveInEditMode(!empty && changed)
        }
    }

    private fun stopEditTextWatchers() {
        binding.removeSaveStateWatcher(saveStateWatcher)
    }

    private fun initialiseTextWatchers() {
        stopEditTextWatchers()
        startEditTextWatchers()
    }

    private fun initialiseToolbar() {
        activity?.findViewById<Toolbar>(com.duckduckgo.mobile.android.R.id.toolbar)?.apply {
            initialActionBarTitle = title.toString()
            titleMarginStart = resources.getDimensionPixelSize(dimen.keyline_2)
            contentInsetStartWithNavigation = 0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        resetToolbarOnExit()
        binding.removeSaveStateWatcher(saveStateWatcher)
    }

    private fun initializeEditStateIfNecessary(mode: EditingExisting) {
        if (!mode.hasPopulatedFields) {
            populateFields(mode.credentialsViewed)
            initialiseTextWatchers()
            viewModel.onCredentialEditModePopulated()
        }
    }

    private fun resetToolbarOnExit() {
        getActionBar()?.apply {
            if (initialActionBarTitle != null) {
                title = initialActionBarTitle
            }
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
            notes = binding.notesEditText.text.convertBlankToNull(),
        )
        viewModel.saveOrUpdateCredentials(updatedCredentials)
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

            getActionBar()?.title = credentials.domainTitle ?: credentials.domain
        }
    }

    private fun showViewMode(credentials: LoginCredentials) {
        updateToolbarForView(credentials)
        binding.apply {
            domainTitleEditText.visibility = View.GONE
            lastUpdatedView.visibility = View.VISIBLE
            domainTitleEditText.isEditable = false
            usernameEditText.isEditable = false
            passwordEditText.isEditable = false
            domainEditText.isEditable = false
            notesEditText.isEditable = false
        }
        stopEditTextWatchers()
    }

    private fun showEditMode() {
        binding.apply {
            domainTitleEditText.visibility = View.VISIBLE
            lastUpdatedView.visibility = View.GONE
            domainTitleEditText.isEditable = true
            usernameEditText.isEditable = true
            passwordEditText.isEditable = true
            domainEditText.isEditable = true
            notesEditText.isEditable = true
        }
        initialiseTextWatchers()
    }

    private fun DaxTextInput.setText(text: String?) {
        this.text = text ?: ""
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.commandsCredentialView.collect { commands ->
                    commands.forEach { processCommand(it) }
                }
            }
        }

        viewModel.viewState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                when (state.credentialMode) {
                    is Viewing -> {
                        populateFields(state.credentialMode.credentialsViewed)
                        showViewMode(state.credentialMode.credentialsViewed)
                        invalidateMenu()
                    }

                    is EditingExisting -> {
                        initializeEditStateIfNecessary(state.credentialMode)
                        updateToolbarForEdit()
                    }

                    is EditingNewEntry -> {
                        updateToolbarForNewEntry()
                    }

                    else -> {
                    }
                }
            }.launchIn(lifecycleScope)
    }

    private fun processCommand(command: CredentialModeCommand) {
        var processed = true
        when (command) {
            is ShowEditCredentialMode -> showEditMode()
            is ShowManualCredentialMode -> showEditMode()
            else -> processed = false
        }
        if (processed) {
            Timber.v("Processed command $command")
            viewModel.commandProcessed(command)
        }
    }

    private fun disableSystemAutofillServiceOnPasswordField() {
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            binding.passwordEditText.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
    }

    private fun String.convertBlankToNull(): String? = this.ifBlank { null }

    private fun updateToolbarForEdit() {
        getActionBar()?.apply {
            setHomeAsUpIndicator(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
            title = getString(R.string.credentialManagementEditTitle)
            setDisplayUseLogoEnabled(false)
        }
        invalidateMenu()
    }

    private fun updateToolbarForNewEntry() {
        getActionBar()?.apply {
            setHomeAsUpIndicator(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
            title = getString(R.string.autofillManagementAddLogin)
            setDisplayUseLogoEnabled(false)
        }
        invalidateMenu()
    }

    private fun updateToolbarForView(credentials: LoginCredentials) {
        getActionBar()?.apply {
            setHomeAsUpIndicator(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)
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
                            width = resources.getDimensionPixelSize(dimen.toolbarIconSize),
                            height = resources.getDimensionPixelSize(dimen.toolbarIconSize),
                            cornerRadius = resources.getDimensionPixelSize(dimen.keyline_0),
                        ),
                    ),
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
