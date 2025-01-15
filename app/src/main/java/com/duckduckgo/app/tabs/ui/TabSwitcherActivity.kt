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
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR
import androidx.appcompat.content.res.AppCompatResources
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
import com.duckduckgo.app.tabs.TabMultiSelectionFeature
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.Close
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.CloseAllTabsRequest
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
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
    lateinit var tabMultiSelectionFeature: TabMultiSelectionFeature

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

    private var layoutTypeMenuItem: MenuItem? = null
    private var layoutType: LayoutType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tab_switcher)

        firstTimeLoadingTabsList = savedInstanceState?.getBoolean(KEY_FIRST_TIME_LOADING) ?: true

        tabsFab = findViewById(R.id.tabsFab)

        extractIntentExtras()
        configureViewReferences()
        setupToolbar(toolbar)
        configureRecycler()
        configureFab()
        configureObservers()
        configureOnBackPressedListener()
    }

    private fun configureFab() {
        if (tabMultiSelectionFeature.self().isEnabled()) {
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

        tabItemDecorator = TabItemDecorator(this, selectedTabId)
        tabsRecycler.addItemDecoration(tabItemDecorator)
        tabsRecycler.setHasFixedSize(true)

        if (tabMultiSelectionFeature.self().isEnabled()) {
            tabsRecycler.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        if (dy < 0) {
                            tabsFab.shrink()
                        }
                    }

                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            tabsFab.extend()
                        }
                        super.onScrollStateChanged(recyclerView, newState)
                    }
                },
            )
        }
    }

    private fun configureObservers() {
        viewModel.tabs.observe(this) { tabs ->
            render(tabs)

            val noTabSelected = tabs.none { it.tabId == tabItemDecorator.selectedTabId }
            if (noTabSelected && tabs.isNotEmpty()) {
                updateTabGridItemDecorator(tabs.last())
            }
        }
        viewModel.activeTab.observe(this) { tab ->
            if (tab != null && tab.tabId != tabItemDecorator.selectedTabId && !tab.deletable) {
                updateTabGridItemDecorator(tab)
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

        lifecycleScope.launch {
            viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collectLatest {
                updateFabType(it.fabType)
            }
        }

        viewModel.command.observe(this) {
            processCommand(it)
        }
    }

    private fun updateLayoutType(layoutType: LayoutType) {
        tabsRecycler.hide()

        val centerOffsetPercent = getCurrentCenterOffset()

        this.layoutType = layoutType
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

    private fun updateFabType(fabType: TabSwitcherViewModel.ViewState.FabType) {
        when (fabType) {
            TabSwitcherViewModel.ViewState.FabType.NEW_TAB -> {
                tabsFab.icon = AppCompatResources.getDrawable(this, com.duckduckgo.mobile.android.R.drawable.ic_add_24)
                tabsFab.setText(R.string.tabSwitcherFabNewTab)
            }
            TabSwitcherViewModel.ViewState.FabType.CLOSE_TABS -> {
                tabsFab.icon = AppCompatResources.getDrawable(this, com.duckduckgo.mobile.android.R.drawable.ic_close_24)
                tabsFab.setText(R.string.tabSwitcherFabCloseTabs)
            }
        }
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

    private fun render(tabs: List<TabEntity>) {
        tabsAdapter.updateData(tabs)
    }

    private fun scrollToShowCurrentTab() {
        val index = tabsAdapter.adapterPositionForTab(selectedTabId)
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
        menuInflater.inflate(R.menu.menu_tab_switcher_activity, menu)
        layoutTypeMenuItem = menu.findItem(R.id.layoutType)

        when (layoutType) {
            LayoutType.GRID -> showListLayoutButton()
            LayoutType.LIST -> showGridLayoutButton()
            null -> layoutTypeMenuItem?.isVisible = false
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.layoutType -> onLayoutTypeToggled()
            R.id.fire -> onFire()
            R.id.newTab -> onNewTabRequested(fromOverflowMenu = false)
            R.id.newTabOverflow -> onNewTabRequested(fromOverflowMenu = true)
            R.id.duckChat -> duckChat.openDuckChat()
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

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val closeAllTabsMenuItem = menu?.findItem(R.id.closeAllTabs)
        closeAllTabsMenuItem?.isVisible = viewModel.tabs.value?.isNotEmpty() == true
        val duckChatMenuItem = menu?.findItem(R.id.duckChat)
        duckChatMenuItem?.isVisible = duckChat.showInBrowserMenu()

        return super.onPrepareOptionsMenu(menu)
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
        updateTabGridItemDecorator(tab)
        launch { viewModel.onTabSelected(tab) }
    }

    private fun updateTabGridItemDecorator(tab: TabEntity) {
        tabItemDecorator.selectedTabId = tab.tabId
        tabsRecycler.invalidateItemDecorations()
    }

    override fun onTabDeleted(position: Int, deletedBySwipe: Boolean) {
        tabsAdapter.getTab(position)?.let { tab ->
            launch { viewModel.onMarkTabAsDeletable(tab, deletedBySwipe) }
        }
    }

    override fun onTabMoved(from: Int, to: Int) {
        val tabCount = viewModel.tabs.value?.size ?: 0
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
        viewModel.tabs.removeObservers(this)
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
