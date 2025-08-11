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
import com.duckduckgo.common.ui.store.AppTheme
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
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DuckChatSettingsNoParams::class, screenName = "duckai.settings")
class DuckChatSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: DuckChatSettingsViewModel by bindViewModel()
    private val binding: ActivityDuckChatSettingsBinding by viewBinding()

    @Inject
    lateinit var appTheme: AppTheme

    private val userEnabledDuckChatToggleListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.onDuckChatUserEnabledToggled(isChecked)
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
            binding.userEnabledDuckChatToggleRebranding.quietlySetIsChecked(viewState.isDuckChatUserEnabled, userEnabledDuckChatToggleListener)
            binding.duckChatSettingsTitle.setText(R.string.duck_chat_title_rebranding)
            binding.userEnabledDuckChatToggle.gone()
            binding.userEnabledDuckChatToggleRebranding.show()
            binding.duckChatToggleSettingsTitle.setText(R.string.duck_chat_show_in_heading_rebranding)
            binding.showDuckChatSearchSettingsLink.setPrimaryText(getString(R.string.duck_chat_assist_settings_title_rebranding))
            binding.showDuckChatSearchSettingsLink.setSecondaryText(getString(R.string.duck_chat_assist_settings_description_rebranding))
        } else {
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

        binding.duckAiInputScreenContent.isVisible = viewState.shouldShowInputScreenToggle
        if (viewState.isInputScreenEnabled) {
            // disable without ai container
            binding.duckAiInputScreenToggleWithoutAiImage.setImageDrawable(
                if (appTheme.isLightModeEnabled()) {
                    ContextCompat.getDrawable(this, R.drawable.searchbox_withoutai)
                } else {
                    ContextCompat.getDrawable(this, R.drawable.searchbox_withoutai_ondark)
                }
            )
            binding.duckAiInputScreenToggleWithoutAiCheck.setImageDrawable(
                ContextCompat.getDrawable(this, CommonR.drawable.ic_shape_circle_24)
            )
            // enable with ai container
            binding.duckAiInputScreenToggleWithAiImage.setImageDrawable(
                if (appTheme.isLightModeEnabled()) {
                    ContextCompat.getDrawable(this, R.drawable.searchbox_withai_active)
                } else {
                    ContextCompat.getDrawable(this, R.drawable.searchbox_withai_active_ondark)
                }
            )
            binding.duckAiInputScreenToggleWithAiCheck.setImageDrawable(
                ContextCompat.getDrawable(this, CommonR.drawable.ic_check_blue_round_24)
            )
        } else {
            // enable without ai container
            binding.duckAiInputScreenToggleWithoutAiImage.setImageDrawable(
                if (appTheme.isLightModeEnabled()) {
                    ContextCompat.getDrawable(this, R.drawable.searchbox_withoutai_active)
                } else {
                    ContextCompat.getDrawable(this, R.drawable.searchbox_withoutai_active_ondark)
                }
            )
            binding.duckAiInputScreenToggleWithoutAiCheck.setImageDrawable(
                ContextCompat.getDrawable(this, CommonR.drawable.ic_check_blue_round_24)
            )
            // enable with ai container
            binding.duckAiInputScreenToggleWithAiImage.setImageDrawable(
                if (appTheme.isLightModeEnabled()) {
                    ContextCompat.getDrawable(this, R.drawable.searchbox_withai)
                } else {
                    ContextCompat.getDrawable(this, R.drawable.searchbox_withai_ondark)
                }
            )
            binding.duckAiInputScreenToggleWithAiCheck.setImageDrawable(
                ContextCompat.getDrawable(this, CommonR.drawable.ic_shape_circle_24)
            )
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
        binding.duckAiInputScreenWithoutAiContainer.setOnClickListener {
            viewModel.onDuckAiInputScreenWithoutAiSelected()
        }
        binding.duckAiInputScreenWithAiContainer.setOnClickListener {
            viewModel.onDuckAiInputScreenWithAiSelected()
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
