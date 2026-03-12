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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.ActivityShowOnAppLaunchSettingBinding
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.edgetoedge.api.EdgeToEdge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(ShowOnAppLaunchScreenNoParams::class)
class ShowOnAppLaunchActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdge: EdgeToEdge

    private val viewModel: ShowOnAppLaunchViewModel by bindViewModel()
    private val binding: ActivityShowOnAppLaunchSettingBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge.enableIfToggled(this)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        setupEdgeToEdge()

        binding.specificPageUrlInput.setSelectAllOnFocus(true)

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun setupEdgeToEdge() {
        if (!edgeToEdge.isEnabled()) return
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }
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

                if (binding.specificPageUrlInput.text.isBlank()) {
                    binding.specificPageUrlInput.text = viewState.specificPageUrl
                }
            }
            .launchIn(lifecycleScope)
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
