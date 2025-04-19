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

package com.duckduckgo.newtabpage.impl.settings

import android.os.Bundle
import android.view.View
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.NewTabSettingsScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.recyclerviewext.GridColumnCalculator
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.duckduckgo.newtabpage.impl.databinding.ActivityNewTabSettingsBinding
import com.duckduckgo.newtabpage.impl.settings.DragLinearLayout.OnViewSwapListener
import com.duckduckgo.newtabpage.impl.settings.NewTabSettingsViewModel.ViewState
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsAdapter.Companion.SHORTCUT_GRID_MAX_COLUMNS
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsAdapter.Companion.SHORTCUT_ITEM_MAX_SIZE_DP
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NewTabSettingsScreenNoParams::class, screenName = "newtabsettings")
class NewTabSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: NewTabSettingsViewModel by bindViewModel()
    private val binding: ActivityNewTabSettingsBinding by viewBinding()

    private lateinit var adapter: ManageShortcutsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureGrid()

        binding.newTabSettingSectionsLayout.setLongClickDrag(true)
        binding.newTabSettingSectionsLayout.setViewSwapListener(
            object : OnViewSwapListener {
                override fun onSwap(
                    firstView: View?,
                    firstPosition: Int,
                    secondView: View?,
                    secondPosition: Int,
                ) {
                    super.onSwap(firstView, firstPosition, secondView, secondPosition)
                    viewModel.onSectionsSwapped(firstPosition, secondPosition)
                }
            },
        )

        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)
    }

    private fun configureGrid() {
        val gridColumnCalculator = GridColumnCalculator(this)
        val numOfColumns = gridColumnCalculator.calculateNumberOfColumns(SHORTCUT_ITEM_MAX_SIZE_DP, SHORTCUT_GRID_MAX_COLUMNS)
        val layoutManager = GridLayoutManager(this, numOfColumns)
        binding.shortcutsList.layoutManager = layoutManager
        adapter = ManageShortcutsAdapter {
            viewModel.onShortcutSelected(it)
        }
        binding.shortcutsList.adapter = adapter
    }

    private fun render(viewState: ViewState) {
        // we only want to make changes if the sections have changed
        val existingSections = binding.newTabSettingSectionsLayout.children.map { it.tag }.toMutableList()
        val newSections = viewState.sections.map { it.name }
        if (existingSections != newSections) {
            binding.newTabSettingSectionsLayout.removeAllViews()
        }

        // we will only add shortcuts that haven't been added yet
        viewState.sections.forEach { section ->
            val sectionView = binding.newTabSettingSectionsLayout.findViewWithTag<View>(section.name)
            if (sectionView == null) {
                val newSection = section.getView(this).also { it?.tag = section.name }
                binding.newTabSettingSectionsLayout.addDragView(newSection, newSection)
            }
        }

        if (viewState.shortcutsManagementEnabled) {
            binding.shortcutsList.alpha = 1f
        } else {
            binding.shortcutsList.alpha = 0.5f
        }
        binding.shortcutsList.isEnabled = viewState.shortcutsManagementEnabled
        adapter.submitList(viewState.shortcuts)
    }
}

@ContributesPluginPoint(
    scope = AppScope::class,
    boundType = NewTabPageSectionSettingsPlugin::class,
)
private interface NewTabPageSectionSettingsPluginPointTrigger
