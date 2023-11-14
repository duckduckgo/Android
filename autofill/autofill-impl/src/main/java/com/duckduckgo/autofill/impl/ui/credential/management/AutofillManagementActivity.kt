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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillSettingsActivityLauncher
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityAutofillSettingsBinding
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Error
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.UserCancelled
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.Features.AUTOFILL_TO_ACCESS_CREDENTIALS
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitDisabledMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitListMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitLockedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.InitialiseViewAfterUnlock
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.LaunchDeviceAuth
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.OfferUserUndoDeletion
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
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.ui.view.SearchBar
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.hideKeyboard
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.view.showKeyboard
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@InjectWith(ActivityScope::class)
class AutofillManagementActivity : DuckDuckGoActivity() {

    val binding: ActivityAutofillSettingsBinding by viewBinding()
    private val viewModel: AutofillSettingsViewModel by bindViewModel()

    @Inject
    lateinit var deviceAuthenticator: DeviceAuthenticator

    @Inject
    lateinit var pixel: Pixel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        observeViewModel()
        sendLaunchPixel(savedInstanceState)
    }

    private fun sendLaunchPixel(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            val launchedFromBrowser = intent.hasExtra(EXTRAS_SUGGESTIONS_FOR_URL)
            val directLinkToCredentials = intent.hasExtra(EXTRAS_CREDENTIALS_TO_VIEW)
            viewModel.sendLaunchPixel(launchedFromBrowser, directLinkToCredentials)
        }
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
        if (intent.hasExtra(EXTRAS_CREDENTIALS_TO_VIEW)) {
            intent.getParcelableExtra<LoginCredentials>(EXTRAS_CREDENTIALS_TO_VIEW)?.let {
                viewModel.onViewCredentials(it)
            }
        } else {
            viewModel.onShowListMode()
        }
    }

    private fun launchDeviceAuth() {
        viewModel.lock()

        deviceAuthenticator.authenticate(AUTOFILL_TO_ACCESS_CREDENTIALS, this) {
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

    private fun showListMode() {
        resetToolbar()
        val currentUrl = intent.getStringExtra(EXTRAS_SUGGESTIONS_FOR_URL)
        Timber.v("showListMode. currentUrl is %s", currentUrl)

        supportFragmentManager.commitNow {
            replace(R.id.fragment_container_view, AutofillManagementListMode.instance(currentUrl), TAG_ALL_CREDENTIALS)
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
            viewModel.onShowListMode()
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
        return intent.getParcelableExtra<LoginCredentials>(EXTRAS_CREDENTIALS_TO_VIEW) != null
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
        setTitle(R.string.autofillManagementScreenTitle)
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
                    viewModel.onShowListMode()
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

    companion object {
        private const val EXTRAS_CREDENTIALS_TO_VIEW = "extras_credentials_to_view"
        private const val EXTRAS_SUGGESTIONS_FOR_URL = "extras_suggestions_for_url"
        private const val TAG_LOCKED = "tag_fragment_locked"
        private const val TAG_DISABLED = "tag_fragment_disabled"
        private const val TAG_UNSUPPORTED = "tag_fragment_unsupported"
        private const val TAG_CREDENTIAL = "tag_fragment_credential"
        private const val TAG_ALL_CREDENTIALS = "tag_fragment_credentials_list"

        /**
         * Launch the Autofill management activity, with LoginCredentials to jump directly into viewing mode.
         */
        fun intentDirectViewMode(
            context: Context,
            loginCredentials: LoginCredentials,
        ): Intent {
            return Intent(context, AutofillManagementActivity::class.java).apply {
                putExtra(EXTRAS_CREDENTIALS_TO_VIEW, loginCredentials)
            }
        }

        fun intentShowSuggestion(
            context: Context,
            currentUrl: String?,
        ): Intent {
            return Intent(context, AutofillManagementActivity::class.java).apply {
                putExtra(EXTRAS_SUGGESTIONS_FOR_URL, currentUrl)
            }
        }

        fun intentDefaultList(context: Context): Intent = Intent(context, AutofillManagementActivity::class.java)
    }
}

private sealed interface CopiedToClipboardDataType {
    object Username : CopiedToClipboardDataType
    object Password : CopiedToClipboardDataType
}

@ContributesTo(AppScope::class)
@Module
class AutofillSettingsModule {

    @Provides
    fun activityLauncher(): AutofillSettingsActivityLauncher {
        return object : AutofillSettingsActivityLauncher {
            override fun intent(context: Context): Intent = AutofillManagementActivity.intentDefaultList(context)

            override fun intentAlsoShowSuggestionsForSite(
                context: Context,
                currentUrl: String?,
            ): Intent {
                return AutofillManagementActivity.intentShowSuggestion(context, currentUrl)
            }

            override fun intentDirectlyViewCredentials(
                context: Context,
                loginCredentials: LoginCredentials,
            ): Intent {
                return AutofillManagementActivity.intentDirectViewMode(context, loginCredentials)
            }
        }
    }
}
