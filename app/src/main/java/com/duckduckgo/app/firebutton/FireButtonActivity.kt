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

package com.duckduckgo.app.firebutton

import android.app.ActivityOptions
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityDataClearingBinding
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesActivity
import com.duckduckgo.app.firebutton.FireButtonViewModel.AutomaticallyClearData
import com.duckduckgo.app.firebutton.FireButtonViewModel.Command
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.FireAnimationActivity
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.FireAnimation.HeroAbstract.getAnimationForIndex
import com.duckduckgo.app.settings.clear.getClearWhatOptionForIndex
import com.duckduckgo.app.settings.clear.getClearWhenForIndex
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(FireButtonScreenNoParams::class)
class FireButtonActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private val viewModel: FireButtonViewModel by bindViewModel()
    private val binding: ActivityDataClearingBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        supportActionBar?.setTitle(R.string.dataClearingActivityTitle)

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        with(binding) {
            fireproofWebsites.setClickListener { viewModel.onFireproofWebsitesClicked() }
            automaticallyClearWhatSetting.setClickListener { viewModel.onAutomaticallyClearWhatClicked() }
            automaticallyClearWhenSetting.setClickListener { viewModel.onAutomaticallyClearWhenClicked() }
            selectedFireAnimationSetting.setClickListener { viewModel.userRequestedToChangeFireAnimation() }
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    updateAutomaticClearDataOptions(it.automaticallyClearData)
                    updateSelectedFireAnimation(it.selectedFireAnimation)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun updateAutomaticClearDataOptions(automaticallyClearData: AutomaticallyClearData) {
        val clearWhatSubtitle = getString(automaticallyClearData.clearWhatOption.nameStringResourceId())
        binding.automaticallyClearWhatSetting.setSecondaryText(clearWhatSubtitle)

        val clearWhenSubtitle = getString(automaticallyClearData.clearWhenOption.nameStringResourceId())
        binding.automaticallyClearWhenSetting.setSecondaryText(clearWhenSubtitle)

        val whenOptionEnabled = automaticallyClearData.clearWhenOptionEnabled
        binding.automaticallyClearWhenSetting.isEnabled = whenOptionEnabled
    }

    private fun updateSelectedFireAnimation(fireAnimation: FireAnimation) {
        val subtitle = getString(fireAnimation.nameResId)
        binding.selectedFireAnimationSetting.setSecondaryText(subtitle)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is Command.LaunchFireproofWebsites -> launchFireproofWebsites()
            is Command.ShowClearWhatDialog -> launchAutomaticallyClearWhatDialog(it.option)
            is Command.ShowClearWhenDialog -> launchAutomaticallyClearWhenDialog(it.option)
            is Command.LaunchFireAnimationSettings -> launchFireAnimationSelector(it.animation)
        }
    }

    @StringRes
    private fun ClearWhatOption.nameStringResourceId(): Int {
        return when (this) {
            ClearWhatOption.CLEAR_NONE -> R.string.settingsAutomaticallyClearWhatOptionNone
            ClearWhatOption.CLEAR_TABS_ONLY -> R.string.settingsAutomaticallyClearWhatOptionTabs
            ClearWhatOption.CLEAR_TABS_AND_DATA -> R.string.settingsAutomaticallyClearWhatOptionTabsAndData
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

    private fun launchFireproofWebsites() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(FireproofWebsitesActivity.intent(this), options)
    }

    private fun launchAutomaticallyClearWhatDialog(option: ClearWhatOption) {
        val currentOption = option.getOptionIndex()
        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsAutomaticallyClearWhatDialogTitle)
            .setOptions(
                listOf(
                    R.string.settingsAutomaticallyClearWhatOptionNone,
                    R.string.settingsAutomaticallyClearWhatOptionTabs,
                    R.string.settingsAutomaticallyClearWhatOptionTabsAndData,
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
                        Timber.d("Option selected: $clearWhenSelected")
                    }
                },
            )
            .show()
        pixel.fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_SHOWN)
    }

    private fun launchFireAnimationSelector(animation: FireAnimation) {
        val currentAnimationOption = animation.getOptionIndex()

        RadioListAlertDialogBuilder(this)
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
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val selectedAnimation = selectedItem.getAnimationForIndex()

                        viewModel.onFireAnimationSelected(selectedAnimation)
                    }

                    override fun onRadioItemSelected(selectedItem: Int) {
                        val selectedAnimation = selectedItem.getAnimationForIndex()
                        if (selectedAnimation != FireAnimation.None) {
                            startActivity(FireAnimationActivity.intent(baseContext, selectedAnimation))
                        }
                    }
                },
            )
            .show()
    }
}
