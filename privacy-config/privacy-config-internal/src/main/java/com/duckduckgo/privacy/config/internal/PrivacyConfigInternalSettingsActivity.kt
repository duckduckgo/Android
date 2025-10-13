/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.internal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CompoundButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalViewModel.Command
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalViewModel.Command.ConfigDownloaded
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalViewModel.Command.ConfigError
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalViewModel.Command.Loading
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalViewModel.ViewState
import com.duckduckgo.privacy.config.internal.databinding.ActivityPrivacyConfigInternalSettingsBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class PrivacyConfigInternalSettingsActivity : DuckDuckGoActivity() {

    @Inject lateinit var downloader: PrivacyConfigDownloader

    private val binding: ActivityPrivacyConfigInternalSettingsBinding by viewBinding()
    private val viewModel: PrivacyConfigInternalViewModel by bindViewModel()

    private val endpointToggleListener = CompoundButton.OnCheckedChangeListener { _, toggleState ->
        viewModel.changeToggleState(toggleState)
        binding.urlInput.isEditable = toggleState
        if (!toggleState) binding.validation.hide()
        runValidation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        configureViews()
        viewModel.start()
        viewModel.viewState().flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            renderView(it)
        }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun configureViews() {
        binding.endpointToggle.setOnCheckedChangeListener(endpointToggleListener)
        binding.urlInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // NOOP
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // NOOP
                }

                override fun afterTextChanged(p0: Editable?) {
                    viewModel.changeCustomUrl(p0.toString())
                    runValidation()
                }
            },
        )
        binding.load.setOnClickListener {
            runValidation()
            viewModel.download()
        }
        binding.reset.setOnClickListener {
            binding.endpointToggle.setIsChecked(false)
            viewModel.download()
        }
    }

    private fun renderView(viewState: ViewState) {
        binding.latestVersion.setSecondaryText(viewState.latestVersionLoaded)
        binding.timestamp.setSecondaryText(viewState.timestamp)
        binding.latestUrl.setSecondaryText(viewState.latestUrl)
        binding.urlInput.text = viewState.customUrl
        binding.urlInput.isEditable = viewState.toggleState
        binding.endpointToggle.setIsChecked(viewState.toggleState)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Loading -> {
                binding.progress.show()
                binding.container.hide()
            }
            is ConfigDownloaded -> {
                binding.progress.hide()
                binding.container.show()
                onConfigLoaded(command.url)
            }
            is ConfigError -> {
                binding.progress.hide()
                binding.container.show()
                onConfigError(command.message)
            }
        }
    }

    private fun runValidation() {
        if (viewModel.canUrlBeChanged()) {
            binding.validation.gone()
            binding.load.isEnabled = true
        } else {
            binding.validation.show()
            binding.load.isEnabled = false
            binding.validation.text = "URL is not valid or toggle is disabled. Default URL will be used"
        }
    }

    private fun onConfigLoaded(url: String) {
        TextAlertDialogBuilder(this)
            .setTitle("Remote Config Loaded")
            .setPositiveButton(R.string.ok)
            .setMessage("$url was successfully loaded and applied.")
            .show()
    }
    private fun onConfigError(message: String) {
        TextAlertDialogBuilder(this)
            .setTitle("Something went wrong :(")
            .setMessage(message)
            .setPositiveButton(R.string.ok, DESTRUCTIVE)
            .show()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, PrivacyConfigInternalSettingsActivity::class.java)
        }
    }
}
