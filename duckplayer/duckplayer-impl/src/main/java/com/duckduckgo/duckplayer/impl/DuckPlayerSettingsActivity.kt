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

package com.duckduckgo.duckplayer.impl

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.addClickableLink
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckplayer.api.DuckPlayerSettingsNoParams
import com.duckduckgo.duckplayer.api.PrivatePlayerMode
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.duckduckgo.duckplayer.impl.DuckPlayerSettingsViewModel.ViewState
import com.duckduckgo.duckplayer.impl.databinding.ActivityDuckPlayerSettingsBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DuckPlayerSettingsNoParams::class)
class DuckPlayerSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: DuckPlayerSettingsViewModel by bindViewModel()
    private val binding: ActivityDuckPlayerSettingsBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.duckPlayerSettingsText.addClickableLink(
            annotation = "learn_more_link",
            textSequence = getText(R.string.duck_player_settings_activity_description),
            onClick = { viewModel.duckPlayerLearnMoreClicked() },
        )

        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        binding.duckPlayerModeSelector.setClickListener {
            viewModel.duckPlayerModeSelectorClicked()
        }
        binding.duckPlayerDisabledLearnMoreButton.setOnClickListener {
            viewModel.onContingencyLearnMoreClicked()
        }
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: DuckPlayerSettingsViewModel.Command) {
        when (it) {
            is DuckPlayerSettingsViewModel.Command.OpenPlayerModeSelector -> {
                launchPlayerModeSelector(it.current)
            }
            is DuckPlayerSettingsViewModel.Command.OpenLearnMore -> {
                globalActivityStarter.start(
                    this,
                    WebViewActivityWithParams(
                        url = it.learnMoreLink,
                        screenTitle = getString(R.string.duck_player_setting_title),
                    ),
                )
            }
            is DuckPlayerSettingsViewModel.Command.LaunchDuckPlayerContingencyPage -> {
                globalActivityStarter.start(
                    this,
                    WebViewActivityWithParams(
                        url = it.helpPageLink,
                        screenTitle = getString(R.string.duck_player_unavailable),
                    ),
                )
            }
        }
    }

    private fun launchPlayerModeSelector(privatePlayerMode: PrivatePlayerMode) {
        val options =
            listOf(
                Pair(Enabled, R.string.duck_player_mode_always),
                Pair(Disabled, R.string.duck_player_mode_never),
                Pair(AlwaysAsk, R.string.duck_player_mode_always_ask),
            )
        RadioListAlertDialogBuilder(this)
            .setTitle(getString(R.string.duck_player_mode_dialog_title))
            .setMessage(getString(R.string.duck_player_mode_dialog_description))
            .setOptions(
                options.map { it.second },
                options.map { it.first }.indexOf(privatePlayerMode) + 1,
            )
            .setPositiveButton(R.string.duck_player_save)
            .setNegativeButton(R.string.duck_player_cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val selectedPlayerMode =
                            when (selectedItem) {
                                1 -> Enabled
                                2 -> Disabled
                                else -> AlwaysAsk
                            }
                        viewModel.onPlayerModeSelected(selectedPlayerMode)
                    }
                },
            )
            .show()
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState) {
            is ViewState.Enabled -> {
                binding.duckPlayerModeSelector.isEnabled = true
                binding.duckPlayerDisabledSection.isVisible = false
            }
            is ViewState.DisabledWithHelpLink -> {
                binding.duckPlayerModeSelector.isEnabled = false
                binding.duckPlayerDisabledSection.isVisible = true
            }
        }
        binding.duckPlayerModeSelector.setSecondaryText(
            when (viewState.privatePlayerMode) {
                Enabled -> getString(R.string.duck_player_mode_always)
                Disabled -> getString(R.string.duck_player_mode_never)
                else -> getString(R.string.duck_player_mode_always_ask)
            },
        )
    }
}
