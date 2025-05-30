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

package com.duckduckgo.app.generalsettings

import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityGeneralSettingsBinding
import com.duckduckgo.app.browser.webview.SCAM_PROTECTION_LEARN_MORE_URL
import com.duckduckgo.app.generalsettings.GeneralSettingsViewModel.Command
import com.duckduckgo.app.generalsettings.GeneralSettingsViewModel.Command.LaunchShowOnAppLaunchScreen
import com.duckduckgo.app.generalsettings.GeneralSettingsViewModel.Command.OpenMaliciousLearnMore
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchScreenNoParams
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.fadeTransitionConfig
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(GeneralSettingsScreenNoParams::class)
class GeneralSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: GeneralSettingsViewModel by bindViewModel()
    private val binding: ActivityGeneralSettingsBinding by viewBinding()

    private val autocompleteToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteSettingChanged(isChecked)
    }

    private val autocompleteRecentlyVisitedSitesToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteRecentlyVisitedSitesSettingChanged(isChecked)
    }

    private val maliciousSiteProtectionToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onMaliciousSiteProtectionSettingChanged(isChecked)
    }

    private val voiceSearchChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onVoiceSearchChanged(isChecked)
    }

    private val showOnAppLaunchClickListener = OnClickListener {
        viewModel.onShowOnAppLaunchButtonClick()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        binding.maliciousLearnMore.addClickableSpan(
            textSequence = getText(R.string.maliciousSiteSettingLearnMore),
            spans = listOf(
                "learn_more_link" to object : DuckDuckGoClickableSpan() {
                    override fun onClick(widget: View) {
                        viewModel.maliciousSiteLearnMoreClicked()
                    }
                },
            ),
        )

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        binding.showOnAppLaunchButton.setOnClickListener(showOnAppLaunchClickListener)
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState?.let {
                    binding.autocompleteToggle.quietlySetIsChecked(
                        newCheckedState = it.autoCompleteSuggestionsEnabled,
                        changeListener = autocompleteToggleListener,
                    )
                    if (it.storeHistoryEnabled) {
                        binding.autocompleteRecentlyVisitedSitesToggle.isVisible = true
                        binding.autocompleteRecentlyVisitedSitesToggle.quietlySetIsChecked(
                            newCheckedState = it.autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled,
                            changeListener = autocompleteRecentlyVisitedSitesToggleListener,
                        )
                        binding.autocompleteRecentlyVisitedSitesToggle.isEnabled = it.autoCompleteSuggestionsEnabled
                    } else {
                        binding.autocompleteRecentlyVisitedSitesToggle.isVisible = false
                    }
                    if (it.maliciousSiteProtectionFeatureAvailable) {
                        binding.maliciousDisabledMessage.isVisible = !it.maliciousSiteProtectionEnabled
                        binding.maliciousToggle.quietlySetIsChecked(
                            newCheckedState = it.maliciousSiteProtectionEnabled,
                            changeListener = maliciousSiteProtectionToggleListener,
                        )
                    } else {
                        binding.maliciousDisabledMessage.isVisible = false
                    }
                    binding.maliciousLearnMore.isVisible = it.maliciousSiteProtectionFeatureAvailable
                    binding.maliciousSiteDivider.isVisible = it.maliciousSiteProtectionFeatureAvailable
                    binding.maliciousSiteHeading.isVisible = it.maliciousSiteProtectionFeatureAvailable
                    binding.maliciousToggle.isVisible = it.maliciousSiteProtectionFeatureAvailable

                    if (it.showVoiceSearch) {
                        binding.voiceSearchToggle.isVisible = true
                        binding.voiceSearchToggle.quietlySetIsChecked(viewState.voiceSearchEnabled, voiceSearchChangeListener)
                    }

                    binding.showOnAppLaunchButton.isVisible = it.isShowOnAppLaunchOptionVisible
                    setShowOnAppLaunchOptionSecondaryText(viewState.showOnAppLaunchSelectedOption)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun setShowOnAppLaunchOptionSecondaryText(showOnAppLaunchOption: ShowOnAppLaunchOption) {
        val optionString = when (showOnAppLaunchOption) {
            is LastOpenedTab -> getString(R.string.showOnAppLaunchOptionLastOpenedTab)
            is NewTabPage -> getString(R.string.showOnAppLaunchOptionNewTabPage)
            is SpecificPage -> showOnAppLaunchOption.url
        }
        binding.showOnAppLaunchButton.setSecondaryText(optionString)
    }

    private fun processCommand(command: Command) {
        when (command) {
            LaunchShowOnAppLaunchScreen -> {
                globalActivityStarter.start(this, ShowOnAppLaunchScreenNoParams, fadeTransitionConfig())
            }
            OpenMaliciousLearnMore -> {
                globalActivityStarter.start(
                    this,
                    WebViewActivityWithParams(
                        url = SCAM_PROTECTION_LEARN_MORE_URL,
                        screenTitle = getString(R.string.maliciousSiteLearnMoreTitle),
                    ),
                )
            }
        }
    }
}
