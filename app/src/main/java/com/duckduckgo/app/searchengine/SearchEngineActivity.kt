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

package com.duckduckgo.app.searchengine

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivitySearchEngineBinding
import com.duckduckgo.app.searchengine.DuckDuckGoSearchEngine.getSearchEngineForIndex
import com.duckduckgo.app.searchengine.SearchEngineViewModel.Command
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
@ContributeToActivityStarter(SearchEngineScreenNoParams::class)
class SearchEngineActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private val viewModel: SearchEngineViewModel by bindViewModel()
    private val binding: ActivitySearchEngineBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        binding.searchEngineSetting.setClickListener { viewModel.userRequestedToChangeSearchEngine() }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    updateSelectedSearchEngine(it.selectedSearchEngine)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun updateSelectedSearchEngine(searchEngine: SearchEngine) {
        val subtitle = getString(searchEngine.nameResId)
        binding.searchEngineSetting.setSecondaryText(subtitle)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is Command.LaunchSearchEngineSettings -> launchSearchEngineSelector(it.searchEngine)
        }
    }

    private fun launchSearchEngineSelector(searchEngine: SearchEngine) {
        val currentSearchEngineOption = searchEngine.getOptionIndex()

        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsSelectFireAnimationDialog)
            .setOptions(
                listOf(
                    R.string.settingsSearchEngineDuckduckgo,
                    R.string.settingsSearchEngineSearx
                ),
                currentSearchEngineOption,
            )
            .setPositiveButton(R.string.settingsSelectSearchEngineDialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val selectedAnimation = selectedItem.getSearchEngineForIndex(viewModel.searxInstance)
                        viewModel.onSearchEngineSelected(selectedAnimation)
                    }
                },
            )
            .show()
    }
}
