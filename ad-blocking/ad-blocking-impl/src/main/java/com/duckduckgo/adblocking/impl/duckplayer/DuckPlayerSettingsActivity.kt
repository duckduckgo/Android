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

package com.duckduckgo.adblocking.impl.duckplayer

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.OpenDuckPlayerInNewTab.On
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.OpenDuckPlayerInNewTab.Unavailable
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayerSettingsNoParams
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.Disabled
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.Enabled
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.adblocking.impl.databinding.ActivityDuckPlayerSettingsBinding
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsViewModel.ViewState
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DuckPlayerSettingsNoParams::class)
class DuckPlayerSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: DuckPlayerSettingsViewModel by bindViewModel()
    private val binding: ActivityDuckPlayerSettingsBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SETTINGS)
        if (edgeToEdgeEnabled) {
            val barStyle = if (isDarkThemeEnabled()) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            // Keep the navigation bar transparent in gesture nav; let the system draw a contrast
            // scrim only in 2/3-button nav so the buttons stay legible.
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = true
            }
        }

        setContentView(binding.root)

        with(binding) {
            duckPlayerSettingsText.addClickableSpan(
                textSequence = getText(R.string.duck_player_settings_activity_description),
                spans = listOf(
                    "learn_more_link" to object : DuckDuckGoClickableSpan() {
                        override fun onClick(widget: View) {
                            viewModel.duckPlayerLearnMoreClicked()
                        }
                    },
                ),
            )
        }

        setupToolbar(binding.includeToolbar.toolbar)

        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = true)
    }

    private fun configureUiEventHandlers() {
        with(binding) {
            duckPlayerModeSelector.setClickListener {
                viewModel.duckPlayerModeSelectorClicked()
            }
            duckPlayerDisabledLearnMoreButton.setOnClickListener {
                viewModel.onContingencyLearnMoreClicked()
            }
            openDuckPlayerInNewTabToggle.setOnCheckedChangeListener { _, isChecked ->
                viewModel.onOpenDuckPlayerInNewTabToggled(isChecked)
            }
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
                Pair(AlwaysAsk, R.string.duck_player_mode_always_ask),
                Pair(Disabled, R.string.duck_player_mode_never),
            )
        RadioListAlertDialogBuilder(this)
            .setTitle(getString(R.string.duck_player_mode_dialog_title))
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
                                2 -> AlwaysAsk
                                else -> Disabled
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
                with(binding) {
                    duckPlayerModeSelector.isEnabled = true
                    duckPlayerDisabledSection.isVisible = false
                    openDuckPlayerInNewTabToggle.isVisible =
                        viewState.privatePlayerMode != Disabled && viewState.openDuckPlayerInNewTab != Unavailable
                    openDuckPlayerInNewTabToggle.setIsChecked(viewState.openDuckPlayerInNewTab is On)
                    setDuckPlayerSectionVisibility(true)
                }
            }
            is ViewState.DisabledWithHelpLink -> {
                with(binding) {
                    duckPlayerModeSelector.isEnabled = false
                    duckPlayerDisabledSection.isVisible = true
                    setDuckPlayerSectionVisibility(false)
                }
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

    private fun setDuckPlayerSectionVisibility(isVisible: Boolean) {
        with(binding) {
            duckPlayerSettingsTitle.isVisible = isVisible
            duckPlayerSettingsIcon.isVisible = isVisible
            duckPlayerSettingsText.isVisible = isVisible
        }
    }
}
