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

import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreen
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreenDirectlyViewCredentialsParams
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreenShowSuggestionsForSiteParams
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityAutofillSettingsBinding
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Error
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.UserCancelled
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
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.AutofillManagementCredentialsMode
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.AutofillManagementDeviceUnsupportedMode
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.AutofillManagementDisabledMode
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.AutofillManagementListMode
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.AutofillManagementLockedMode
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.SearchBar
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.settings.api.SettingsPageFeature
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AutofillSettingsScreen::class)
@ContributeToActivityStarter(AutofillSettingsScreenShowSuggestionsForSiteParams::class)
@ContributeToActivityStarter(AutofillSettingsScreenDirectlyViewCredentialsParams::class)
class AutofillManagementActivity : DuckDuckGoActivity(), PasswordsScreenPromotionPlugin.Callback {

    val binding: ActivityAutofillSettingsBinding by viewBinding()
    private val viewModel: AutofillSettingsViewModel by bindViewModel()

    @Inject
    lateinit var deviceAuthenticator: DeviceAuthenticator

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var settingsPageFeature: SettingsPageFeature

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (deviceAuthenticator.isAuthenticationRequiredForAutofill()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        observeViewModel()
        sendLaunchPixel(savedInstanceState)
    }

    private fun sendLaunchPixel(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            viewModel.sendLaunchPixel(extractLaunchSource())
        }
    }

    private fun extractLaunchSource(): AutofillSettingsLaunchSource {
        intent.getActivityParams(AutofillSettingsScreenShowSuggestionsForSiteParams::class.java)?.let {
            return it.source
        }

        intent.getActivityParams(AutofillSettingsScreenDirectlyViewCredentialsParams::class.java)?.let {
            return it.source
        }

        intent.getActivityParams(AutofillSettingsScreen::class.java)?.let {
            return it.source
        }

        // default if nothing else matches
        return AutofillSettingsLaunchSource.Unknown
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            viewModel.onViewStarted()
            viewModel.launchDeviceAuth()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            viewModel.lock()
        }
    }

    private fun setupInitialState() {
        when (val mode = extractViewMode()) {
            is ViewMode.ListMode -> viewModel.onInitialiseListMode()
            is ViewMode.ListModeWithSuggestions -> viewModel.onInitialiseListMode()
            is ViewMode.CredentialMode -> viewModel.onViewCredentials(mode.loginCredentials)
        }
    }

    private fun launchDeviceAuth() {
        viewModel.lock()

        deviceAuthenticator.authenticate(this) {
            when (it) {
                Success -> onAuthenticationSuccessful()
                UserCancelled -> onAuthenticationCancelled()
                is Error -> onAuthenticationError()
            }
        }
    }

    private fun onAuthenticationSuccessful() {
        viewModel.unlock()
    }

    private fun onAuthenticationCancelled() {
        finish()
    }

    private fun onAuthenticationError() {
        finish()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.commands.collect { commands ->
                    commands.forEach { processCommand(it) }
                }
            }
        }
    }

    private fun processCommand(command: AutofillSettingsViewModel.Command) {
        var processed = true
        when (command) {
            is ShowCredentialMode -> showCredentialMode()
            is ShowUserUsernameCopied -> showCopiedToClipboardSnackbar(CopiedToClipboardDataType.Username)
            is ShowUserPasswordCopied -> showCopiedToClipboardSnackbar(CopiedToClipboardDataType.Password)
            is OfferUserUndoDeletion -> showUserCredentialDeletedWithUndoAction(command)
            is OfferUserUndoMassDeletion -> showUserCredentialsMassDeletedWithUndoAction(command)
            is ShowListMode -> showListMode()
            is ShowDisabledMode -> showDisabledMode()
            is ShowDeviceUnsupportedMode -> showDeviceUnsupportedMode()
            is ShowLockedMode -> showLockMode()
            is LaunchDeviceAuth -> launchDeviceAuth()
            is InitialiseViewAfterUnlock -> setupInitialState()
            is ExitCredentialMode -> exitCredentialMode()
            is ExitLockedMode -> exitLockedMode()
            is ExitDisabledMode -> exitDisabledMode()
            is ExitListMode -> exitListMode()
            else -> processed = false
        }
        if (processed) {
            Timber.v("Processed command $command")
            viewModel.commandProcessed(command)
        }
    }

    private fun showCopiedToClipboardSnackbar(dataType: CopiedToClipboardDataType) {
        val stringResourceId = when (dataType) {
            is CopiedToClipboardDataType.Username -> R.string.autofillManagementUsernameCopied
            is CopiedToClipboardDataType.Password -> R.string.autofillManagementPasswordCopied
        }
        Snackbar.make(binding.root, getString(stringResourceId), Snackbar.LENGTH_SHORT).show()
    }

    private fun showUserCredentialDeletedWithUndoAction(command: OfferUserUndoDeletion) {
        val snackbar = Snackbar.make(binding.root, R.string.autofillManagementDeletedConfirmation, Snackbar.LENGTH_LONG)
        if (command.credentials != null) {
            snackbar.setAction(R.string.autofillManagementUndoDeletion) {
                viewModel.reinsertCredentials(command.credentials)
            }
        }
        snackbar.show()
    }

    private fun showUserCredentialsMassDeletedWithUndoAction(command: OfferUserUndoMassDeletion) {
        val numberDeleted = command.credentials.size
        val stringResource = resources.getQuantityString(
            R.plurals.credentialManagementDeleteAllPasswordsSnackbarConfirmation,
            numberDeleted,
            numberDeleted,
        )

        Snackbar.make(binding.root, stringResource, Snackbar.LENGTH_LONG).also {
            it.setAction(R.string.autofillManagementUndoDeletion) {
                viewModel.reinsertCredentials(command.credentials)
            }
        }.show()
    }

    private fun showListMode() {
        resetToolbar()
        val currentUrl = extractSuggestionsUrl()
        val privacyProtectionStatus = extractPrivacyProtectionEnabled()
        val launchSource = extractLaunchSource()
        Timber.v("showListMode. currentUrl is %s", currentUrl)

        supportFragmentManager.commitNow {
            val fragment = AutofillManagementListMode.instance(currentUrl, privacyProtectionStatus, launchSource)
            replace(R.id.fragment_container_view, fragment, TAG_ALL_CREDENTIALS)
        }
    }

    private fun showCredentialMode() {
        supportFragmentManager.commitNow {
            replace(R.id.fragment_container_view, AutofillManagementCredentialsMode.instance(), TAG_CREDENTIAL)
        }
    }

    private fun exitCredentialMode() {
        if (credentialModeLaunchedDirectly()) {
            finish()
        } else {
            viewModel.onReturnToListModeFromCredentialMode()
        }
    }

    private fun exitDisabledMode() {
        supportFragmentManager.removeFragment(TAG_DISABLED)
    }

    private fun exitListMode() {
        supportFragmentManager.removeFragment(TAG_ALL_CREDENTIALS)
    }

    private fun exitLockedMode() {
        supportFragmentManager.commitNow {
            supportFragmentManager.findFragmentByTag(TAG_LOCKED)?.let {
                remove(it)
            }
        }
    }

    private fun credentialModeLaunchedDirectly(): Boolean {
        return extractViewMode() is ViewMode.CredentialMode
    }

    private fun showLockMode() {
        resetToolbar()

        supportFragmentManager.commitNow {
            supportFragmentManager.findFragmentByTag(TAG_LOCKED)?.let {
                remove(it)
            }
            setReorderingAllowed(true)
            add(R.id.fragment_container_view, AutofillManagementLockedMode.instance(), TAG_LOCKED)
        }
    }

    private fun showDisabledMode() {
        resetToolbar()

        supportFragmentManager.commit {
            supportFragmentManager.findFragmentByTag(TAG_DISABLED)?.let { remove(it) }
            replace(R.id.fragment_container_view, AutofillManagementDisabledMode.instance(), TAG_DISABLED)
        }
    }

    private fun showDeviceUnsupportedMode() {
        resetToolbar()

        supportFragmentManager.commit {
            supportFragmentManager.findFragmentByTag(TAG_UNSUPPORTED)?.let { remove(it) }
            replace(R.id.fragment_container_view, AutofillManagementDeviceUnsupportedMode.instance(), TAG_UNSUPPORTED)
        }
    }

    private fun resetToolbar() {
        if (settingsPageFeature.newSettingsPage().isEnabled()) {
            setTitle(R.string.autofillManagementScreenTitleNew)
        } else {
            setTitle(R.string.autofillManagementScreenTitle)
        }
        binding.toolbar.menu.clear()
        hideSearchBar()
        supportActionBar?.setHomeAsUpIndicator(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)
    }

    fun showSearchBar() {
        with(binding) {
            toolbar.gone()
            searchBar.handle(SearchBar.Event.ShowSearchBar)
            searchBar.showKeyboard()
        }
    }

    fun hideSearchBar() {
        with(binding) {
            toolbar.show()
            searchBar.handle(SearchBar.Event.DismissSearchBar)
            searchBar.hideKeyboard()
        }
    }

    private fun isSearchBarVisible(): Boolean = binding.searchBar.isVisible

    override fun onBackPressed() {
        when (viewModel.viewState.value.credentialMode) {
            is EditingExisting -> viewModel.onCancelEditMode()
            is EditingNewEntry -> viewModel.onCancelManualCreation()
            is Viewing -> {
                if (credentialModeLaunchedDirectly()) {
                    finish()
                } else {
                    viewModel.onReturnToListModeFromCredentialMode()
                }
            }

            is ListMode -> {
                if (isSearchBarVisible()) {
                    hideSearchBar()
                } else {
                    finish()
                }
            }

            is Disabled -> finish()
            is Locked -> finish()
            else -> super.onBackPressed()
        }
    }

    private fun extractViewMode(): ViewMode {
        intent.getActivityParams(AutofillSettingsScreenShowSuggestionsForSiteParams::class.java)?.let {
            return ViewMode.ListModeWithSuggestions(it.currentUrl)
        }

        intent.getActivityParams(AutofillSettingsScreenDirectlyViewCredentialsParams::class.java)?.let {
            return ViewMode.CredentialMode(it.loginCredentials)
        }

        // default if nothing else matches
        return ViewMode.ListMode
    }

    private fun extractSuggestionsUrl(): String? {
        val viewMode = extractViewMode()
        if (viewMode is ViewMode.ListModeWithSuggestions) {
            return viewMode.currentUrl
        }
        return null
    }

    private fun extractPrivacyProtectionEnabled(): Boolean? {
        intent.getActivityParams(AutofillSettingsScreenShowSuggestionsForSiteParams::class.java)?.let {
            return it.privacyProtectionEnabled
        } ?: return null
    }

    override fun onPromotionDismissed() {
        viewModel.onPromoDismissed()
    }

    companion object {
        private const val TAG_LOCKED = "tag_fragment_locked"
        private const val TAG_DISABLED = "tag_fragment_disabled"
        private const val TAG_UNSUPPORTED = "tag_fragment_unsupported"
        private const val TAG_CREDENTIAL = "tag_fragment_credential"
        private const val TAG_ALL_CREDENTIALS = "tag_fragment_credentials_list"
    }

    private sealed interface ViewMode {
        data object ListMode : ViewMode
        data class ListModeWithSuggestions(val currentUrl: String? = null) : ViewMode
        data class CredentialMode(val loginCredentials: LoginCredentials) : ViewMode
    }

    private sealed interface CopiedToClipboardDataType {
        object Username : CopiedToClipboardDataType
        object Password : CopiedToClipboardDataType
    }
}
