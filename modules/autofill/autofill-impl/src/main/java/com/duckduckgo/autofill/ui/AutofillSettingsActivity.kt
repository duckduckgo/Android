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

package com.duckduckgo.autofill.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.databinding.ActivityAutofillSettingsBinding
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
class AutofillSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var faviconManager: FaviconManager

    private lateinit var adapter: AutofillSettingsRecyclerAdapter

    private val binding: ActivityAutofillSettingsBinding by viewBinding()
    private val viewModel: AutofillSettingsViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureRecyclerView()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                val logins = viewModel.logins()
                adapter.updateLogins(logins)
            }
        }
    }

    private fun onCredentialsSelected(loginCredentials: LoginCredentials) {
        Timber.e("credentials selected: %s", loginCredentials)
    }

    private fun onCopyUsername(loginCredentials: LoginCredentials) {
        loginCredentials.username?.let {
            it.copyToClipboard()
            showCopiedToClipboardSnackbar("Username")
        }
    }

    private fun onCopyPassword(loginCredentials: LoginCredentials) {
        loginCredentials.password?.let {
            it.copyToClipboard()
            showCopiedToClipboardSnackbar("Password")
        }
    }

    private fun String.copyToClipboard() {
        clipboardManager().setPrimaryClip(ClipData.newPlainText("", this))
    }

    private fun showCopiedToClipboardSnackbar(type: String) {
        Snackbar.make(binding.root, "$type copied to clipboard", Snackbar.LENGTH_SHORT).show()
    }

    private fun configureRecyclerView() {

        adapter = AutofillSettingsRecyclerAdapter(
            this, faviconManager,
            onCredentialSelected = this::onCredentialsSelected,
            onCopyUsername = this::onCopyUsername,
            onCopyPassword = this::onCopyPassword,
        )

        binding.logins.adapter = adapter
    }

    private fun clipboardManager(): ClipboardManager {
        return getSystemService(ClipboardManager::class.java)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AutofillSettingsActivity::class.java)
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
                return AutofillSettingsActivity.intent(context)
            }
        }
    }
}
