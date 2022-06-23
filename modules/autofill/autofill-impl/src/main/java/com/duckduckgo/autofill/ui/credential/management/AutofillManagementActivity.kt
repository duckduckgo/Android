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
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityAutofillSettingsBinding
import com.duckduckgo.autofill.ui.AutofillSettingsActivityLauncher
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command.*
import com.duckduckgo.autofill.ui.credential.management.viewing.AutofillManagementEditMode
import com.duckduckgo.autofill.ui.credential.management.viewing.AutofillManagementListMode
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult
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

    private var inEditMode: Boolean = false

    @Inject
    lateinit var deviceAuthenticator: DeviceAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // TODO implement reauth when app is backgrounded
        showAuthenticationChallenge(savedInstanceState)
    }

    private fun showAuthenticationChallenge(savedInstanceState: Bundle?) {
        deviceAuthenticator.authenticate(AUTOFILL, this) {
            if (it == AuthResult.Success) {
                if (savedInstanceState == null) {
                    showListMode()
                }

                observeViewModel()
            } else {
                // TODO show locked autofill UI once available
                finish()
            }
        }
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

    }

    private fun processCommand(command: AutofillSettingsViewModel.Command) {
        var processed = true
        when (command) {
            is ShowListMode -> showListMode()
            is ShowEditMode -> showEditMode(command.credentials)
            is ShowUserUsernameCopied -> showCopiedToClipboardSnackbar("Username")
            is ShowUserPasswordCopied -> showCopiedToClipboardSnackbar("Password")
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
        Timber.e("Show view mode")
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            // addToBackStack(null)
            replace(R.id.fragment_container_view, AutofillManagementListMode.instance())
        }

        inEditMode = false
    }

    private fun showEditMode(credentials: LoginCredentials) {
        Timber.e("Show edit mode")
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            // addToBackStack(null)
            replace(R.id.fragment_container_view, AutofillManagementEditMode.instance(credentials))
        }

        inEditMode = true
    }

    override fun onBackPressed() {
        if (inEditMode) {
            showListMode()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AutofillManagementActivity::class.java)
        }
    }

}

@ContributesTo(AppScope::class)
@Module
class AutofillSettingsModule {

    @Provides
    fun activityLauncher(): AutofillSettingsActivityLauncher {
        return object : AutofillSettingsActivityLauncher {
            override fun intent(context: Context): Intent {
                return AutofillManagementActivity.intent(context)
            }
        }
    }
}
