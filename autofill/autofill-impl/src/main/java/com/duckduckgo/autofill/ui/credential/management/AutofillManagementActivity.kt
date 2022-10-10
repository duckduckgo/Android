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

package com.duckduckgo.autofill.ui.credential.management

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityAutofillSettingsBinding
import com.duckduckgo.autofill.pixel.AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_CANCELLED
import com.duckduckgo.autofill.pixel.AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_FAILURE
import com.duckduckgo.autofill.pixel.AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_SHOWN
import com.duckduckgo.autofill.pixel.AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_SUCCESSFUL
import com.duckduckgo.autofill.ui.AutofillSettingsActivityLauncher
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command.*
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialMode.*
import com.duckduckgo.autofill.ui.credential.management.viewing.AutofillManagementCredentialsMode
import com.duckduckgo.autofill.ui.credential.management.viewing.AutofillManagementDisabledMode
import com.duckduckgo.autofill.ui.credential.management.viewing.AutofillManagementListMode
import com.duckduckgo.autofill.ui.credential.management.viewing.AutofillManagementLockedMode
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.Error
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.UserCancelled
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.Features.AUTOFILL
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class AutofillManagementActivity : DuckDuckGoActivity() {

    private val binding: ActivityAutofillSettingsBinding by viewBinding()
    private val viewModel: AutofillSettingsViewModel by bindViewModel()

    @Inject
    lateinit var deviceAuthenticator: DeviceAuthenticator

    @Inject
    lateinit var pixel: Pixel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        setupInitialState()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.launchDeviceAuth()
    }

    override fun onStop() {
        super.onStop()
        viewModel.lock()
    }

    private fun setupInitialState() {
        if (intent.hasExtra(EXTRAS_CREDENTIALS_TO_VIEW)) {
            intent.getParcelableExtra<LoginCredentials>(EXTRAS_CREDENTIALS_TO_VIEW)?.let {
                viewModel.onViewCredentials(it, true)
            }
        } else {
            showListMode()
        }
    }

    private fun launchDeviceAuth() {
        if (deviceAuthenticator.hasValidDeviceAuthentication()) {
            viewModel.lock()

            pixel.fire(AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_SHOWN)
            deviceAuthenticator.authenticate(AUTOFILL, this) {
                when (it) {
                    Success -> onAuthenticationSuccessful()
                    UserCancelled -> onAuthenticationCancelled()
                    is Error -> onAuthenticationError()
                }
                viewModel.onAuthenticationEnded()
            }
        } else {
            viewModel.disabled()
        }
    }

    private fun onAuthenticationSuccessful() {
        pixel.fire(AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_SUCCESSFUL)
        viewModel.unlock()
    }

    private fun onAuthenticationCancelled() {
        pixel.fire(AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_CANCELLED)
        finish()
    }

    private fun onAuthenticationError() {
        pixel.fire(AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_FAILURE)
        finish()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { state ->
                    processState(state)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.commands.collect { commands ->
                    commands.forEach { processCommand(it) }
                }
            }
        }
    }

    private fun processState(state: AutofillSettingsViewModel.ViewState) {
        if (state.credentialMode is NotInCredentialMode && !state.isLocked) {
            showListMode()
        }
    }

    private fun processCommand(command: AutofillSettingsViewModel.Command) {
        var processed = true
        when (command) {
            is ShowCredentialMode -> showCredentialMode(command.credentials, command.isLaunchedDirectly)
            is ShowUserUsernameCopied -> showCopiedToClipboardSnackbar("Username")
            is ShowUserPasswordCopied -> showCopiedToClipboardSnackbar("Password")
            is ShowDisabledMode -> showDisabledMode()
            is ShowLockedMode -> showLockMode()
            is LaunchDeviceAuth -> launchDeviceAuth()
            is ExitCredentialMode -> supportFragmentManager.forceExitFragment(TAG_CREDENTIAL)
            is ExitLockedMode -> supportFragmentManager.forceExitFragment(TAG_LOCKED)
            is ExitDisabledMode -> supportFragmentManager.forceExitFragment(TAG_DISABLED)
            is ExitListMode -> supportFragmentManager.forceExitFragment(TAG_ALL_CREDENTIALS)
            else -> processed = false
        }
        if (processed) {
            Timber.v("Processed command $command")
            viewModel.commandProcessed(command)
        }
    }

    private fun showCopiedToClipboardSnackbar(type: String) {
        Snackbar.make(binding.root, "$type copied to clipboard", Snackbar.LENGTH_SHORT).show()
    }

    private fun showListMode() {
        resetToolbar()
        supportFragmentManager.showFragment(AutofillManagementListMode.instance(), TAG_ALL_CREDENTIALS, false)
    }

    private fun showCredentialMode(
        credentials: LoginCredentials?,
        isLaunchedDirectly: Boolean
    ) {
        if (credentials != null) {
            binding.includeToolbar.toolbar.apply {
                titleMarginStart = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_2)
                contentInsetStartWithNavigation = 0
            }
            title = credentials.domainTitle ?: credentials.domain

            supportFragmentManager.showFragment(
                fragment = AutofillManagementCredentialsMode.instance(),
                tag = TAG_CREDENTIAL,
                shouldAddToBackStack = !isLaunchedDirectly
            )
        }
    }

    private fun showLockMode() {
        resetToolbar()
        supportFragmentManager.showFragment(AutofillManagementLockedMode.instance(), TAG_LOCKED, true)
    }

    private fun showDisabledMode() {
        resetToolbar()
        supportFragmentManager.showFragment(AutofillManagementDisabledMode.instance(), TAG_DISABLED, false)
    }

    private fun resetToolbar() {
        setTitle(R.string.managementScreenTitle)
        binding.includeToolbar.toolbar.menu.clear()
        supportActionBar?.setHomeAsUpIndicator(com.duckduckgo.mobile.android.R.drawable.ic_back_24)
    }

    override fun onBackPressed() {
        when (viewModel.viewState.value.credentialMode) {
            is Editing -> viewModel.onCancelEditMode()
            is Viewing -> if (supportFragmentManager.backStackEntryCount > 1) {
                viewModel.onExitCredentialMode()
            } else {
                super.onBackPressed()
            }

            else -> super.onBackPressed()
        }
    }

    companion object {
        private const val EXTRAS_CREDENTIALS_TO_VIEW = "extras_credentials_to_view"
        private const val TAG_LOCKED = "tag_fragment_locked"
        private const val TAG_DISABLED = "tag_fragment_disabled"
        private const val TAG_CREDENTIAL = "tag_fragment_credential"
        private const val TAG_ALL_CREDENTIALS = "tag_fragment_all_credentials"

        /**
         * Launch the Autofill management activity.
         * Optionally, can provide LoginCredentials to jump directly into viewing mode.
         * If no LoginCredentials provided, will show the list mode.
         */
        fun intent(
            context: Context,
            loginCredentials: LoginCredentials? = null
        ): Intent {
            return Intent(context, AutofillManagementActivity::class.java).apply {
                if (loginCredentials != null) {
                    putExtra(EXTRAS_CREDENTIALS_TO_VIEW, loginCredentials)
                }
            }
        }
    }
}

@ContributesTo(AppScope::class)
@Module
class AutofillSettingsModule {

    @Provides
    fun activityLauncher(): AutofillSettingsActivityLauncher {
        return object : AutofillSettingsActivityLauncher {
            override fun intent(
                context: Context,
                loginCredentials: LoginCredentials?
            ): Intent {
                return AutofillManagementActivity.intent(context, loginCredentials)
            }
        }
    }
}
