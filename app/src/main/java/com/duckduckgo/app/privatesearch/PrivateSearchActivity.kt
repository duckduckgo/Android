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

package com.duckduckgo.app.privatesearch

import android.os.Bundle
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityPrivateSearchBinding
import com.duckduckgo.app.privatesearch.PrivateSearchViewModel.Command
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.SettingsPageFeature
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PrivateSearchScreenNoParams::class)
class PrivateSearchActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: PrivateSearchViewModel by bindViewModel()
    private val binding: ActivityPrivateSearchBinding by viewBinding()

    private val autocompleteToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteSettingChanged(isChecked)
    }

    private val autocompleteRecentlyVisitedSitesToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteRecentlyVisitedSitesSettingChanged(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        binding.privateSearchAutocompleteToggle.setOnCheckedChangeListener(autocompleteToggleListener)
        binding.privateSearchAutocompleteRecentlyVisitedSitesToggle.setOnCheckedChangeListener(autocompleteRecentlyVisitedSitesToggleListener)
        binding.privateSearchMoreSearchSettings.setOnClickListener { viewModel.onPrivateSearchMoreSearchSettingsClicked() }
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState?.let {
                    binding.privateSearchAutocompleteToggle.quietlySetIsChecked(
                        newCheckedState = it.autoCompleteSuggestionsEnabled,
                        changeListener = autocompleteToggleListener,
                    )
                    if (it.storeHistoryEnabled) {
                        binding.privateSearchAutocompleteRecentlyVisitedSites.isVisible = true
                        binding.privateSearchAutocompleteRecentlyVisitedSitesToggle.quietlySetIsChecked(
                            newCheckedState = it.autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled,
                            changeListener = autocompleteRecentlyVisitedSitesToggleListener,
                        )
                        binding.privateSearchAutocompleteRecentlyVisitedSitesToggle.isEnabled = it.autoCompleteSuggestionsEnabled
                    } else {
                        binding.privateSearchAutocompleteRecentlyVisitedSites.isVisible = false
                    }
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is Command.LaunchCustomizeSearchWebPage -> launchCustomizeSearchWebPage()
        }
    }

    private fun launchCustomizeSearchWebPage() {
        globalActivityStarter.start(
            this,
            WebViewActivityWithParams(
                url = DUCKDUCKGO_SETTINGS_WEB_LINK,
                getString(R.string.privateSearchMoreSearchSettingsTitle),
            ),
        )
    }

    companion object {
        private const val DUCKDUCKGO_SETTINGS_WEB_LINK = "https://duckduckgo.com/settings"
    }
}
