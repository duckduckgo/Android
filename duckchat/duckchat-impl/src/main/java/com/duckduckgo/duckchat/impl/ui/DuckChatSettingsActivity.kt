/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ActivityDuckChatSettingsBinding
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SETTINGS_DISPLAYED
import com.duckduckgo.duckchat.impl.ui.DuckChatSettingsViewModel.ViewState
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DuckChatSettingsNoParams::class)
class DuckChatSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: DuckChatSettingsViewModel by bindViewModel()
    private val binding: ActivityDuckChatSettingsBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var pixel: Pixel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.duckChatSettingsText.addClickableSpan(
            textSequence = getText(R.string.duck_chat_settings_activity_description),
            spans = listOf(
                "learn_more_link" to object : DuckDuckGoClickableSpan() {
                    override fun onClick(widget: View) {
                        viewModel.duckChatLearnMoreClicked()
                    }
                },
            ),
        )

        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        observeViewModel()

        binding.showDuckChatInAddressBarToggle.isVisible = viewModel.isAddressBarEntryPointEnabled()

        pixel.fire(DUCK_CHAT_SETTINGS_DISPLAYED)
    }

    private fun configureUiEventHandlers() {
        binding.showDuckChatInMenuToggle.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onShowDuckChatInMenuToggled(isChecked)
        }
        binding.showDuckChatInAddressBarToggle.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onShowDuckChatInAddressBarToggled(isChecked)
        }
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        binding.showDuckChatInMenuToggle.setIsChecked(viewState.showInBrowserMenu)
        binding.showDuckChatInAddressBarToggle.setIsChecked(viewState.showInAddressBar)
    }

    private fun processCommand(command: DuckChatSettingsViewModel.Command) {
        when (command) {
            is DuckChatSettingsViewModel.Command.OpenLearnMore -> {
                globalActivityStarter.start(
                    this,
                    WebViewActivityWithParams(
                        url = command.learnMoreLink,
                        screenTitle = getString(R.string.duck_chat_title),
                    ),
                )
            }
        }
    }
}
