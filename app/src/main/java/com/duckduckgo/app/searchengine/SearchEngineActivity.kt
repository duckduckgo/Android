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
import android.view.inputmethod.EditorInfo
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
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.show
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
        binding.useStartPageToggle.quietlySetIsChecked(viewModel.useCustomStartPage) { _, enabled ->
            viewModel.onUseStartPageUpdated(enabled)
        }
        binding.startPageSetting.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                viewModel.onStartPageUrlUpdated(binding.startPageSetting.text)
                binding.startPageSetting.hideKeyboard()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        binding.searchEngineSetting.setClickListener { viewModel.userRequestedToChangeSearchEngine() }
        binding.searxInstanceSetting.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.onSearxInstanceUpdated(binding.searxInstanceSetting.text)
                binding.searxInstanceSetting.hideKeyboard()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    updateUseStartPage(it.useCustomStartPage)
                    updateSelectedStartPage(it.useCustomStartPage, it.selectedStartPageUrl)

                    updateSelectedSearchEngine(it.selectedSearchEngine)
                    updateSelectedSearxInstance(it.selectedSearchEngine, it.selectedSearxInstance)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    // updateUseStartPage(it.useCustomStartPage)
    // updateSelectedStartPage(it.useCustomStartPage, it.selectedStartPageUrl)
    private fun updateUseStartPage(useCustomStartPage: Boolean) {}

    private fun updateSelectedStartPage(useCustomStartPage: Boolean, startPageUrl: String) {
        binding.startPageSetting.isEditable = useCustomStartPage
        binding.startPageSetting.text = startPageUrl
    }

    private fun updateSelectedSearchEngine(searchEngine: SearchEngine) {
        val subtitle = getString(searchEngine.nameResId)
        binding.searchEngineSetting.setSecondaryText(subtitle)
    }

    private fun updateSelectedSearxInstance(searchEngine: SearchEngine, searxInstance: String) {
        if (searchEngine is SearxSearchEngine) {
            binding.searxInstanceSetting.show()
        } else {
            binding.searxInstanceSetting.hide()
        }
        binding.searxInstanceSetting.text = searxInstance
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
