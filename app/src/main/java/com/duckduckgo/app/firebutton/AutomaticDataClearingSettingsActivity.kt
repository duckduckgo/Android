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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityAutomaticDataClearingSettingsBinding
import com.duckduckgo.app.firebutton.FireButtonViewModel.AutomaticallyClearData
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.getClearWhatOptionForIndex
import com.duckduckgo.app.settings.clear.getClearWhenForIndex
import com.duckduckgo.app.statistics.pixels.Pixel
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
    lateinit var pixel: Pixel

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private val viewModel: FireButtonViewModel by bindViewModel()
    private val binding: ActivityAutomaticDataClearingSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        supportActionBar?.setTitle(R.string.dataClearingAutomaticDataClearing)

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        with(binding) {
            automaticallyClearWhatSetting.setClickListener { viewModel.onAutomaticallyClearWhatClicked() }
            automaticallyClearWhenSetting.setClickListener { viewModel.onAutomaticallyClearWhenClicked() }
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                updateAutomaticClearDataOptions(viewState.automaticallyClearData, viewState.clearDuckAiData)
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun updateAutomaticClearDataOptions(
        automaticallyClearData: AutomaticallyClearData,
        clearDuckAiData: Boolean,
    ) {
        val clearWhatSubtitle = getString(automaticallyClearData.clearWhatOption.nameStringResourceId(clearDuckAiData))
        binding.automaticallyClearWhatSetting.setSecondaryText(clearWhatSubtitle)

        val clearWhenSubtitle = getString(automaticallyClearData.clearWhenOption.nameStringResourceId())
        binding.automaticallyClearWhenSetting.setSecondaryText(clearWhenSubtitle)

        val whenOptionEnabled = automaticallyClearData.clearWhenOptionEnabled
        binding.automaticallyClearWhenSetting.isEnabled = whenOptionEnabled
    }

    @StringRes
    private fun ClearWhatOption.nameStringResourceId(clearDuckAi: Boolean): Int {
        return when (this) {
            ClearWhatOption.CLEAR_NONE -> R.string.settingsAutomaticallyClearWhatOptionNone
            ClearWhatOption.CLEAR_TABS_ONLY -> R.string.settingsAutomaticallyClearWhatOptionTabs
            ClearWhatOption.CLEAR_TABS_AND_DATA -> if (clearDuckAi) {
                R.string.settingsAutomaticallyClearWhatOptionTabsAndDataAndChats
            } else {
                R.string.settingsAutomaticallyClearWhatOptionTabsAndData
            }
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

    private fun processCommand(it: FireButtonViewModel.Command) {
        when (it) {
            is FireButtonViewModel.Command.ShowClearWhatDialog -> launchAutomaticallyClearWhatDialog(it.option, it.clearDuckAi)
            is FireButtonViewModel.Command.ShowClearWhenDialog -> launchAutomaticallyClearWhenDialog(it.option)
            else -> { /* Handled by parent activity */ }
        }
    }

    private fun launchAutomaticallyClearWhatDialog(
        option: ClearWhatOption,
        clearDuckAi: Boolean,
    ) {
        val currentOption = option.getOptionIndex()
        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsAutomaticallyClearWhatDialogTitle)
            .setOptions(
                listOf(
                    R.string.settingsAutomaticallyClearWhatOptionNone,
                    R.string.settingsAutomaticallyClearWhatOptionTabs,
                    if (clearDuckAi) {
                        R.string.settingsAutomaticallyClearWhatOptionTabsAndDataAndChats
                    } else {
                        R.string.settingsAutomaticallyClearWhatOptionTabsAndData
                    },
                ),
                currentOption,
            )
            .setPositiveButton(R.string.settingsAutomaticallyClearingDialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val clearWhatSelected = selectedItem.getClearWhatOptionForIndex()
                        viewModel.onAutomaticallyWhatOptionSelected(clearWhatSelected)
                    }
                },
            )
            .show()
        pixel.fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_SHOWN)
    }

    private fun launchAutomaticallyClearWhenDialog(option: ClearWhenOption) {
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
                        viewModel.onAutomaticallyWhenOptionSelected(clearWhenSelected)
                    }
                },
            )
            .show()
        pixel.fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_SHOWN)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AutomaticDataClearingSettingsActivity::class.java)
        }
    }
}
