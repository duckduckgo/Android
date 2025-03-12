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
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.Close
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.CloseAllTabsRequest
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Selection
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
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
    lateinit var tabManagerFeatureFlags: TabManagerFeatureFlags

    private val viewModel: TabSwitcherViewModel by bindViewModel()

    private val tabsAdapter: TabSwitcherAdapter by lazy { TabSwitcherAdapter(this, webViewPreviewPersister, this, faviconManager, dispatchers) }

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

    private val binding: ActivityTabSwitcherBinding by viewBinding()
    private val popupMenu by lazy {
        PopupMenu(layoutInflater, R.layout.popup_tabs_menu)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        firstTimeLoadingTabsList = savedInstanceState?.getBoolean(KEY_FIRST_TIME_LOADING) ?: true

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
            tabsFab.apply {
                show()
                extend()
                setOnClickListener {
                    viewModel.onFabClicked()
                }
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

        tabItemDecorator = TabItemDecorator(this)
        tabsRecycler.addItemDecoration(tabItemDecorator)

        tabsRecycler.setHasFixedSize(true)

        if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            handleFabStateUpdates()
            handleSelectionModeCancellation()
        }
    }

    private fun handleSelectionModeCancellation() {
        tabsRecycler.addOnItemTouchListener(
            object : RecyclerView.OnItemTouchListener {
                private var lastEventAction: Int? = null
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    if (e.action == MotionEvent.ACTION_DOWN && tabsRecycler.findChildViewUnder(e.x, e.y) == null ||
                        e.action == MotionEvent.ACTION_MOVE
                    ) {
                        lastEventAction = e.action
                    } else if (e.action == MotionEvent.ACTION_UP) {
                        if (lastEventAction == MotionEvent.ACTION_DOWN) {
                            viewModel.onEmptyAreaClicked()
                        }
                        lastEventAction = null
                    }
                    return false
                }

                override fun onTouchEvent(
                    rv: RecyclerView,
                    e: MotionEvent,
                ) {
                    // no-op
                }

                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                    // no-op
                }
            },
        )
    }

    private fun handleFabStateUpdates() {
        tabsRecycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
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

    private fun updateToolbarTitle(mode: Mode) {
        toolbar.title = if (mode is Mode.Selection) {
            if (mode.selectedTabs.isEmpty()) {
                getString(R.string.selectTabsMenuItem)
            } else {
                getString(R.string.tabSelectionTitle, mode.selectedTabs.size)
            }
        } else {
            getString(R.string.tabActivityTitle)
        }
    }

    private fun configureObservers() {
        if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            lifecycleScope.launch {
                viewModel.selectionViewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collectLatest {
                    tabsRecycler.invalidateItemDecorations()
                    tabsAdapter.updateData(it.items)

                    updateToolbarTitle(it.mode)
                    updateTabGridItemDecorator()

                    invalidateOptionsMenu()
                }
            }
        } else {
            viewModel.activeTab.observe(this) { tab ->
                if (tab != null && !tab.deletable) {
                    updateTabGridItemDecorator()
                }
            }

            viewModel.tabSwitcherItems.observe(this) { tabSwitcherItems ->
                tabsAdapter.updateData(tabSwitcherItems)

                val noTabSelected = tabSwitcherItems.none { (it as? NormalTab)?.isActive == true }
                if (noTabSelected && tabSwitcherItems.isNotEmpty()) {
                    updateTabGridItemDecorator()
                }
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

        viewModel.command.observe(this) {
            processCommand(it)
        }
    }

    private fun updateLayoutType(layoutType: LayoutType) {
        tabsRecycler.hide()

        val centerOffsetPercent = getCurrentCenterOffset()

        when (layoutType) {
            LayoutType.GRID -> {
                val gridLayoutManager = GridLayoutManager(
                    this@TabSwitcherActivity,
                    gridViewColumnCalculator.calculateNumberOfColumns(TAB_GRID_COLUMN_WIDTH_DP, TAB_GRID_MAX_COLUMN_COUNT),
                )
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

        if (firstTimeLoadingTabsList) {
            firstTimeLoadingTabsList = false

            scrollToShowCurrentTab()
        } else {
            scrollToPreviousCenterOffset(centerOffsetPercent)
        }

        tabsRecycler.show()
    }

    private fun scrollToPreviousCenterOffset(centerOffsetPercent: Float) {
        tabsRecycler.post {
            val newRange = tabsRecycler.computeVerticalScrollRange()
            val newExtent = tabsRecycler.computeVerticalScrollExtent()
            val newOffset = (centerOffsetPercent * newRange - newExtent / 2).toInt()
            (tabsRecycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, -newOffset)
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

    private fun scrollToShowCurrentTab() {
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
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            menuInflater.inflate(R.menu.menu_tab_switcher_activity_with_selection, menu)
            popupMenuItem = menu.findItem(R.id.popupMenuItem)

            val popupBinding = PopupTabsMenuBinding.bind(popupMenu.contentView)
            val viewState = viewModel.selectionViewState.value
            val numSelectedTabs = (viewModel.selectionViewState.value.mode as? Selection)?.selectedTabs?.size ?: 0

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
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.deselectAllMenuItem)) { viewModel.onDeselectAllTabs() }
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
            viewModel.selectionViewState.value.dynamicInterface.isMoreMenuItemEnabled
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
        launch { viewModel.onTabSelected(tab) }
    }

    private fun updateTabGridItemDecorator() {
        tabsRecycler.invalidateItemDecorations()
    }

    override fun onTabDeleted(position: Int, deletedBySwipe: Boolean) {
        tabsAdapter.getTabSwitcherItem(position)?.let { tab ->
            when (tab) {
                is Tab -> {
                    launch {
                        viewModel.onMarkTabAsDeletable(
                            tab = tab.tabEntity,
                            swipeGestureUsed = deletedBySwipe,
                        )
                    }
                }
            }
        }
    }

    override fun onTabMoved(from: Int, to: Int) {
        val tabCount = viewModel.tabSwitcherItems.value?.size ?: 0
        val canSwap = from in 0..< tabCount && to in 0..< tabCount
        if (canSwap) {
            tabsAdapter.onTabMoved(from, to)
            viewModel.onTabMoved(from, to)
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
            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_SLIDE)
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

    private fun configureOnBackPressedListener() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBackButtonPressed()
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
