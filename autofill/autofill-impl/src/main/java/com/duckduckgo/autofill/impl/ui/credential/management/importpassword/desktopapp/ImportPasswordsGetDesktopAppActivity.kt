/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityGetDesktopAppBinding
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.ImportPasswordsGetDesktopAppViewModel.Command
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.ImportPasswordsGetDesktopAppViewModel.Command.ShareLink
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.ImportPasswordsGetDesktopAppViewModel.Command.ShowCopiedNotification
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

data object GetDesktopAppParams : ActivityParams {
    private fun readResolve(): Any = GetDesktopAppParams
}

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(GetDesktopAppParams::class)
class ImportPasswordsGetDesktopAppActivity : DuckDuckGoActivity() {

    private val viewModel: ImportPasswordsGetDesktopAppViewModel by bindViewModel()
    private val binding: ActivityGetDesktopAppBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { executeCommand(it) }
            .launchIn(lifecycleScope)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        binding.shareLinkButton.setOnClickListener {
            viewModel.onShareClicked()
        }
        binding.downloadLinkText.setOnClickListener {
            viewModel.onLinkClicked()
        }
    }

    private fun executeCommand(command: Command) {
        when (command) {
            is ShareLink -> launchSharePageChooser(command.link)
            is ShowCopiedNotification -> showCopiedNotification()
        }
    }

    private fun showCopiedNotification() {
        Snackbar.make(binding.root, R.string.autofillManagementImportPasswordsGetDesktopBrowserLinkCopied, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun launchSharePageChooser(link: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val message = getString(R.string.autofillManagementImportPasswordsGetDesktopBrowserIntentMessage, link)
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_TITLE, getString(R.string.autofillManagementImportPasswordsGetDesktopBrowserIntentTitle))
        }

        try {
            startActivity(Intent.createChooser(share, null))
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "Activity not found: ${e.asLog()}" }
        }
    }
}
