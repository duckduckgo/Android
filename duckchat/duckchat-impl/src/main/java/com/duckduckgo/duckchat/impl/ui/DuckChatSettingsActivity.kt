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
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
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
@ContributeToActivityStarter(DuckChatSettingsNoParams::class, screenName = "duckai.settings")
class DuckChatSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: DuckChatSettingsViewModel by bindViewModel()
    private val binding: ActivityDuckChatSettingsBinding by viewBinding()

    private val userEnabledDuckChatToggleListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.onDuckChatUserEnabledToggled(isChecked)
        }

    private val inputScreenToggleListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.onDuckAiInputScreenToggled(isChecked)
        }

    private val menuToggleListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.onShowDuckChatInMenuToggled(isChecked)
        }

    private val addressBarToggleListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.onShowDuckChatInAddressBarToggled(isChecked)
        }

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var pixel: Pixel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setupToolbar(binding.includeToolbar.toolbar)

        observeViewModel()

        pixel.fire(DUCK_CHAT_SETTINGS_DISPLAYED)
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        if (viewState.isRebrandingAiFeaturesEnabled) {
            binding.includeToolbar.toolbar.title = getString(R.string.duck_chat_title_rebranding)
            binding.duckChatSettingsIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_ai_128))
            binding.userEnabledDuckChatToggleRebranding.quietlySetIsChecked(viewState.isDuckChatUserEnabled, userEnabledDuckChatToggleListener)
            binding.duckChatSettingsTitle.setText(R.string.duck_chat_title_rebranding)
            binding.userEnabledDuckChatToggle.gone()
            binding.userEnabledDuckChatToggleRebranding.show()
            binding.duckChatToggleSettingsTitle.setText(R.string.duck_chat_show_in_heading_rebranding)
            binding.showDuckChatSearchSettingsLink.setPrimaryText(getString(R.string.duck_chat_assist_settings_title_rebranding))
            binding.showDuckChatSearchSettingsLink.setSecondaryText(getString(R.string.duck_chat_assist_settings_description_rebranding))
        } else {
            binding.includeToolbar.toolbar.title = getString(R.string.duck_ai_paid_settings_title)
            binding.duckChatSettingsIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.chat_private_128))
            binding.userEnabledDuckChatToggle.quietlySetIsChecked(viewState.isDuckChatUserEnabled, userEnabledDuckChatToggleListener)
            binding.duckChatSettingsTitle.setText(R.string.duck_chat_title)
            binding.userEnabledDuckChatToggle.show()
            binding.userEnabledDuckChatToggleRebranding.gone()
            binding.duckChatToggleSettingsTitle.setText(R.string.duck_chat_show_in_heading)
            binding.showDuckChatSearchSettingsLink.setPrimaryText(getString(R.string.duck_chat_assist_settings_title))
            binding.showDuckChatSearchSettingsLink.setSecondaryText(getString(R.string.duck_chat_assist_settings_description))
        }

        binding.duckChatSettingsText.addClickableSpan(
            textSequence = if (viewState.isRebrandingAiFeaturesEnabled) {
                getText(R.string.duck_chat_settings_activity_description_rebranding)
            } else {
                getText(R.string.duck_chat_settings_activity_description)
            },
            spans = listOf(
                "learn_more_link" to object : DuckDuckGoClickableSpan() {
                    override fun onClick(widget: View) {
                        viewModel.duckChatLearnMoreClicked()
                    }
                },
            ),
        )

        binding.duckAiInputScreenEnabledToggle.apply {
            isVisible = viewState.shouldShowInputScreenToggle
            quietlySetIsChecked(viewState.isInputScreenEnabled, inputScreenToggleListener)
        }

        binding.duckChatToggleSettingsTitle.isVisible = viewState.isDuckChatUserEnabled

        binding.showDuckChatInMenuToggle.apply {
            isVisible = viewState.shouldShowAddressBarToggle
            quietlySetIsChecked(viewState.showInBrowserMenu, menuToggleListener)
        }
        binding.showDuckChatInAddressBarToggle.apply {
            isVisible = viewState.shouldShowAddressBarToggle
            quietlySetIsChecked(viewState.showInAddressBar, addressBarToggleListener)
        }
        binding.showDuckChatSearchSettingsLink.setOnClickListener {
            viewModel.duckChatSearchAISettingsClicked()
        }
    }

    private fun processCommand(command: DuckChatSettingsViewModel.Command) {
        when (command) {
            is DuckChatSettingsViewModel.Command.OpenLink -> {
                globalActivityStarter.start(
                    this,
                    WebViewActivityWithParams(
                        url = command.link,
                        screenTitle = getString(R.string.duck_chat_title),
                    ),
                )
            }
            is DuckChatSettingsViewModel.Command.OpenLinkInNewTab -> {
                startActivity(browserNav.openInNewTab(this@DuckChatSettingsActivity, command.link))
            }
        }
    }
}
