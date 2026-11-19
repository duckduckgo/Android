/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.ui

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayerSettingsNoParams
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.Disabled
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.Enabled
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.adblocking.impl.databinding.ActivityAdBlockingSettingsBinding
import com.duckduckgo.adblocking.impl.sponsorblock.SponsorBlockMode
import com.duckduckgo.adblocking.impl.ui.AdBlockingSettingsViewModel.DuckPlayerSection
import com.duckduckgo.browser.api.ui.BrowserScreens
import com.duckduckgo.codegen.annotations.ContributeToActivityStarter
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.StatusIndicatorView
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AdBlockingSettingsNoParams::class)
class AdBlockingSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: AdBlockingSettingsViewModel by bindViewModel()

    private val binding: ActivityAdBlockingSettingsBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val blockAdsToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onBlockAdsToggled(isChecked)
    }

    private val untilRelaunchToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onBlockAdsToggled(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        setTitle(R.string.ad_blocking_settings_title_v2)
        binding.adBlockingStatusIndicator.setStatus(StatusIndicatorView.Status.ALWAYS_ON)
        setupToolbar(binding.includeToolbar.toolbar)

        binding.duckPlayerEntry.setClickListener { viewModel.onDuckPlayerClicked() }
        binding.duckPlayerDescription.setOnClickListener { viewModel.onDuckPlayerClicked() }
        binding.duckPlayerSettingsLink.setOnClickListener { viewModel.onDuckPlayerClicked() }
        binding.sponsorBlockModeEntry.setClickListener { viewModel.onSponsorBlockModeClicked() }

        configureEdgeToEdgeInsets()
        observeViewModel()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = true)
    }

    private fun render(state: AdBlockingSettingsViewModel.ViewState) {
        binding.blockAdsToggle.quietlySetIsChecked(state.isEnabled, blockAdsToggleListener)
        renderDescription(state.showConsentDescription)
        binding.blockAdsToggle.isVisible = !state.disabledUntilRelaunch && !state.isContingencyMode
        binding.blockAdsToggleUntilRelaunch.isVisible = state.disabledUntilRelaunch && !state.isContingencyMode
        if (state.disabledUntilRelaunch) {
            binding.blockAdsToggleUntilRelaunch.quietlySetIsChecked(state.isEnabled, untilRelaunchToggleListener)
        }
        binding.duckPlayerEntry.isVisible = state.duckPlayerSection == DuckPlayerSection.Entry
        binding.duckPlayerDescription.isVisible = state.duckPlayerSection == DuckPlayerSection.Entry
        binding.duckPlayerSettingsLink.isVisible = state.duckPlayerSection == DuckPlayerSection.Link
        binding.duckPlayerEntry.setSecondaryText(
            when (state.duckPlayerMode) {
                Enabled -> getString(R.string.duck_player_mode_always)
                Disabled -> getString(R.string.duck_player_mode_never)
                else -> getString(R.string.duck_player_mode_always_ask)
            },
        )
        binding.sponsorBlockModeEntry.isVisible = state.sponsorBlockMode != null
        binding.sponsorBlockDescription.isVisible = state.sponsorBlockMode != null
        state.sponsorBlockMode?.let { binding.sponsorBlockModeEntry.setSecondaryText(getString(it.labelRes())) }
        if (state.isContingencyMode) {
            binding.adBlockingDescription.gone()
            binding.contingencyModeItem.show()
        } else {
            binding.contingencyModeItem.gone()
        }
    }

    private fun renderDescription(showConsentDescription: Boolean?) {
        if (showConsentDescription == null) {
            binding.adBlockingDescription.gone()
            return
        }
        val descriptionRes = if (showConsentDescription) {
            R.string.ad_blocking_settings_description_with_consent
        } else {
            R.string.ad_blocking_settings_description
        }
        binding.adBlockingDescription.addClickableSpan(
            textSequence = getText(descriptionRes),
            spans = listOf(
                "learn_more_link" to object : DuckDuckGoClickableSpan() {
                    override fun onClick(widget: View) {
                        viewModel.onLearnMoreClicked()
                    }
                },
            ),
        )
        binding.adBlockingDescription.show()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    is AdBlockingSettingsViewModel.Command.OpenLearnMore -> globalActivityStarter.start(
                        this,
                        BrowserScreens.WebViewActivityWithParams(
                            url = command.url,
                            screenTitle = getString(R.string.ad_blocking_settings_title_v2),
                        ),
                    )
                    AdBlockingSettingsViewModel.Command.OpenDuckPlayerSettings -> globalActivityStarter.start(
                        this,
                        DuckPlayerSettingsNoParams,
                    )
                    is AdBlockingSettingsViewModel.Command.ShowSponsorBlockModeDialog -> showSponsorBlockModeDialog(command.mode)
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun showSponsorBlockModeDialog(currentMode: SponsorBlockMode) {
        val modes = SponsorBlockMode.entries
        RadioListAlertDialogBuilder(this)
            .setTitle(getString(R.string.sponsor_block_settings_title))
            .setOptions(modes.map { it.labelRes() }, modes.indexOf(currentMode) + 1)
            .setPositiveButton(CommonR.string.dialogSave)
            .setNegativeButton(CommonR.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        viewModel.onSponsorBlockModeSelected(modes[selectedItem - 1])
                    }
                },
            )
            .show()
    }

    private fun SponsorBlockMode.labelRes(): Int = when (this) {
        SponsorBlockMode.ALWAYS_ASK -> R.string.sponsor_block_mode_always_ask
        SponsorBlockMode.ALWAYS_SKIP -> R.string.sponsor_block_mode_always_skip
        SponsorBlockMode.OFF -> R.string.sponsor_block_mode_off
    }
}
