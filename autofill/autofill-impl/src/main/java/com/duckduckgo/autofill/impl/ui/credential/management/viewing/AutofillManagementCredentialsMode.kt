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

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.CompoundButton
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementEditModeBinding
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.CredentialMode.Editing
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.CredentialMode.EditingExisting
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.CredentialMode.EditingNewEntry
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.CredentialMode.Viewing
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.CredentialModeCommand
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.CredentialModeCommand.ShowEditCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.CredentialModeCommand.ShowManualCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.DuckAddressStatus
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.DuckAddressStatus.Activated
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.DuckAddressStatus.Deactivated
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.DuckAddressStatus.FailedToObtainStatus
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.DuckAddressStatus.FetchingActivationStatus
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.DuckAddressStatus.NotADuckAddress
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.DuckAddressStatus.NotManageable
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.DuckAddressStatus.NotSignedIn
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillPasswordsManagementViewModel.DuckAddressStatus.SettingActivationStatus
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.InitialExtractor
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextInput
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.R.dimen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

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

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var initialExtractor: InitialExtractor

    @Inject
    lateinit var duckAddressStatusChangeConfirmer: DuckAddressStatusChangeConfirmer

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var stringBuilder: AutofillManagementStringBuilder

    // we need to revert the toolbar title when this fragment is destroyed, so will track its initial value
    private var initialActionBarTitle: String? = null

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillPasswordsManagementViewModel::class.java]
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
                launchDeleteLoginConfirmationDialog()
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

    private fun launchDeleteLoginConfirmationDialog() {
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
                                    viewModel.onDeleteCurrentCredentials()
                                    viewModel.onExitCredentialMode()
                                }
                            },
                        )
                        .show()
                }
            }
        }
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
            initialActionBarTitle = title?.toString() ?: ""
            titleMarginStart = resources.getDimensionPixelSize(dimen.keyline_2)
            contentInsetStartWithNavigation = 0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disableSystemAutofillServiceOnPasswordField()
        resetToolbarOnExit()
        binding.removeSaveStateWatcher(saveStateWatcher)
    }

    private fun initializeEditStateIfNecessary(mode: EditingExisting) {
        if (!mode.hasPopulatedFields) {
            populateFields(mode.credentialsViewed, showLinkButton = false)
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
            val text = binding.usernameEditText.text
            if (text.isNotEmpty()) viewModel.onCopyUsername(text)
        }
        binding.passwordEditText.onAction {
            val text = binding.passwordEditText.text
            if (text.isNotEmpty()) viewModel.onCopyPassword(text)
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

    private fun populateFields(
        credentials: LoginCredentials,
        showLinkButton: Boolean,
    ) {
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

            configureDomainLinkButton(showLinkButton)

            getActionBar()?.title = credentials.extractTitle()
        }
    }

    private fun configureDomainLinkButton(showLinkButton: Boolean) {
        if (showLinkButton) {
            binding.domainEditText.setEndIcon(R.drawable.ic_link_24, getString(R.string.credentialManagementLinkButtonContentDescription))
            binding.domainEditText.onAction {
                startActivity(browserNav.openInNewTab(binding.root.context, binding.domainEditText.text))
                activity?.finish()
            }
        } else {
            binding.domainEditText.removeEndIcon()
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

            hideAllDuckAddressManagementViews()
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
            .transformLatest {
                emit(it.credentialMode)
            }
            .distinctUntilChanged()
            .onEach { credentialMode ->
                when (credentialMode) {
                    is Viewing -> {
                        populateFields(credentialMode.credentialsViewed, showLinkButton = credentialMode.showLinkButton)
                        renderDuckAddressStatus(credentialMode.duckAddressStatus)
                        showViewMode(credentialMode.credentialsViewed)
                        invalidateMenu()
                    }

                    is EditingExisting -> {
                        initializeEditStateIfNecessary(credentialMode)
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

    private val activationStatusChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        val duckAddress = binding.usernameEditText.text
        val context = binding.usernameEditText.context
        if (isChecked) {
            duckAddressStatusChangeConfirmer.showConfirmationToActivate(
                context = context,
                duckAddress = duckAddress,
                onConfirm = {
                    viewModel.activationStatusChanged(checked = true, duckAddress = duckAddress)
                },
                onCancel = {
                    revertToggleValue(false)
                },
            )
        } else {
            duckAddressStatusChangeConfirmer.showConfirmationToDeactivate(
                context = context,
                duckAddress = duckAddress,
                onConfirm = {
                    viewModel.activationStatusChanged(checked = false, duckAddress = duckAddress)
                },
                onCancel = {
                    revertToggleValue(true)
                },
            )
        }
    }

    private fun revertToggleValue(newCheckedState: Boolean) {
        binding.duckAddressManagementLabel.quietlySetIsChecked(newCheckedState, activationStatusChangeListener)
    }

    private fun renderDuckAddressStatus(duckAddressStatus: DuckAddressStatus) = with(binding) {
        when (duckAddressStatus) {
            is FetchingActivationStatus -> hideAllDuckAddressManagementViews()
            is NotADuckAddress -> hideAllDuckAddressManagementViews()
            is NotManageable -> hideAllDuckAddressManagementViews()

            is Activated -> {
                duckAddressManagementLabel.quietlySetIsChecked(true, activationStatusChangeListener)
                duckAddressManagementLabel.setLeadingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_email_16)
                duckAddressManagementLabel.setSecondaryText(getString(R.string.credentialManagementDuckAddressActivatedLabel))
                duckAddressManagementLabel.show()
                duckAddressManagementLabel.isEnabled = true

                duckAddressManagementUnavailable.gone()
                notSignedIntoDuckAddressInfoPanel.gone()
            }

            is Deactivated -> {
                duckAddressManagementLabel.quietlySetIsChecked(false, activationStatusChangeListener)
                duckAddressManagementLabel.setLeadingIconResource(R.drawable.ic_email_deactivate_24)
                duckAddressManagementLabel.setSecondaryText(getString(R.string.credentialManagementDuckAddressDeactivatedLabel))
                duckAddressManagementLabel.isEnabled = true
                duckAddressManagementLabel.show()

                duckAddressManagementUnavailable.gone()
                notSignedIntoDuckAddressInfoPanel.gone()
            }

            is SettingActivationStatus -> {
                duckAddressManagementLabel.show()
                duckAddressManagementLabel.isEnabled = false

                val text = if (duckAddressStatus.activating) {
                    R.string.credentialManagementDuckAddressActivatingLabel
                } else {
                    R.string.credentialManagementDuckAddressDeactivatingLabel
                }
                duckAddressManagementLabel.setSecondaryText(getString(text))

                duckAddressManagementUnavailable.gone()
                notSignedIntoDuckAddressInfoPanel.gone()
            }

            is NotSignedIn -> {
                duckAddressManagementLabel.gone()
                duckAddressManagementUnavailable.gone()
                val text = getText(R.string.credentialManagementEnableEmailProtectionPrompt)
                notSignedIntoDuckAddressInfoPanel.setClickableLink(
                    annotation = "enable_duck_address",
                    fullText = text,
                    onClick = {
                        startActivity(browserNav.openInNewTab(notSignedIntoDuckAddressInfoPanel.context, EMAIL_SIGN_IN_URL))
                    },
                )
                notSignedIntoDuckAddressInfoPanel.show()
            }

            is FailedToObtainStatus -> {
                duckAddressManagementLabel.gone()
                notSignedIntoDuckAddressInfoPanel.gone()
                duckAddressManagementUnavailable.show()
                duckAddressManagementUnavailable.setSecondaryText(
                    getString(R.string.credentialManagementDuckAddressManagementTemporarilyUnavailable),
                )
            }
        }
    }

    private fun processCommand(command: CredentialModeCommand) {
        var processed = true
        when (command) {
            is ShowEditCredentialMode -> showEditMode()
            is ShowManualCredentialMode -> showEditMode()
            else -> processed = false
        }
        if (processed) {
            logcat(VERBOSE) { "Processed command $command" }
            viewModel.commandProcessed(command)
        }
    }

    private fun disableSystemAutofillServiceOnPasswordField() {
        binding.passwordEditText.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        context?.let {
            it.getSystemService(AutofillManager::class.java)?.cancel()
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
            title = credentials.extractTitle()
            setDisplayUseLogoEnabled(true)
        }
        invalidateMenu()
    }

    private suspend fun showPlaceholderFavicon(credentials: LoginCredentials) {
        withContext(dispatchers.io()) {
            val size = resources.getDimensionPixelSize(dimen.toolbarIconSize)
            val placeholder = generateDefaultFavicon(credentials, size)
            val favicon = BitmapDrawable(resources, placeholder)
            withContext(dispatchers.main()) {
                getActionBar()?.setLogo(favicon)
            }
        }
    }

    private fun loadDomainFavicon(credentials: LoginCredentials) {
        lifecycleScope.launch(dispatchers.io()) {
            showPlaceholderFavicon(credentials)
            generateFaviconFromDomain(credentials)?.let {
                withContext(dispatchers.main()) {
                    getActionBar()?.setLogo(it)
                }
            }
        }
    }

    private fun hideAllDuckAddressManagementViews() {
        binding.notSignedIntoDuckAddressInfoPanel.gone()
        binding.duckAddressManagementLabel.gone()
        binding.duckAddressManagementUnavailable.gone()
    }

    private suspend fun generateFaviconFromDomain(credentials: LoginCredentials): BitmapDrawable? {
        val size = resources.getDimensionPixelSize(dimen.toolbarIconSize)
        val domain = credentials.domain ?: return null
        val favicon = faviconManager.loadFromDiskWithParams(
            tabId = null,
            url = domain,
            width = size,
            height = size,
            cornerRadius = resources.getDimensionPixelSize(dimen.keyline_0),
        ) ?: return null
        return BitmapDrawable(resources, favicon)
    }

    private fun generateDefaultFavicon(
        credentials: LoginCredentials,
        size: Int,
    ): Bitmap {
        val faviconPlaceholderLetter = initialExtractor.extractInitial(credentials)
        return faviconManager.generateDefaultFavicon(placeholder = faviconPlaceholderLetter, domain = credentials.domain ?: "").toBitmap(size, size)
    }

    private fun getActionBar(): ActionBar? = (activity as AppCompatActivity).supportActionBar
    private fun invalidateMenu() = (activity as AppCompatActivity).invalidateMenu()

    companion object {
        fun instance() = AutofillManagementCredentialsMode()

        private const val EMAIL_SIGN_IN_URL = "https://duckduckgo.com/email/login"
    }
}

fun LoginCredentials.extractTitle(): String? = if (this.domainTitle.isNullOrEmpty()) this.domain else this.domainTitle
