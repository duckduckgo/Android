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
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.recyclerviewext.GridColumnCalculator
import com.duckduckgo.common.ui.recyclerviewext.disableAnimation
import com.duckduckgo.common.ui.recyclerviewext.enableAnimation
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.newtabpage.impl.databinding.ViewNewTabShortcutsSectionBinding
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsAdapter.Companion.SHORTCUT_GRID_MAX_COLUMNS
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsAdapter.Companion.SHORTCUT_ITEM_MAX_SIZE_DP
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsViewModel.ViewState
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class ShortcutsNewTabSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewNewTabShortcutsSectionBinding by viewBinding()

    private lateinit var adapter: ShortcutsAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val viewModel: ShortcutsViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ShortcutsViewModel::class.java]
    }

    private val conflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        configureGrid()

        conflatedJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)
    }

    override fun onDetachedFromWindow() {
        conflatedJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun render(viewState: ViewState) {
        adapter.submitList(viewState.shortcuts)
    }

    // BrowserTabFragment overrides onConfigurationChange, so we have to do this too
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        configureQuickAccessGridLayout(binding.quickAccessRecyclerView)
    }

    private fun configureGrid() {
        configureQuickAccessGridLayout(binding.quickAccessRecyclerView)
        adapter = createQuickAccessAdapter { viewHolder ->
            binding.quickAccessRecyclerView.enableAnimation()
            itemTouchHelper.startDrag(viewHolder)
        }

        itemTouchHelper = createQuickAccessItemHolder(binding.quickAccessRecyclerView, adapter)
        binding.quickAccessRecyclerView.adapter = adapter
        binding.quickAccessRecyclerView.disableAnimation()
    }

    private fun createQuickAccessAdapter(
        onMoveListener: (RecyclerView.ViewHolder) -> Unit,
    ): ShortcutsAdapter {
        return ShortcutsAdapter(onMoveListener) { shortcutPlugin ->
            viewModel.onShortcutPressed(shortcutPlugin)
        }
    }

    private fun configureQuickAccessGridLayout(recyclerView: RecyclerView) {
        val gridColumnCalculator = GridColumnCalculator(context)
        val numOfColumns = gridColumnCalculator.calculateNumberOfColumns(SHORTCUT_ITEM_MAX_SIZE_DP, SHORTCUT_GRID_MAX_COLUMNS)
        val layoutManager = GridLayoutManager(context, numOfColumns)
        recyclerView.layoutManager = layoutManager
    }

    private fun createQuickAccessItemHolder(
        recyclerView: RecyclerView,
        adapter: ShortcutsAdapter,
    ): ItemTouchHelper {
        return ItemTouchHelper(
            QuickAccessDragTouchItemListener(
                adapter,
                object : QuickAccessDragTouchItemListener.DragDropListener {
                    override fun onListChanged(listElements: List<ShortcutItem>) {
                        viewModel.onQuickAccessListChanged(listElements.map { it.plugin.getShortcut().name() })
                        recyclerView.disableAnimation()
                    }
                },
            ),
        ).also {
            it.attachToRecyclerView(recyclerView)
        }
    }

    companion object {
        private const val AI_CHAT_URL = "https://duckduckgo.com/chat"
    }
}

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageSectionPlugin::class,
    priority = NewTabPageSectionPlugin.PRIORITY_SHORTCUTS,
)
class ShortcutsNewTabSectionPlugin @Inject constructor(
    private val setting: NewTabShortcutDataStore,
) : NewTabPageSectionPlugin {

    override val name = NewTabPageSection.SHORTCUTS.name

    override fun getView(context: Context): View {
        return ShortcutsNewTabSectionView(context)
    }

    override suspend fun isUserEnabled(): Boolean {
        return setting.isEnabled()
    }
}
