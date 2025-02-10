/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityTabSwitcherBinding
import com.duckduckgo.app.browser.databinding.PopupTabsMenuBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.downloads.DownloadsActivity
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.TabSwitcherAnimationFeature
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackerAnimationInfoPanel
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.Close
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.CloseAllTabsRequest
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.DismissAnimatedTileDismissalDialog
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShowAnimatedTileDismissalDialog
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
class TabSwitcherActivity : DuckDuckGoActivity(), TabSwitcherListener, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + dispatchers.main()

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var clearPersonalDataAction: ClearDataAction

    @Inject
    lateinit var gridViewColumnCalculator: GridViewColumnCalculator

    @Inject
    lateinit var webViewPreviewPersister: WebViewPreviewPersister

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var userEventsStore: UserEventsStore

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var fireButtonStore: FireButtonStore

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var duckChat: DuckChat

    @Inject
    lateinit var tabSwitcherAnimationFeature: TabSwitcherAnimationFeature

    @Inject
    lateinit var trackerCountAnimator: TrackerCountAnimator

    @Inject
    lateinit var tabManagerFeatureFlags: TabManagerFeatureFlags

    private val viewModel: TabSwitcherViewModel by bindViewModel()

    private val tabsAdapter: TabSwitcherAdapter by lazy {
        TabSwitcherAdapter(
            this,
            webViewPreviewPersister,
            this,
            faviconManager,
            dispatchers,
            trackerCountAnimator,
        )
    }

    private val onScrolledListener = object : OnScrollListener() {
        override fun onScrolled(
            recyclerView: RecyclerView,
            dx: Int,
            dy: Int,
        ) {
            super.onScrolled(recyclerView, dx, dy)
            checkTrackerAnimationPanelVisibility()
        }
    }

    // we need to scroll to show selected tab, but only if it is the first time loading the tabs.
    private var firstTimeLoadingTabsList = true

    private var selectedTabId: String? = null

    private lateinit var tabTouchHelper: TabTouchHelper
    private lateinit var tabsRecycler: RecyclerView
    private lateinit var tabItemDecorator: TabItemDecorator
    private lateinit var toolbar: Toolbar
    private lateinit var tabsFab: ExtendedFloatingActionButton

    private var popupMenuItem: MenuItem? = null
    private var layoutTypeMenuItem: MenuItem? = null

    private var tabSwitcherAnimationTileRemovalDialog: DaxAlertDialog? = null

    private var isTrackerAnimationPanelVisible = false

    private val binding: ActivityTabSwitcherBinding by viewBinding()
    private val popupMenu by lazy {
        PopupMenu(layoutInflater, R.layout.popup_tabs_menu)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        firstTimeLoadingTabsList = savedInstanceState?.getBoolean(KEY_FIRST_TIME_LOADING) ?: true

        if (tabSwitcherAnimationFeature.self().isEnabled()) {
            tabsAdapter.setAnimationTileCloseClickListener {
                viewModel.onTrackerAnimationInfoPanelClicked()
            }
        }

        extractIntentExtras()
        configureViewReferences()
        setupToolbar(toolbar)
        configureRecycler()
        configureFab()
        configureObservers()
        configureOnBackPressedListener()

        if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            initMenuClickListeners()
        }
    }

    private fun configureFab() {
        tabsFab = binding.tabsFab
        if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            tabsFab.show()
            tabsFab.setOnClickListener {
                viewModel.onFabClicked()
            }
        } else {
            tabsFab.hide()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(KEY_FIRST_TIME_LOADING, firstTimeLoadingTabsList)
    }

    private fun extractIntentExtras() {
        selectedTabId = intent.getStringExtra(EXTRA_KEY_SELECTED_TAB)
    }

    private fun configureViewReferences() {
        tabsRecycler = findViewById(R.id.tabsRecycler)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun configureRecycler() {
        val numberColumns = gridViewColumnCalculator.calculateNumberOfColumns(TAB_GRID_COLUMN_WIDTH_DP, TAB_GRID_MAX_COLUMN_COUNT)

        // the tabs recycler view is initially hidden until we know what type of layout to show
        tabsRecycler.gone()
        tabsRecycler.adapter = tabsAdapter

        tabTouchHelper = TabTouchHelper(
            numberGridColumns = numberColumns,
            onTabSwiped = { position -> this.onTabDeleted(position, true) },
            onTabMoved = this::onTabMoved,
            onTabDraggingStarted = this::onTabDraggingStarted,
            onTabDraggingFinished = this::onTabDraggingFinished,
        )

        val swipeListener = ItemTouchHelper(tabTouchHelper)
        swipeListener.attachToRecyclerView(tabsRecycler)

        tabItemDecorator = TabItemDecorator(this, selectedTabId, viewModel.viewState.value.mode)
        tabsRecycler.addItemDecoration(tabItemDecorator)

        tabsRecycler.setHasFixedSize(true)

        if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            tabsRecycler.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        if (dy > 0) {
                            tabsFab.shrink()
                        } else if (dy < 0) {
                            tabsFab.extend()
                        }
                    }
                },
            )
        }
    }

    private fun checkTrackerAnimationPanelVisibility() {
        if (!tabSwitcherAnimationFeature.self().isEnabled()) {
            return
        }

        val layoutManager = tabsRecycler.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val isPanelCurrentlyVisible = firstVisible == 0 && tabsAdapter.getTabSwitcherItem(0) is TrackerAnimationInfoPanel

        if (!isPanelCurrentlyVisible) {
            isTrackerAnimationPanelVisible = false
            return
        }

        val viewHolder = tabsRecycler.findViewHolderForAdapterPosition(0) ?: return
        val itemView = viewHolder.itemView

        val itemHeight = itemView.height
        val visibleHeight = itemHeight - max(0, -itemView.top) -
            max(0, itemView.bottom - tabsRecycler.height)

        val isEnoughVisible = visibleHeight > itemHeight * 0.75

        if (isEnoughVisible && !isTrackerAnimationPanelVisible) {
            viewModel.onTrackerAnimationInfoPanelVisible()
            isTrackerAnimationPanelVisible = true
        } else if (!isEnoughVisible) {
            isTrackerAnimationPanelVisible = false
        }
    }

    private fun configureObservers() {
        if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            lifecycleScope.launch {
                viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collectLatest {
                    tabsAdapter.updateData(it.tabs, it.mode)

                    tabsRecycler.invalidateItemDecorations()

                    if (it.layoutType != null) {
                        updateLayoutType(it.layoutType)
                    }

                    if (it.deletableTabs.isNotEmpty()) {
                        onDeletableTab(it.deletableTabs.last())
                    }

                    // if (it.selectedTab != null && it.selectedTab.tabId != tabItemDecorator.highlightedTabId && !it.selectedTab.deletable) {
                    updateTabGridItemDecorator(it.selectedTab, it.mode)
                    // }

                    invalidateOptionsMenu()
                }
            }
        } else {
            viewModel.tabSwitcherItems.observe(this) { tabSwitcherItems ->
                tabsAdapter.updateData(tabSwitcherItems)

                val noTabSelected = tabSwitcherItems.none { it.id == tabItemDecorator.tabSwitcherItemId }
                if (noTabSelected && tabSwitcherItems.isNotEmpty()) {
                    updateTabGridItemDecorator(tabSwitcherItems.last().id)
                }

                if (firstTimeLoadingTabsList) {
                    firstTimeLoadingTabsList = false
                    scrollToActiveTab()
                }
            }
            viewModel.activeTab.observe(this) { tab ->
                if (tab != null && tab.tabId != tabItemDecorator.tabSwitcherItemId && !tab.deletable) {
                    updateTabGridItemDecorator(tab.tabId)
                }
            }
            viewModel.deletableTabs.observe(this) {
                if (it.isNotEmpty()) {
                    onDeletableTab(it.last())
                }
            }

            lifecycleScope.launch {
                viewModel.layoutType.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).filterNotNull().collect {
                    updateLayoutType(it)
                }
            }
        }

        viewModel.command.observe(this) {
            processCommand(it)
        }
    }

    private fun updateLayoutType(layoutType: LayoutType) {
        tabsRecycler.hide()
        tabsRecycler.removeOnScrollListener(onScrolledListener)

        val centerOffsetPercent = getCurrentCenterOffset()

        when (layoutType) {
            LayoutType.GRID -> {
                val columnCount = gridViewColumnCalculator.calculateNumberOfColumns(TAB_GRID_COLUMN_WIDTH_DP, TAB_GRID_MAX_COLUMN_COUNT)

                val gridLayoutManager = getGridLayoutManager(columnCount)
                tabsRecycler.layoutManager = gridLayoutManager
                showListLayoutButton()
            }
            LayoutType.LIST -> {
                tabsRecycler.layoutManager = LinearLayoutManager(this@TabSwitcherActivity)
                showGridLayoutButton()
            }
        }

        tabsAdapter.onLayoutTypeChanged(layoutType)
        tabTouchHelper.onLayoutTypeChanged(layoutType)

        scrollToPreviousCenterOffset(
            centerOffsetPercent = centerOffsetPercent,
            onScrollCompleted = {
                tabsRecycler.addOnScrollListener(onScrolledListener)
            },
        )

        tabsRecycler.show()
    }

    private fun getGridLayoutManager(columnCount: Int): GridLayoutManager {
        return GridLayoutManager(
            this,
            columnCount,
        ).apply {
            spanSizeLookup = object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (tabsAdapter.getTabSwitcherItem(position) is TrackerAnimationInfoPanel) {
                        columnCount
                    } else {
                        1
                    }
                }
            }
        }
    }

    private fun scrollToPreviousCenterOffset(
        centerOffsetPercent: Float,
        onScrollCompleted: () -> Unit = {},
    ) {
        tabsRecycler.post {
            val newRange = tabsRecycler.computeVerticalScrollRange()
            val newExtent = tabsRecycler.computeVerticalScrollExtent()
            val newOffset = (centerOffsetPercent * newRange - newExtent / 2).toInt()
            (tabsRecycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, -newOffset)
            tabsRecycler.post {
                onScrollCompleted()
            }
        }
    }

    private fun getCurrentCenterOffset(): Float {
        val range = tabsRecycler.computeVerticalScrollRange()
        val offset = tabsRecycler.computeVerticalScrollOffset()
        val extent = tabsRecycler.computeVerticalScrollExtent()
        val centerOffsetPercent = (offset + extent.toFloat() / 2) / range
        return centerOffsetPercent
    }

    private fun showGridLayoutButton() {
        layoutTypeMenuItem?.let { viewModeMenuItem ->
            viewModeMenuItem.setIcon(R.drawable.ic_grid_view_24)
            viewModeMenuItem.title = getString(R.string.tabSwitcherGridViewMenu)
            viewModeMenuItem.setVisible(true)
        }
    }

    private fun showListLayoutButton() {
        layoutTypeMenuItem?.let { viewModeMenuItem ->
            viewModeMenuItem.setIcon(R.drawable.ic_list_view_24)
            viewModeMenuItem.title = getString(R.string.tabSwitcherListViewMenu)
            viewModeMenuItem.setVisible(true)
        }
    }

    private fun scrollToActiveTab() {
        val index = tabsAdapter.getAdapterPositionForTab(selectedTabId)
        if (index != -1) {
            scrollToPosition(index)
        }
    }

    private fun scrollToPosition(index: Int) {
        tabsRecycler.post {
            val height = tabsRecycler.height
            val offset = height / 2 - (tabsRecycler.getChildAt(0)?.height ?: 0) / 2
            (tabsRecycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, offset)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Close -> finishAfterTransition()
            is CloseAllTabsRequest -> showCloseAllTabsConfirmation()
            ShowAnimatedTileDismissalDialog -> showAnimatedTileDismissalDialog()
            DismissAnimatedTileDismissalDialog -> tabSwitcherAnimationTileRemovalDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            menuInflater.inflate(R.menu.menu_tab_switcher_activity_with_selection, menu)
            popupMenuItem = menu.findItem(R.id.popupMenuItem)

            val popupBinding = PopupTabsMenuBinding.bind(popupMenu.contentView)
            val viewState = viewModel.viewState.value
            val numSelectedTabs = (viewModel.viewState.value.mode as? Selection)?.selectedTabs?.size ?: 0

            layoutTypeMenuItem = menu.createDynamicInterface(numSelectedTabs, popupBinding, binding.tabsFab, viewState.dynamicInterface)
        } else {
            menuInflater.inflate(R.menu.menu_tab_switcher_activity, menu)
            layoutTypeMenuItem = menu.findItem(R.id.layoutTypeMenuItem)
        }

        when (viewModel.layoutType.value) {
            LayoutType.GRID -> showListLayoutButton()
            LayoutType.LIST -> showGridLayoutButton()
            null -> layoutTypeMenuItem?.isVisible = false
        }

        return true
    }

    private fun initMenuClickListeners() {
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.newTabMenuItem)) { onNewTabRequested(fromOverflowMenu = true) }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.selectAllMenuItem)) { viewModel.onSelectAllTabs() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.shareSelectedLinksMenuItem)) { viewModel.onShareSelectedTabs() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.bookmarkSelectedTabsMenuItem)) { viewModel.onBookmarkSelectedTabs() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.selectTabsMenuItem)) { viewModel.onSelectionModeRequested() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.closeSelectedTabsMenuItem)) { viewModel.onCloseSelectedTabs() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.closeOtherTabsMenuItem)) { viewModel.onCloseOtherTabs() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.closeAllTabsMenuItem)) { viewModel.onCloseAllTabsRequested() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.layoutTypeMenuItem -> onLayoutTypeToggled()
            R.id.fireMenuItem -> onFire()
            R.id.popupMenuItem -> showPopupMenu(item.itemId)
            R.id.newTab -> onNewTabRequested(fromOverflowMenu = false)
            R.id.newTabOverflow -> onNewTabRequested(fromOverflowMenu = true)
            R.id.duckChat -> {
                viewModel.onDuckChatMenuClicked()
            }
            R.id.closeAllTabs -> closeAllTabs()
            R.id.downloads -> showDownloads()
            R.id.settings -> showSettings()
            android.R.id.home -> {
                viewModel.onUpButtonPressed()
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showPopupMenu(itemId: Int) {
        val anchorView = findViewById<View>(itemId)
        popupMenu.show(binding.root, anchorView)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val duckChatMenuItem = menu?.findItem(R.id.duckChat)
        duckChatMenuItem?.isVisible = duckChat.showInBrowserMenu()

        return if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            viewModel.viewState.value.dynamicInterface.isMoreMenuItemEnabled
        } else {
            super.onPrepareOptionsMenu(menu)
        }
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        if (featureId == FEATURE_SUPPORT_ACTION_BAR) {
            viewModel.onMenuOpened()
        }
        return super.onMenuOpened(featureId, menu)
    }

    private fun onFire() {
        pixel.fire(AppPixelName.FORGET_ALL_PRESSED_TABSWITCHING)
        val dialog = FireDialog(
            context = this,
            clearPersonalDataAction = clearPersonalDataAction,
            pixel = pixel,
            settingsDataStore = settingsDataStore,
            userEventsStore = userEventsStore,
            appCoroutineScope = appCoroutineScope,
            dispatcherProvider = dispatcherProvider,
            fireButtonStore = fireButtonStore,
            appBuildConfig = appBuildConfig,
        )
        dialog.show()
    }

    private fun onLayoutTypeToggled() {
        viewModel.onLayoutTypeToggled()
    }

    override fun onNewTabRequested(fromOverflowMenu: Boolean) {
        clearObserversEarlyToStopViewUpdates()
        launch { viewModel.onNewTabRequested(fromOverflowMenu) }
    }

    override fun onTabSelected(tab: TabEntity) {
        selectedTabId = tab.tabId
        updateTabGridItemDecorator(tab.tabId)
        launch { viewModel.onTabSelected(tab) }
    }

    private fun updateTabGridItemDecorator(tabSwitcherItemId: String) {
        tabItemDecorator.tabSwitcherItemId = tabSwitcherItemId
        tabsRecycler.invalidateItemDecorations()
    }

    override fun onTabDeleted(position: Int, deletedBySwipe: Boolean) {
        tabsAdapter.getTabSwitcherItem(position)?.let { tab ->
            when (tab) {
                is TabSwitcherItem.Tab -> {
                    launch {
                        viewModel.onMarkTabAsDeletable(
                            tab = tab.tabEntity,
                            swipeGestureUsed = deletedBySwipe,
                        )
                    }
                }
                is TrackerAnimationInfoPanel -> Unit
            }
        }
    }

    override fun onTabMoved(from: Int, to: Int) {
        if (tabSwitcherAnimationFeature.self().isEnabled()) {
            val isTrackerAnimationInfoPanelVisible = viewModel.tabSwitcherItems.value?.get(0) is TrackerAnimationInfoPanel
            val canSwapFromIndex = if (isTrackerAnimationInfoPanelVisible) 1 else 0
            val tabSwitcherItemCount = viewModel.tabSwitcherItems.value?.count() ?: 0

            val canSwap = from in canSwapFromIndex..<tabSwitcherItemCount && to in canSwapFromIndex..<tabSwitcherItemCount
            if (canSwap) {
                tabsAdapter.onTabMoved(from, to)
                // Adjust indices if animation feature is enabled to account for the TrackerAnimationTile at index 0
                viewModel.onTabMoved(from - canSwapFromIndex, to - canSwapFromIndex)
            }
        } else {
            val tabCount = viewModel.tabSwitcherItems.value?.size ?: 0
            val canSwap = from in 0..<tabCount && to in 0..<tabCount
            if (canSwap) {
                tabsAdapter.onTabMoved(from, to)
                viewModel.onTabMoved(from, to)
            }
        }
    }

    private fun onTabDraggingStarted() {
        viewModel.onTabDraggingStarted()
        tabsAdapter.onDraggingStarted()

        // remove the tab selection border while dragging because it doesn't scale well
        while (tabsRecycler.itemDecorationCount > 1) {
            tabsRecycler.removeItemDecorationAt(1)
        }
    }

    private fun onTabDraggingFinished() {
        tabsAdapter.onDraggingFinished()

        tabsRecycler.addItemDecoration(tabItemDecorator)
    }

    private fun onDeletableTab(tab: TabEntity) {
        Snackbar.make(toolbar, getString(R.string.tabClosed), Snackbar.LENGTH_LONG)
            .setDuration(3500) // 3.5 seconds
            .setAction(R.string.tabClosedUndo) {
                // noop, handled in onDismissed callback
            }
            .addCallback(
                object : Snackbar.Callback() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        when (event) {
                            // handle the UNDO action here as we only have one
                            BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_ACTION -> launch { viewModel.undoDeletableTab(tab) }
                            BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_SWIPE,
                            BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_TIMEOUT,
                            -> launch { viewModel.purgeDeletableTabs() }
                            BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_CONSECUTIVE,
                            BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_MANUAL,
                            -> { /* noop */
                            }
                        }
                    }
                },
            )
            .apply { view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).maxLines = 1 }
            .show()
    }

    private fun closeAllTabs() {
        viewModel.onCloseAllTabsRequested()
    }

    private fun showDownloads() {
        startActivity(DownloadsActivity.intent(this))
        viewModel.onDownloadsMenuPressed()
    }

    private fun showSettings() {
        startActivity(SettingsActivity.intent(this))
        viewModel.onSettingsMenuPressed()
    }

    override fun finish() {
        clearObserversEarlyToStopViewUpdates()
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.deletableTabs.removeObservers(this)
        // we don't want to purge during device rotation
        if (isFinishing) {
            launch { viewModel.purgeDeletableTabs() }
        }
    }

    private fun clearObserversEarlyToStopViewUpdates() {
        viewModel.tabSwitcherItems.removeObservers(this)
        viewModel.deletableTabs.removeObservers(this)
    }

    private fun showCloseAllTabsConfirmation() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.closeAppTabsConfirmationDialogTitle)
            .setMessage(R.string.closeAppTabsConfirmationDialogDescription)
            .setPositiveButton(R.string.closeAppTabsConfirmationDialogClose, DESTRUCTIVE)
            .setNegativeButton(R.string.closeAppTabsConfirmationDialogCancel, GHOST_ALT)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onCloseAllTabsConfirmed()
                    }
                },
            )
            .show()
    }

    private fun showAnimatedTileDismissalDialog() {
        tabSwitcherAnimationTileRemovalDialog = TextAlertDialogBuilder(this)
            .setTitle(R.string.tabSwitcherAnimationTileRemovalDialogTitle)
            .setMessage(R.string.tabSwitcherAnimationTileRemovalDialogBody)
            .setPositiveButton(R.string.daxDialogGotIt)
            .setNegativeButton(R.string.tabSwitcherAnimationTileRemovalDialogNegativeButton, GHOST)
            .setCancellable(true)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onNegativeButtonClicked() {
                        viewModel.onTrackerAnimationTileNegativeButtonClicked()
                    }

                    override fun onPositiveButtonClicked() {
                        viewModel.onTrackerAnimationTilePositiveButtonClicked()
                    }
                },
            )
            .build()
            .also { dialog ->
                dialog.show()
            }
    }

    private fun configureOnBackPressedListener() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBackButtonPressed()
                    finish()
                }
            },
        )
    }

    companion object {
        fun intent(
            context: Context,
            selectedTabId: String? = null,
        ): Intent {
            val intent = Intent(context, TabSwitcherActivity::class.java)
            intent.putExtra(EXTRA_KEY_SELECTED_TAB, selectedTabId)
            return intent
        }

        const val EXTRA_KEY_SELECTED_TAB = "selected"

        private const val TAB_GRID_COLUMN_WIDTH_DP = 180
        private const val TAB_GRID_MAX_COLUMN_COUNT = 4
        private const val KEY_FIRST_TIME_LOADING = "FIRST_TIME_LOADING"
    }
}
