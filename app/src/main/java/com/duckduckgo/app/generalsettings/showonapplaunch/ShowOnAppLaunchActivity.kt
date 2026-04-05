/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.generalsettings.showonapplaunch

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityShowOnAppLaunchSettingBinding
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchViewModel.Command.ShowTimeoutDialog
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(ShowOnAppLaunchScreenNoParams::class)
class ShowOnAppLaunchActivity : DuckDuckGoActivity() {

    private val viewModel: ShowOnAppLaunchViewModel by bindViewModel()
    private val binding: ActivityShowOnAppLaunchSettingBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        binding.specificPageUrlInput.setSelectAllOnFocus(true)

        configureUiEventHandlers()
        observeViewModel()
    }

    override fun onPause() {
        super.onPause()
        viewModel.setSpecificPageUrl(binding.specificPageUrlInput.text)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun configureUiEventHandlers() {
        binding.lastOpenedTabCheckListItem.setClickListener {
            viewModel.onShowOnAppLaunchOptionChanged(LastOpenedTab)
        }

        binding.newTabCheckListItem.setClickListener {
            viewModel.onShowOnAppLaunchOptionChanged(NewTabPage)
        }

        binding.specificPageCheckListItem.setClickListener {
            viewModel.onShowOnAppLaunchOptionChanged(SpecificPage(binding.specificPageUrlInput.text))
        }

        binding.specificPageUrlInput.addFocusChangedListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.onShowOnAppLaunchOptionChanged(
                    SpecificPage(binding.specificPageUrlInput.text),
                )
            }
        }

        binding.afterInactivityTimeoutRow.setClickListener {
            viewModel.onTimeoutRowClicked()
        }
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                when (viewState.selectedOption) {
                    LastOpenedTab -> {
                        uncheckNewTabCheckListItem()
                        uncheckSpecificPageCheckListItem()
                        binding.lastOpenedTabCheckListItem.setChecked(true)
                    }
                    NewTabPage -> {
                        uncheckLastOpenedTabCheckListItem()
                        uncheckSpecificPageCheckListItem()
                        binding.newTabCheckListItem.setChecked(true)
                    }
                    is SpecificPage -> {
                        uncheckLastOpenedTabCheckListItem()
                        uncheckNewTabCheckListItem()
                        with(binding) {
                            specificPageCheckListItem.setChecked(true)
                            specificPageUrlInput.isEnabled = true
                        }
                    }
                }

                if (viewState.showNTPAfterIdleReturn) {
                    setTitle(R.string.afterInactivityOptionTitle)
                    binding.afterInactivityTimeoutRow.setSecondaryText(viewState.selectedIdleThresholdSeconds.toTimeoutLabel())
                    binding.afterInactivityTimeoutRow.visibility = View.VISIBLE
                } else {
                    setTitle(R.string.showOnAppLaunchOptionTitle)
                    binding.afterInactivityTimeoutRow.visibility = View.GONE
                }

                if (binding.specificPageUrlInput.text.isBlank()) {
                    binding.specificPageUrlInput.text = viewState.specificPageUrl
                }
            }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    is ShowTimeoutDialog -> showTimeoutDialog(command.options, command.currentSelection)
                }
            }
            .launchIn(lifecycleScope)
    }

    @Suppress("DEPRECATION")
    private fun showTimeoutDialog(options: List<Long>, currentSelection: Long) {
        val labels = options.map { it.toTimeoutLabel() }
        val selectedIndex = options.indexOf(currentSelection).takeIf { it >= 0 }?.plus(1)
        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.afterInactivityDialogTitle)
            .setOptions(labels, selectedIndex)
            .setPositiveButton(com.duckduckgo.mobile.android.R.string.dialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        viewModel.onTimeoutSelected(options[selectedItem - 1])
                    }
                },
            )
            .show()
    }

    private fun Long.toTimeoutLabel(): String = when {
        this == 1L -> getString(R.string.afterInactivityTimeoutAlways)
        this < 3600L -> {
            val minutes = this / 60
            if (minutes == 1L) {
                getString(R.string.afterInactivityTimeout1Minute)
            } else {
                getString(R.string.afterInactivityTimeoutXMinutes, minutes)
            }
        }
        else -> {
            val hours = this / 3600
            if (hours == 1L) {
                getString(R.string.afterInactivityTimeout1Hour)
            } else {
                getString(R.string.afterInactivityTimeoutXHours, hours)
            }
        }
    }

    private fun uncheckLastOpenedTabCheckListItem() {
        binding.lastOpenedTabCheckListItem.setChecked(false)
    }

    private fun uncheckNewTabCheckListItem() {
        binding.newTabCheckListItem.setChecked(false)
    }

    private fun uncheckSpecificPageCheckListItem() {
        binding.specificPageCheckListItem.setChecked(false)
        binding.specificPageUrlInput.isEnabled = false
    }
}
