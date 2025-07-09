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

package com.duckduckgo.savedsites.impl.newtab

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.text.Spanned
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.recyclerviewext.GridColumnCalculator
import com.duckduckgo.common.ui.recyclerviewext.disableAnimation
import com.duckduckgo.common.ui.recyclerviewext.enableAnimation
import com.duckduckgo.common.ui.tabs.SwipingTabsFeatureProvider
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.ViewNewTabFavouritesSectionBinding
import com.duckduckgo.saved.sites.impl.databinding.ViewNewTabFavouritesTooltipBinding
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.api.views.FavoritesGridConfig
import com.duckduckgo.savedsites.api.views.FavoritesPlacement
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment.DeleteBookmarkListener
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment.EditSavedSiteListener
import com.duckduckgo.savedsites.impl.newtab.FavouriteNewTabSectionsItem.FavouriteItemFavourite
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.Command
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.Command.DeleteFavoriteConfirmation
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.Command.DeleteSavedSiteConfirmation
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.Command.ShowEditSavedSiteDialog
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.SavedSiteChangedViewState
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.ViewState
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionsAdapter.Companion.QUICK_ACCESS_GRID_MAX_COLUMNS
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionsAdapter.Companion.QUICK_ACCESS_ITEM_MAX_SIZE_DP
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class FavouritesNewTabSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var swipingTabsFeature: SwipingTabsFeatureProvider

    private var isExpandable = true
    private var showPlaceholders = false
    private var showDaxWhenEmpty = false
    private var placement: FavoritesPlacement = FavoritesPlacement.NEW_TAB_PAGE

    private val binding: ViewNewTabFavouritesSectionBinding by viewBinding()

    private lateinit var adapter: FavouritesNewTabSectionsAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val viewModel: FavouritesNewTabSectionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[FavouritesNewTabSectionViewModel::class.java]
    }

    private val expandAnimator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 250L
        addUpdateListener {
            val progress = it.animatedValue as Float
            binding.newTabFavoritesToggle.rotation = progress * 180
        }
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.FavouritesNewTabSectionView,
            0,
            R.style.Widget_DuckDuckGo_FavouritesNewTabSection,
        ).apply {
            isExpandable = getBoolean(R.styleable.FavouritesNewTabSectionView_isExpandable, true)
            showPlaceholders = getBoolean(R.styleable.FavouritesNewTabSectionView_showPlaceholders, true)
            placement = FavoritesPlacement.from(getInt(R.styleable.FavouritesNewTabSectionView_favoritesPlacement, 1))
            recycle()
        }
    }

    constructor(context: Context, config: FavoritesGridConfig?) : this(context, null, 0) {
        config?.let {
            isExpandable = it.isExpandable
            showPlaceholders = it.showPlaceholders
            showDaxWhenEmpty = it.showDaxWhenEmpty
            placement = it.placement
        }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        configureViews()

        viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        conflatedCommandJob += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun configureViews() {
        configureHomeTabQuickAccessGrid()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (swipingTabsFeature.isEnabled) {
            parent.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun configureHomeTabQuickAccessGrid() {
        configureQuickAccessGridLayout(binding.quickAccessRecyclerView)
        adapter = createQuickAccessAdapter { viewHolder ->
            binding.quickAccessRecyclerView.enableAnimation()
            itemTouchHelper.startDrag(viewHolder)
        }
        itemTouchHelper = createQuickAccessItemHolder(binding.quickAccessRecyclerView, adapter)
        binding.quickAccessRecyclerView.adapter = adapter
        binding.quickAccessRecyclerView.disableAnimation()

        binding.sectionHeaderOverflowIcon.setOnClickListener {
            showNewTabFavouritesPopup(it)
        }
    }

    private fun showNewTabFavouritesPopup(anchor: View) {
        val popupContent = ViewNewTabFavouritesTooltipBinding.inflate(LayoutInflater.from(context))
        popupContent.cardView.cardElevation = PopupMenu.POPUP_DEFAULT_ELEVATION_DP.toPx()

        val cornerRadius = resources.getDimension(com.duckduckgo.mobile.android.R.dimen.mediumShapeCornerRadius)
        popupContent.cardView.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(cornerRadius)
            .build()

        popupContent.cardContent.text = HtmlCompat.fromHtml(context.getString(R.string.newTabPageFavoritesTooltip), HtmlCompat.FROM_HTML_MODE_LEGACY)

        PopupWindow(
            popupContent.root,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            viewModel.onTooltipPressed()
            showAsDropDown(anchor)
        }
    }

    // BrowserTabFragment overrides onConfigurationChange, so we have to do this too
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        configureQuickAccessGridLayout(binding.quickAccessRecyclerView)
        restorePlaceholders()
    }

    private fun restorePlaceholders() {
        if (viewModel.viewState.value.favourites.isEmpty()) {
            val gridColumnCalculator = GridColumnCalculator(context)
            val numOfColumns = gridColumnCalculator.calculateNumberOfColumns(QUICK_ACCESS_ITEM_MAX_SIZE_DP, QUICK_ACCESS_GRID_MAX_COLUMNS)
            if (numOfColumns == QUICK_ACCESS_GRID_MAX_COLUMNS) {
                adapter.submitList(FavouritesNewTabSectionsAdapter.LANDSCAPE_PLACEHOLDERS)
            } else {
                adapter.submitList(FavouritesNewTabSectionsAdapter.PORTRAIT_PLACEHOLDERS)
            }
        }
    }

    private fun configureQuickAccessGridLayout(recyclerView: RecyclerView) {
        val gridColumnCalculator = GridColumnCalculator(context)
        val numOfColumns = gridColumnCalculator.calculateNumberOfColumns(QUICK_ACCESS_ITEM_MAX_SIZE_DP, QUICK_ACCESS_GRID_MAX_COLUMNS)
        val layoutManager = GridLayoutManager(context, numOfColumns)
        recyclerView.layoutManager = layoutManager
    }

    private fun createQuickAccessItemHolder(
        recyclerView: RecyclerView,
        apapter: FavouritesNewTabSectionsAdapter,
    ): ItemTouchHelper {
        return ItemTouchHelper(
            QuickAccessDragTouchItemListener(
                apapter,
                object : QuickAccessDragTouchItemListener.DragDropListener {
                    override fun onListChanged(listElements: List<FavouriteNewTabSectionsItem>) {
                        val favouriteItems = listElements.filterIsInstance<FavouriteNewTabSectionsItem.FavouriteItemFavourite>()
                        viewModel.onQuickAccessListChanged(favouriteItems.map { it.favorite })
                        recyclerView.disableAnimation()
                    }
                },
            ),
        ).also {
            it.attachToRecyclerView(recyclerView)
        }
    }

    private fun createQuickAccessAdapter(
        onMoveListener: (RecyclerView.ViewHolder) -> Unit,
    ): FavouritesNewTabSectionsAdapter {
        return FavouritesNewTabSectionsAdapter(
            findViewTreeLifecycleOwner()!!,
            faviconManager,
            onMoveListener,
            {
                submitUrl(it.url)
            },
            { viewModel.onEditSavedSiteRequested(it) },
            { viewModel.onDeleteFavoriteRequested(it) },
            { viewModel.onDeleteSavedSiteRequested(it) },
        )
    }

    private fun submitUrl(url: String) {
        viewModel.onFavoriteClicked(placement)
        context.startActivity(browserNav.openInCurrentTab(context, url))
    }

    private fun render(viewState: ViewState) {
        val gridColumnCalculator = GridColumnCalculator(context)
        val numOfColumns = gridColumnCalculator.calculateNumberOfColumns(QUICK_ACCESS_ITEM_MAX_SIZE_DP, QUICK_ACCESS_GRID_MAX_COLUMNS)

        if (viewState.favourites.isEmpty()) {
            binding.newTabFavoritesToggleLayout.gone()
            if (showPlaceholders) {
                binding.sectionHeaderLayout.show()
                binding.sectionHeaderLayout.setOnClickListener {
                    showNewTabFavouritesPopup(binding.sectionHeaderOverflowIcon)
                }
                if (numOfColumns == QUICK_ACCESS_GRID_MAX_COLUMNS) {
                    adapter.submitList(FavouritesNewTabSectionsAdapter.LANDSCAPE_PLACEHOLDERS)
                } else {
                    adapter.submitList(FavouritesNewTabSectionsAdapter.PORTRAIT_PLACEHOLDERS)
                }
            } else {
                adapter.submitList(emptyList())
            }
        } else {
            binding.sectionHeaderLayout.setOnClickListener(null)
            binding.sectionHeaderLayout.gone()

            if (isExpandable) {
                val numOfCollapsedItems = numOfColumns * 2
                val showToggle = viewState.favourites.size > numOfCollapsedItems
                val showCollapsed = !adapter.expanded

                if (showCollapsed) {
                    adapter.submitList(viewState.favourites.take(numOfCollapsedItems).map { FavouriteItemFavourite(it) })
                } else {
                    adapter.submitList(viewState.favourites.map { FavouriteItemFavourite(it) })
                }

                if (showToggle) {
                    binding.newTabFavoritesToggleLayout.show()
                    binding.newTabFavoritesToggleLayout.setOnClickListener {
                        if (adapter.expanded) {
                            expandAnimator.reverse()
                            adapter.submitList(viewState.favourites.take(numOfCollapsedItems).map { FavouriteItemFavourite(it) })
                            adapter.expanded = false
                            viewModel.onListCollapsed()
                        } else {
                            expandAnimator.start()
                            adapter.submitList(viewState.favourites.map { FavouriteItemFavourite(it) })
                            adapter.expanded = true
                            viewModel.onListExpanded()
                        }
                    }
                } else {
                    binding.newTabFavoritesToggleLayout.gone()
                }
            } else {
                binding.newTabFavoritesToggleLayout.gone()
                adapter.submitList(viewState.favourites.map { FavouriteItemFavourite(it) })
            }
            viewModel.onNewTabFavouritesShown()
        }
        binding.ddgLogo.isVisible = showDaxWhenEmpty && viewState.favourites.isEmpty()
    }

    private fun processCommands(command: Command) {
        when (command) {
            is DeleteFavoriteConfirmation -> confirmDeleteSavedSite(
                command.savedSite,
                context.getString(R.string.favoriteDeleteConfirmationMessage).toSpannable(),
            ) {
                viewModel.onDeleteFavoriteSnackbarDismissed(it)
            }

            is DeleteSavedSiteConfirmation -> confirmDeleteSavedSite(
                command.savedSite,
                context.getString(R.string.bookmarkDeleteConfirmationMessage, command.savedSite.title).html(context),
            ) {
                viewModel.onDeleteSavedSiteSnackbarDismissed(it)
            }

            is ShowEditSavedSiteDialog -> editSavedSite(command.savedSiteChangedViewState)
        }
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

    private fun editSavedSite(savedSiteChangedViewState: SavedSiteChangedViewState) {
        val addBookmarkDialog = EditSavedSiteDialogFragment.instance(
            savedSiteChangedViewState.savedSite,
            savedSiteChangedViewState.bookmarkFolder?.id ?: SavedSitesNames.BOOKMARKS_ROOT,
            savedSiteChangedViewState.bookmarkFolder?.name,
        )
        val btf = FragmentManager.findFragment<DuckDuckGoFragment>(this)
        addBookmarkDialog.show(btf.childFragmentManager, ADD_SAVED_SITE_FRAGMENT_TAG)
        addBookmarkDialog.listener = object : EditSavedSiteListener {
            override fun onFavouriteEdited(favorite: Favorite) {
                viewModel.onFavouriteEdited(favorite)
            }

            override fun onBookmarkEdited(
                bookmark: Bookmark,
                oldFolderId: String,
                updateFavorite: Boolean,
            ) {
                viewModel.onBookmarkEdited(bookmark, oldFolderId, updateFavorite)
            }

            override fun onFavoriteAdded() {
                viewModel.onFavoriteAdded()
            }

            override fun onFavoriteRemoved() {
                viewModel.onFavoriteRemoved()
            }
        }
        addBookmarkDialog.deleteBookmarkListener = object : DeleteBookmarkListener {
            override fun onSavedSiteDeleted(savedSite: SavedSite) {
                viewModel.onSavedSiteDeleted(savedSite)
            }

            override fun onSavedSiteDeleteCancelled() {}

            override fun onSavedSiteDeleteRequested() {}
        }
    }

    private companion object {
        const val ADD_SAVED_SITE_FRAGMENT_TAG = "ADD_SAVED_SITE"
    }
}

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageSectionPlugin::class,
    priority = NewTabPageSectionPlugin.PRIORITY_FAVOURITES,
)
class FavouritesNewTabSectionPlugin @Inject constructor(
    private val setting: NewTabFavouritesSectionSetting,
) : NewTabPageSectionPlugin {
    override val name = NewTabPageSection.FAVOURITES.name

    override fun getView(context: Context): View {
        return FavouritesNewTabSectionView(context)
    }

    override suspend fun isUserEnabled(): Boolean {
        return setting.self().isEnabled()
    }
}
