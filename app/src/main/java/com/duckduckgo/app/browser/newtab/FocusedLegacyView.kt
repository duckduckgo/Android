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

package com.duckduckgo.app.browser.newtab

import android.annotation.SuppressLint
import android.content.Context
import android.text.Spanned
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.text.toSpannable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewFocusedViewLegacyBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter.Companion.QUICK_ACCESS_GRID_MAX_COLUMNS
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter.Companion.QUICK_ACCESS_ITEM_MAX_SIZE_DP
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.browser.newtab.FocusedLegacyViewModel.Command
import com.duckduckgo.app.browser.newtab.FocusedLegacyViewModel.Command.DeleteFavoriteConfirmation
import com.duckduckgo.app.browser.newtab.FocusedLegacyViewModel.Command.DeleteSavedSiteConfirmation
import com.duckduckgo.app.browser.newtab.FocusedLegacyViewModel.Command.ShowEditSavedSiteDialog
import com.duckduckgo.app.browser.newtab.FocusedLegacyViewModel.ViewState
import com.duckduckgo.app.browser.newtab.QuickAccessDragTouchItemListener.DragDropListener
import com.duckduckgo.app.browser.viewstate.SavedSiteChangedViewState
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.common.ui.recyclerviewext.disableAnimation
import com.duckduckgo.common.ui.recyclerviewext.enableAnimation
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.impl.edit.EditBookmarkScreens.EditBookmarkScreen
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class FocusedLegacyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var gridViewColumnCalculator: GridViewColumnCalculator

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private var coroutineScope: CoroutineScope? = null

    private val binding: ViewFocusedViewLegacyBinding by viewBinding()

    private lateinit var quickAccessAdapter: FavoritesQuickAccessAdapter
    private lateinit var quickAccessItemTouchHelper: ItemTouchHelper

    private val viewModel: FocusedLegacyViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[FocusedLegacyViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)

        configureViews()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        coroutineScope?.cancel()
        coroutineScope = null
    }

    private fun configureViews() {
        configureHomeTabQuickAccessGrid()
        setOnClickListener(null)
    }

    private fun configureHomeTabQuickAccessGrid() {
        configureQuickAccessGridLayout(binding.quickAccessSuggestionsRecyclerView)
        quickAccessAdapter = createQuickAccessAdapter(originPixel = AppPixelName.FAVORITE_OMNIBAR_ITEM_PRESSED) { viewHolder ->
            binding.quickAccessSuggestionsRecyclerView.enableAnimation()
            quickAccessItemTouchHelper.startDrag(viewHolder)
        }
        quickAccessItemTouchHelper = createQuickAccessItemHolder(binding.quickAccessSuggestionsRecyclerView, quickAccessAdapter)
        binding.quickAccessSuggestionsRecyclerView.adapter = quickAccessAdapter
        binding.quickAccessSuggestionsRecyclerView.disableAnimation()
    }

    private fun createQuickAccessAdapter(
        originPixel: AppPixelName,
        onMoveListener: (RecyclerView.ViewHolder) -> Unit,
    ): FavoritesQuickAccessAdapter {
        return FavoritesQuickAccessAdapter(
            findViewTreeLifecycleOwner()!!,
            faviconManager,
            onMoveListener,
            {
                pixel.fire(originPixel)
                submitUrl(it.favorite.url)
            },
            { viewModel.onEditSavedSiteRequested(it.favorite) },
            { viewModel.onDeleteFavoriteRequested(it.favorite) },
            { viewModel.onDeleteSavedSiteRequested(it.favorite) },
        )
    }

    private fun submitUrl(url: String) {
        context.startActivity(browserNav.openInCurrentTab(context, url))
    }

    private fun createQuickAccessItemHolder(
        recyclerView: RecyclerView,
        apapter: FavoritesQuickAccessAdapter,
    ): ItemTouchHelper {
        return ItemTouchHelper(
            QuickAccessDragTouchItemListener(
                apapter,
                object : DragDropListener {
                    override fun onListChanged(listElements: List<QuickAccessFavorite>) {
                        viewModel.onQuickAccessListChanged(listElements)
                        recyclerView.disableAnimation()
                    }
                },
            ),
        ).also {
            it.attachToRecyclerView(recyclerView)
        }
    }

    private fun configureQuickAccessGridLayout(recyclerView: RecyclerView) {
        val numOfColumns = gridViewColumnCalculator.calculateNumberOfColumns(QUICK_ACCESS_ITEM_MAX_SIZE_DP, QUICK_ACCESS_GRID_MAX_COLUMNS)
        val layoutManager = GridLayoutManager(context, numOfColumns)
        recyclerView.layoutManager = layoutManager
        val sidePadding = gridViewColumnCalculator.calculateSidePadding(QUICK_ACCESS_ITEM_MAX_SIZE_DP, numOfColumns)
        recyclerView.setPadding(sidePadding, recyclerView.paddingTop, sidePadding, recyclerView.paddingBottom)
    }

    private fun render(viewState: ViewState) {
        if (viewState.favourites.isEmpty()) {
            binding.quickAccessSuggestionsRecyclerView.gone()
        } else {
            binding.quickAccessSuggestionsRecyclerView.show()
            quickAccessAdapter.submitList(viewState.favourites.map { QuickAccessFavorite(it) })
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            is ShowEditSavedSiteDialog -> editSavedSite(command.savedSiteChangedViewState)
            is DeleteFavoriteConfirmation -> confirmDeleteSavedSite(
                command.savedSite,
                context.getString(R.string.favoriteDeleteConfirmationMessage).toSpannable(),
            ) {
                viewModel.onDeleteFavoriteSnackbarDismissed(command.savedSite)
            }
            is DeleteSavedSiteConfirmation -> confirmDeleteSavedSite(
                command.savedSite,
                context.getString(R.string.bookmarkDeleteConfirmationMessage, command.savedSite.title).html(context),
            ) {
                viewModel.onDeleteSavedSiteSnackbarDismissed(command.savedSite)
            }
        }
    }

    private fun editSavedSite(savedSiteChangedViewState: SavedSiteChangedViewState) {
        globalActivityStarter.start(context, EditBookmarkScreen(savedSiteChangedViewState.savedSite.id))
        // val addBookmarkDialog = EditSavedSiteDialogFragment.instance(
        //     savedSiteChangedViewState.savedSite,
        //     savedSiteChangedViewState.bookmarkFolder?.id ?: SavedSitesNames.BOOKMARKS_ROOT,
        //     savedSiteChangedViewState.bookmarkFolder?.name,
        // )
        // val btf = findFragment<DuckDuckGoFragment>()
        // addBookmarkDialog.show(btf.childFragmentManager, ADD_SAVED_SITE_FRAGMENT_TAG)
        // addBookmarkDialog.listener = viewModel
        // addBookmarkDialog.deleteBookmarkListener = viewModel
    }

    private fun confirmDeleteSavedSite(
        savedSite: SavedSite,
        message: Spanned,
        onDeleteSnackbarDismissed: (SavedSite) -> Unit,
    ) {
        binding.root.makeSnackbarWithNoBottomInset(
            message,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.undoDelete(savedSite)
        }
            .addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        if (event != DISMISS_EVENT_ACTION) {
                            onDeleteSnackbarDismissed(savedSite)
                        }
                    }
                },
            )
            .show()
    }
}
