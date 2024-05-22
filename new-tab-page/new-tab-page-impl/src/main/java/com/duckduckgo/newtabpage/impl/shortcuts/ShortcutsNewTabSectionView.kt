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

package com.duckduckgo.newtabpage.impl.shortcuts

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.recyclerviewext.GridColumnCalculator
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.newtabpage.impl.databinding.ViewNewTabShortcutsSectionBinding
import com.duckduckgo.newtabpage.impl.shortcuts.NewTabSectionsItem.ShortcutItem
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsAdapter.Companion.QUICK_ACCESS_GRID_MAX_COLUMNS
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsAdapter.Companion.QUICK_ACCESS_ITEM_MAX_SIZE_DP
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(ViewScope::class)
class ShortcutsNewTabSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var newTabShortcutsProvider: NewTabShortcutsProvider

    private val binding: ViewNewTabShortcutsSectionBinding by viewBinding()

    private lateinit var adapter: ShortcutsAdapter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        configureGrid()
        setupRemoteShortcuts()
    }

    private fun configureGrid() {
        configureQuickAccessGridLayout(binding.quickAccessRecyclerView)
        adapter = ShortcutsAdapter { shortcut ->
        }
        binding.quickAccessRecyclerView.adapter = adapter
    }

    private fun configureQuickAccessGridLayout(recyclerView: RecyclerView) {
        val gridColumnCalculator = GridColumnCalculator(context)
        val numOfColumns = gridColumnCalculator.calculateNumberOfColumns(QUICK_ACCESS_ITEM_MAX_SIZE_DP, QUICK_ACCESS_GRID_MAX_COLUMNS)
        val layoutManager = GridLayoutManager(context, numOfColumns)
        recyclerView.layoutManager = layoutManager
    }

    private fun setupRemoteShortcuts() {
        val shortcuts = newTabShortcutsProvider.provideShortcuts().map { ShortcutItem(it.getShortcut()) }
        adapter.submitList(shortcuts)
    }
}

@ContributesMultibinding(AppScope::class)
class FavouritesNewTabSectionPlugin @Inject constructor() : NewTabPageSectionPlugin {
    override val name = NewTabPageSection.SHORTCUTS.name

    override fun getView(context: Context): View {
        return ShortcutsNewTabSectionView(context)
    }
}
