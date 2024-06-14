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

package com.duckduckgo.app.appearance

import android.os.Bundle
import android.widget.CompoundButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.appearance.AppearanceViewModel.Command
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityAppearanceBinding
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.sendThemeChangedBroadcast
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AppearanceScreenNoParams::class)
class AppearanceActivity : DuckDuckGoActivity() {

    private val viewModel: AppearanceViewModel by bindViewModel()
    private val binding: ActivityAppearanceBinding by viewBinding()

    private val forceDarkModeToggleListener = CompoundButton.OnCheckedChangeListener { view, isChecked ->
        viewModel.onForceDarkModeSettingChanged(isChecked)

        TextAlertDialogBuilder(this)
            .setTitle(R.string.appearanceNightModeDialogTitle)
            .setMessage(R.string.appearanceNightModeDialogMessage)
            .setPositiveButton(R.string.appearanceNightModeDialogPrimaryCTA)
            .setNegativeButton(R.string.appearanceNightModeDialogSecondaryCTA)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        FireActivity.triggerRestart(baseContext, false)
                    }

                    override fun onNegativeButtonClicked() {
                        // no-op
                    }
                },
            )
            .show()
    }

    private val changeIconFlow = registerForActivityResult(ChangeIconContract()) { resultOk ->
        if (resultOk) {
            Timber.d("Icon changed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        binding.selectedThemeSetting.setClickListener { viewModel.userRequestedToChangeTheme() }
        binding.changeAppIconSetting.setOnClickListener { viewModel.userRequestedToChangeIcon() }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    updateSelectedTheme(it.theme)
                    binding.changeAppIcon.setImageResource(it.appIcon.icon)
                    binding.experimentalNightMode.quietlySetIsChecked(viewState.forceDarkModeEnabled, forceDarkModeToggleListener)
                    binding.experimentalNightMode.isEnabled = viewState.canForceDarkMode
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun updateSelectedTheme(selectedTheme: DuckDuckGoTheme) {
        val subtitle = getString(
            when (selectedTheme) {
                DuckDuckGoTheme.DARK -> R.string.settingsDarkTheme
                DuckDuckGoTheme.LIGHT -> R.string.settingsLightTheme
                DuckDuckGoTheme.SYSTEM_DEFAULT -> R.string.settingsSystemTheme
            },
        )
        binding.selectedThemeSetting.setSecondaryText(subtitle)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is Command.LaunchAppIcon -> launchAppIconChange()
            is Command.UpdateTheme -> sendThemeChangedBroadcast()
            is Command.LaunchThemeSettings -> launchThemeSelector(it.theme)
        }
    }

    private fun launchAppIconChange() {
        changeIconFlow.launch(null)
    }

    private fun launchThemeSelector(theme: DuckDuckGoTheme) {
        val currentTheme = theme.getOptionIndex()
        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsTheme)
            .setOptions(
                listOf(
                    R.string.settingsSystemTheme,
                    R.string.settingsLightTheme,
                    R.string.settingsDarkTheme,
                ),
                currentTheme,
            )
            .setPositiveButton(R.string.settingsThemeDialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val selectedTheme = when (selectedItem) {
                            2 -> DuckDuckGoTheme.LIGHT
                            3 -> DuckDuckGoTheme.DARK
                            else -> DuckDuckGoTheme.SYSTEM_DEFAULT
                        }
                        viewModel.onThemeSelected(selectedTheme)
                    }
                },
            )
            .show()
    }
}
