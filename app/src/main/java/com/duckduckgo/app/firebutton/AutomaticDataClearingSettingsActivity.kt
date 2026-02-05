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

package com.duckduckgo.app.firebutton

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityAutomaticDataClearingSettingsBinding
import com.duckduckgo.app.firebutton.AutomaticDataClearingSettingsViewModel.Command
import com.duckduckgo.app.firebutton.AutomaticDataClearingSettingsViewModel.ViewState
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.clear.getClearWhenForIndex
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class AutomaticDataClearingSettingsActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private val viewModel: AutomaticDataClearingSettingsViewModel by bindViewModel()
    private val binding: ActivityAutomaticDataClearingSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        supportActionBar?.setTitle(R.string.dataClearingAutomaticDataClearing)

        configureUiEventHandlers()
        observeViewModel()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onScreenExit()
    }

    private fun configureUiEventHandlers() {
        with(binding) {
            clearWhenSetting.setClickListener {
                viewModel.onClearWhenClicked()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState -> updateUi(viewState) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun updateUi(viewState: ViewState) {
        with(binding) {
            automaticClearingToggle.quietlySetIsChecked(viewState.automaticClearingEnabled) { _, isChecked ->
                viewModel.onAutomaticClearingToggled(isChecked)
            }
            optionsContainer.isVisible = viewState.automaticClearingEnabled

            clearTabsSetting.quietlySetIsChecked(viewState.clearTabs) { _, isChecked ->
                viewModel.onOptionToggled(FireClearOption.TABS, isChecked)
            }
            clearDataSetting.quietlySetIsChecked(viewState.clearData) { _, isChecked ->
                viewModel.onOptionToggled(FireClearOption.DATA, isChecked)
            }
            clearDuckAiChatsSetting.quietlySetIsChecked(viewState.clearDuckAiChats) { _, isChecked ->
                viewModel.onOptionToggled(FireClearOption.DUCKAI_CHATS, isChecked)
            }
            clearDuckAiChatsSetting.isVisible = viewState.showDuckAiChatsOption

            clearWhenSetting.setSecondaryText(getString(viewState.clearWhenOption.nameStringResourceId()))
        }
    }

    @StringRes
    private fun ClearWhenOption.nameStringResourceId(): Int {
        return when (this) {
            ClearWhenOption.APP_EXIT_ONLY -> R.string.settingsAutomaticallyClearWhenAppExitOnly
            ClearWhenOption.APP_EXIT_OR_5_MINS -> R.string.settingsAutomaticallyClearWhenAppExit5Minutes
            ClearWhenOption.APP_EXIT_OR_15_MINS -> R.string.settingsAutomaticallyClearWhenAppExit15Minutes
            ClearWhenOption.APP_EXIT_OR_30_MINS -> R.string.settingsAutomaticallyClearWhenAppExit30Minutes
            ClearWhenOption.APP_EXIT_OR_60_MINS -> R.string.settingsAutomaticallyClearWhenAppExit60Minutes
            ClearWhenOption.APP_EXIT_OR_5_SECONDS -> R.string.settingsAutomaticallyClearWhenAppExit5Seconds
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.ShowClearWhenDialog -> launchClearWhenDialog(command.option)
        }
    }

    private fun launchClearWhenDialog(option: ClearWhenOption) {
        val currentOption = option.getOptionIndex()
        val clearWhenOptions = mutableListOf(
            R.string.settingsAutomaticallyClearWhenAppExitOnly,
            R.string.settingsAutomaticallyClearWhenAppExit5Minutes,
            R.string.settingsAutomaticallyClearWhenAppExit15Minutes,
            R.string.settingsAutomaticallyClearWhenAppExit30Minutes,
            R.string.settingsAutomaticallyClearWhenAppExit60Minutes,
        )
        if (appBuildConfig.isDebug) {
            clearWhenOptions.add(R.string.settingsAutomaticallyClearWhenAppExit5Seconds)
        }
        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsAutomaticallyClearWhenDialogTitle)
            .setOptions(clearWhenOptions, currentOption)
            .setPositiveButton(R.string.settingsAutomaticallyClearingDialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val clearWhenSelected = selectedItem.getClearWhenForIndex()
                        viewModel.onClearWhenOptionSelected(clearWhenSelected)
                    }
                },
            )
            .show()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AutomaticDataClearingSettingsActivity::class.java)
        }
    }
}
