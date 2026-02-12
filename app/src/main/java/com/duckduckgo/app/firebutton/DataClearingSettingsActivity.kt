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

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityDataClearingSettingsBinding
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesActivity
import com.duckduckgo.app.firebutton.DataClearingSettingsViewModel.Command
import com.duckduckgo.app.global.view.FireDialogProvider
import com.duckduckgo.app.global.view.FireDialogProvider.FireDialogOrigin.SETTINGS
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.FireAnimation.HeroAbstract.getAnimationForIndex
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DataClearingSettingsScreenNoParams::class)
class DataClearingSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var fireDialogProvider: FireDialogProvider

    private val viewModel: DataClearingSettingsViewModel by bindViewModel()
    private val binding: ActivityDataClearingSettingsBinding by viewBinding()

    private val clearDuckAiDataToggleListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.onClearDuckAiDataToggled(isChecked)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        supportActionBar?.setTitle(R.string.dataClearingActivityTitle)

        configureUiEventHandlers()
        observeViewModel()

        intent?.getStringExtra(LAUNCH_FROM_NOTIFICATION_PIXEL_NAME)?.let {
            viewModel.onLaunchedFromNotification(it)
        }
    }

    private fun configureUiEventHandlers() {
        with(binding) {
            fireproofWebsites.setClickListener { viewModel.onFireproofWebsitesClicked() }
            automaticDataClearingSetting.setClickListener { viewModel.onAutomaticDataClearingClicked() }
            selectedFireAnimationSetting.setClickListener { viewModel.userRequestedToChangeFireAnimation() }
            clearDuckAiDataSetting.setOnCheckedChangeListener(clearDuckAiDataToggleListener)
            clearDataAction.setClickListener { viewModel.onClearDataActionClicked() }
        }
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    updateAutomaticClearingStatus(it.automaticallyClearingEnabled)
                    updateSelectedFireAnimation(it.selectedFireAnimation)
                    updateClearDuckAiDataSetting(it.clearDuckAiData, it.showClearDuckAiDataSetting)
                    updateClearDataAction(it.clearDuckAiData)
                    updateFireproofWebsitesCount(it.fireproofWebsitesCount)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun updateAutomaticClearingStatus(enabled: Boolean) {
        val statusText = if (enabled) {
            getString(R.string.dataClearingAutomaticClearingOn)
        } else {
            getString(R.string.dataClearingAutomaticClearingOff)
        }
        binding.automaticDataClearingSetting.setSecondaryText(statusText)
    }

    private fun updateSelectedFireAnimation(fireAnimation: FireAnimation) {
        val subtitle = getString(fireAnimation.nameResId)
        binding.selectedFireAnimationSetting.setSecondaryText(subtitle)
    }

    private fun updateClearDuckAiDataSetting(
        enabled: Boolean,
        isVisible: Boolean,
    ) {
        binding.clearDuckAiDataSetting.quietlySetIsChecked(enabled, clearDuckAiDataToggleListener)
        binding.clearDuckAiDataSetting.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun updateClearDataAction(clearDuckAiData: Boolean) {
        if (clearDuckAiData) {
            binding.clearDataAction.setPrimaryText(resources.getString(R.string.fireClearAllPlusDuckChats))
        } else {
            binding.clearDataAction.setPrimaryText(resources.getString(R.string.fireClearAll))
        }
    }

    private fun updateFireproofWebsitesCount(count: Int) {
        val subtitle = resources.getQuantityString(R.plurals.dataClearingExcludedSites, count, count)
        binding.fireproofWebsites.setSecondaryText(subtitle)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is Command.LaunchFireproofWebsites -> launchFireproofWebsites()
            is Command.LaunchFireAnimationSettings -> launchFireAnimationSelector(it.animation)
            is Command.LaunchFireDialog -> launchFireDialog()
            is Command.LaunchAutomaticDataClearingSettings -> launchAutomaticDataClearingSettings()
        }
    }

    private fun launchFireproofWebsites() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(FireproofWebsitesActivity.intent(this), options)
    }

    private fun launchFireAnimationSelector(animation: FireAnimation) {
        val currentAnimationOption = animation.getOptionIndex()

        com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsSelectFireAnimationDialog)
            .setOptions(
                listOf(
                    R.string.settingsHeroFireAnimation,
                    R.string.settingsHeroWaterAnimation,
                    R.string.settingsHeroAbstractAnimation,
                    R.string.settingsNoneAnimation,
                ),
                currentAnimationOption,
            )
            .setPositiveButton(R.string.settingsSelectFireAnimationDialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val selectedAnimation = selectedItem.getAnimationForIndex()
                        viewModel.onFireAnimationSelected(selectedAnimation)
                    }

                    override fun onRadioItemSelected(selectedItem: Int) {
                        val selectedAnimation = selectedItem.getAnimationForIndex()
                        if (selectedAnimation != FireAnimation.None) {
                            startActivity(com.duckduckgo.app.settings.FireAnimationActivity.intent(baseContext, selectedAnimation))
                        }
                    }
                },
            )
            .show()
    }

    private fun launchFireDialog() {
        lifecycleScope.launch {
            val dialog = fireDialogProvider.createFireDialog(SETTINGS)
            dialog.show(supportFragmentManager)
        }
    }

    private fun launchAutomaticDataClearingSettings() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(AutomaticDataClearingSettingsActivity.intent(this), options)
    }

    companion object {
        const val LAUNCH_FROM_NOTIFICATION_PIXEL_NAME = "LAUNCH_FROM_NOTIFICATION_PIXEL_NAME"

        fun intent(context: Context): Intent {
            return Intent(context, DataClearingSettingsActivity::class.java)
        }
    }
}
