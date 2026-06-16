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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.api.OmnibarRepository
import com.duckduckgo.app.browser.databinding.ActivityTabSwitcherBinding
import com.duckduckgo.app.browser.databinding.PopupTabsMenuBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarObserver
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.downloads.DownloadsActivity
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.DuckAiTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.SelectableTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackersAnimationInfoPanel
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.BookmarkTabsRequest
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.Close
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.CloseAllTabsRequest
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.CloseAndShowUndoMessage
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.CloseTabsRequest
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.DismissAnimatedTileDismissalDialog
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShareLink
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShareLinks
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShowAnimatedTileDismissalDialog
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShowUndoBookmarkMessage
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.ShowUndoDeleteTabsMessage
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.ViewState.Mode
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.ViewState.Mode.Selection
import com.duckduckgo.browser.api.ui.BrowserScreens.TabSwitcherScreenNoParams
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.button.ButtonType
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.dataclearing.api.fire.FireDialogProvider
import com.duckduckgo.dataclearing.api.fire.FireDialogProvider.FireDialogOrigin.TabSwitcher
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(TabSwitcherScreenNoParams::class, screenName = "tabSwitcher")
class TabSwitcherActivity :
    DuckDuckGoActivity(),
    TabSwitcherListener,
    CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + dispatchers.main()

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var gridViewColumnCalculator: GridViewColumnCalculator

    @Inject
    lateinit var webViewPreviewPersister: WebViewPreviewPersister

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var trackerCountAnimator: TrackerCountAnimator

    @Inject
    lateinit var addressDisplayFormatter: com.duckduckgo.app.browser.AddressDisplayFormatter

    @Inject
    lateinit var urlDisplayRepository: com.duckduckgo.app.browser.urldisplay.UrlDisplayRepository

    @Inject
    lateinit var fireDialogProvider: FireDialogProvider

    @Inject
    lateinit var omnibarRepository: OmnibarRepository

    @Inject
    lateinit var currentBrowserMode: BrowserMode

    override val applyFireTheme: Boolean
        get() = currentBrowserMode == BrowserMode.FIRE

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val viewModel: TabSwitcherViewModel by bindViewModel()

    private val tabsAdapter: TabSwitcherAdapter by lazy {
        TabSwitcherAdapter(
            itemClickListener = this,
            webViewPreviewPersister = webViewPreviewPersister,
            lifecycleOwner = this,
            faviconManager = faviconManager,
            dispatchers = dispatchers,
            trackerCountAnimator = trackerCountAnimator,
            addressDisplayFormatter = addressDisplayFormatter,
        )
    }

    private val onScrolledListener =
        object : OnScrollListener() {
            override fun onScrolled(
                recyclerView: RecyclerView,
                dx: Int,
                dy: Int,
            ) {
                super.onScrolled(recyclerView, dx, dy)
                checkTrackerAnimationPanelVisibility()
            }

            override fun onScrollStateChanged(
                recyclerView: RecyclerView,
                newState: Int,
            ) {
                super.onScrollStateChanged(recyclerView, newState)
                // Record the top item so a later grid<->list toggle can restore it
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    captureScrollAnchorPosition()?.let { savedScrollAnchorPosition = it }
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    // User is scrolling, reset the toggle anchor
                    pendingToggleScrollAnchor = null
                }
            }
        }

    // we need to scroll to show selected tab, but only if it is the first time loading the tabs
    private var firstTimeLoadingTabsList = true
    private var currentLayoutType: LayoutType? = null
    private var savedScrollAnchorPosition = RecyclerView.NO_POSITION
    private var pendingToggleScrollAnchor: Int? = null
    private var anchorRepinDeadlineMs = 0L

    private val anchorRepinPreDrawListener =
        ViewTreeObserver.OnPreDrawListener {
            val anchor = pendingToggleScrollAnchor
            if (anchor != null) {
                if (SystemClock.uptimeMillis() > anchorRepinDeadlineMs) {
                    pendingToggleScrollAnchor = null
                } else {
                    val layoutManager = tabsRecycler.layoutManager as? LinearLayoutManager
                    val first = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                    // Re-pin if the layout drifted the anchor below the top. Tolerance of one
                    // position allows the 2-column grid's row-leader pairing
                    if (first != RecyclerView.NO_POSITION && first < anchor - 1) {
                        layoutManager?.scrollToPositionWithOffset(anchor, 0)
                    }
                }
            }
            true
        }

    private var isOnScrolledListenerAttached = false

    private var skipTabPurge: Boolean = false

    private lateinit var tabTouchHelper: TabTouchHelper
    private lateinit var tabsRecycler: RecyclerView
    private lateinit var tabsContainer: FrameLayout
    private lateinit var tabItemDecorator: TabItemDecorator
    private lateinit var toolbar: Toolbar

    private var layoutTypeMenuItem: MenuItem? = null
    private var tabSwitcherAnimationTileRemovalDialog: DaxAlertDialog? = null
    private var isTrackerAnimationPanelVisible = false
    private var browserModeToggle: BrowserModeToggleView? = null

    private var fadingOutForRecreate = false
    private var fadingInAfterRecreate = false
    private var fadeInAnimationStarted = false

    private var lastSnackbar: DefaultSnackbar? = null

    private val binding: ActivityTabSwitcherBinding by viewBinding()
    private val popupMenu by lazy {
        if (settingsDataStore.omnibarType == OmnibarType.SINGLE_TOP) {
            PopupMenu(layoutInflater, R.layout.popup_tabs_menu)
        } else {
            PopupMenu(layoutInflater, R.layout.popup_tabs_menu_bottom)
        }
    }

    private val snackbarAnchorView by lazy {
        when (settingsDataStore.omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> {
                toolbar
            }

            OmnibarType.SINGLE_TOP -> {
                null
            }
            OmnibarType.SPLIT -> {
                binding.navigationBar
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.BROWSER)
        if (edgeToEdgeEnabled) {
            val barStyle = if (isDarkThemeEnabled()) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        }

        setContentView(binding.root)

        fadingInAfterRecreate = savedInstanceState?.getBoolean(KEY_FADE_IN_AFTER_RECREATE) == true

        tabsAdapter.setAnimationTileCloseClickListener {
            viewModel.onTrackerAnimationInfoPanelClicked()
        }

        configureViewReferences()
        setupToolbar(toolbar)
        configureRecycler()
        configureNavigationBar()
        configureFireTabsEmptyState()

        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }

        configureObservers()
        configureOnBackPressedListener()

        initMenuClickListeners()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (fadingOutForRecreate) {
            outState.putBoolean(KEY_FADE_IN_AFTER_RECREATE, true)
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)

        when (settingsDataStore.omnibarType) {
            OmnibarType.SINGLE_TOP -> {
                edgeToEdgeHandler.applyStatusBarInsets(binding.tabSwitcherToolbarTop.root)
                edgeToEdgeHandler.applyNavigationBarInsets(tabsContainer)
            }
            OmnibarType.SINGLE_BOTTOM -> {
                edgeToEdgeHandler.applyStatusBarInsets(tabsContainer)
                edgeToEdgeHandler.applyNavigationBarInsets(binding.tabSwitcherToolbarBottom.appBarLayout)
                binding.tabSwitcherToolbarBottom.appBarLayout.setBackgroundColor(
                    getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorToolbar),
                )
            }
            OmnibarType.SPLIT -> {
                edgeToEdgeHandler.applyStatusBarInsets(binding.tabSwitcherToolbarTop.root)
                edgeToEdgeHandler.applyNavigationBarInsets(binding.navigationBar)
            }
        }
    }

    private fun configureNavigationBar() {
        if (omnibarRepository.omnibarType == OmnibarType.SPLIT) {
            binding.navigationBar.browserNavigationBarObserver =
                object : BrowserNavigationBarObserver {
                    override fun onMenuButtonClicked() {
                        showPopupMenu(binding.navigationBar.popupMenuAnchor.id)
                    }

                    override fun onNewTabButtonClicked() {
                        viewModel.onNewTabRequested()
                    }

                    override fun onFireButtonClicked() {
                        viewModel.onFireButtonTapped()
                    }
                }
            binding.navigationBar.setViewMode(BrowserNavigationBarView.ViewMode.TabManager)
            binding.navigationBar.show()
        } else {
            binding.navigationBar.gone()
        }
    }

    private fun configureFireTabsEmptyState() {
        binding.fireTabsEmptyState.newFireTabButton.setOnClickListener {
            onNewTabRequested(fromOverflowMenu = false)
        }
    }

    private fun configureViewReferences() {
        tabsRecycler = findViewById(R.id.tabsRecycler)

        when (settingsDataStore.omnibarType) {
            OmnibarType.SINGLE_TOP -> {
                binding.root.removeView(binding.tabSwitcherToolbarBottom.root)
            }
            OmnibarType.SINGLE_BOTTOM -> {
                binding.root.removeView(binding.tabSwitcherToolbarTop.root)
            }
            OmnibarType.SPLIT -> {
                binding.root.removeView(binding.tabSwitcherToolbarBottom.root)
            }
        }

        toolbar = findViewById(R.id.toolbar)

        tabsContainer = findViewById(R.id.tabsContainer)
    }

    private fun configureRecycler() {
        val numberColumns = gridViewColumnCalculator.calculateNumberOfColumns(TAB_GRID_COLUMN_WIDTH_DP, TAB_GRID_MAX_COLUMN_COUNT)

        // the tabs recycler view is initially hidden until we know what type of layout to show
        tabsRecycler.gone()
        tabsRecycler.adapter = tabsAdapter
        tabsRecycler.viewTreeObserver.addOnPreDrawListener(anchorRepinPreDrawListener)

        if (fadingInAfterRecreate) {
            tabsRecycler.alpha = 0f
            // Suppress per-item add animations during the post-recreate diff so the new mode's
            // items don't shimmer in on top of the container fade-in. Restored once fade-in ends.
            tabsRecycler.itemAnimator = null
        }

        tabTouchHelper =
            TabTouchHelper(
                numberGridColumns = numberColumns,
                onTabSwiped = { position -> this.onTabDeleted(position, true) },
                onTabMoved = this::onTabMoved,
                onTabDraggingStarted = this::onTabDraggingStarted,
                onTabDraggingFinished = this::onTabDraggingFinished,
            )

        val swipeListener = ItemTouchHelper(tabTouchHelper)
        swipeListener.attachToRecyclerView(tabsRecycler)

        tabItemDecorator = TabItemDecorator(context = this)
        tabsRecycler.addItemDecoration(tabItemDecorator)

        tabsRecycler.setHasFixedSize(true)

        handleSelectionModeCancellation()

        viewModel.viewState.value.layoutType?.let {
            applyLayoutType(it)
            tabsRecycler.show()
        }
    }

    private fun handleSelectionModeCancellation() {
        tabsRecycler.addOnItemTouchListener(
            object : RecyclerView.OnItemTouchListener {
                private var lastEventAction: Int? = null

                override fun onInterceptTouchEvent(
                    rv: RecyclerView,
                    e: MotionEvent,
                ): Boolean {
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

    private fun configureBrowserModeToggle(viewState: TabSwitcherViewModel.ViewState) {
        if (browserModeToggle != null || !viewState.isBrowserModeToggleVisible) return

        val toggle = BrowserModeToggleView(this).also { browserModeToggle = it }
        toolbar.addView(
            toggle,
            Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL,
            ),
        )
        toggle.setOnModeChangedListener { mode ->
            fadeOutTabsThenRecreate(mode)
        }

        if (fadingInAfterRecreate) {
            val previousMode = when (currentBrowserMode) {
                BrowserMode.FIRE -> BrowserMode.REGULAR
                BrowserMode.REGULAR -> BrowserMode.FIRE
            }
            toggle.setMode(previousMode)
        }
    }

    private fun applyToolbarViewState(state: TabSwitcherViewModel.ViewState) {
        browserModeToggle?.setMode(state.browserMode)
        state.regularTabCount?.let { browserModeToggle?.setRegularTabCount(it) }
        updateToolbarTitle(state.mode, state.tabs.size)
    }

    private fun fadeOutTabsThenRecreate(newMode: BrowserMode) {
        if (fadingOutForRecreate) return
        fadingOutForRecreate = true

        // In the Fire tabs empty state the recycler is hidden and empty, so there is nothing to
        // fade out. Animate nothing and just switch mode and recreate immediately; the recreated
        // activity still fades the new mode's tabs in via the saved fadingInAfterRecreate flag.
        if (tabsRecycler.visibility != View.VISIBLE) {
            viewModel.onBrowserModeToggled(newMode)
            recreate()
            return
        }

        tabsRecycler.animate()
            .alpha(0f)
            .setDuration(MODE_SWITCH_FADE_OUT_MS)
            .withEndAction {
                tabsRecycler.visibility = View.INVISIBLE
                viewModel.onBrowserModeToggled(newMode)
                recreate()
            }
            .start()
    }

    private fun updateToolbarTitle(
        mode: Mode,
        tabCount: Int,
    ) {
        val toggle = browserModeToggle
        val showToggle = toggle != null && mode !is Selection

        toggle?.visibility = if (showToggle) View.VISIBLE else View.GONE

        supportActionBar?.title =
            when {
                mode is Selection ->
                    if (mode.selectedTabs.isEmpty()) {
                        getString(R.string.selectTabsMenuItem)
                    } else {
                        getString(R.string.tabSelectionTitle, mode.selectedTabs.size)
                    }
                showToggle -> ""
                else -> resources.getQuantityString(R.plurals.tabSwitcherTitle, tabCount, tabCount)
            }
    }

    private fun checkTrackerAnimationPanelVisibility() {
        val layoutManager = tabsRecycler.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val isPanelCurrentlyVisible = firstVisible == 0 && tabsAdapter.getTabSwitcherItem(0) is TrackersAnimationInfoPanel

        if (!isPanelCurrentlyVisible) {
            isTrackerAnimationPanelVisible = false
            return
        }

        val viewHolder = tabsRecycler.findViewHolderForAdapterPosition(0) ?: return
        val itemView = viewHolder.itemView

        val itemHeight = itemView.height
        val visibleHeight =
            itemHeight - max(0, -itemView.top) -
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
        lifecycleScope.launch {
            viewModel.viewState.flowWithLifecycle(lifecycle).collectLatest {
                if (it.showFireTabsEmptyState && !fadingOutForRecreate) {
                    binding.fireTabsEmptyState.root.show()
                    tabsRecycler.gone()
                } else {
                    binding.fireTabsEmptyState.root.gone()

                    it.layoutType?.let(::updateLayoutType)

                    tabsRecycler.invalidateItemDecorations()

                    val shouldTryScroll = it.tabs.isNotEmpty() &&
                        (firstTimeLoadingTabsList || fadingInAfterRecreate)

                    tabsAdapter.updateData(it.tabSwitcherItems) {
                        pendingToggleScrollAnchor?.let { anchor -> scrollPositionToTop(anchor) }

                        val scrolled = shouldTryScroll && scrollToActiveTab(it.tabSwitcherItems)
                        if (scrolled) firstTimeLoadingTabsList = false

                        if (fadingInAfterRecreate && !fadeInAnimationStarted && it.tabs.isNotEmpty()) {
                            fadeInAnimationStarted = true
                            tabsRecycler.show()
                            tabsRecycler.animate()
                                .alpha(1f)
                                .setDuration(MODE_SWITCH_FADE_IN_MS)
                                .withEndAction {
                                    fadingInAfterRecreate = false
                                    tabsRecycler.itemAnimator = DefaultItemAnimator()
                                }
                                .start()
                        }
                    }
                }

                configureBrowserModeToggle(it)
                applyToolbarViewState(it)

                tabTouchHelper.mode = it.mode

                invalidateOptionsMenu()
            }
        }

        lifecycleScope.launch {
            urlDisplayRepository.isFullUrlEnabled.flowWithLifecycle(lifecycle).collect {
                tabsAdapter.isFullUrlEnabled = it
            }
        }

        viewModel.command.observe(this) {
            processCommand(it)
        }
    }

    private fun updateLayoutType(layoutType: LayoutType) {
        if (layoutType == currentLayoutType) {
            attachOnScrolledListener()
            tabsRecycler.show()
            return
        }

        // Preserve the user's scroll position across the grid<->list swap
        if (savedScrollAnchorPosition == RecyclerView.NO_POSITION) {
            savedScrollAnchorPosition = captureScrollAnchorPosition() ?: RecyclerView.NO_POSITION
        }
        val anchorPosition = savedScrollAnchorPosition.takeIf { it != RecyclerView.NO_POSITION }

        tabsRecycler.hide()
        detachOnScrolledListener()

        applyLayoutType(layoutType)

        if (anchorPosition != null) {
            pendingToggleScrollAnchor = anchorPosition
            anchorRepinDeadlineMs = SystemClock.uptimeMillis() + ANCHOR_REPIN_WINDOW_MS
            scrollPositionToTop(anchorPosition)
        } else {
            pendingToggleScrollAnchor = null
            scrollToActiveTab(viewModel.viewState.value.tabSwitcherItems)
        }
        attachOnScrolledListener()

        tabsRecycler.show()
    }

    private fun attachOnScrolledListener() {
        if (isOnScrolledListenerAttached) return

        tabsRecycler.addOnScrollListener(onScrolledListener)
        isOnScrolledListenerAttached = true
    }

    private fun detachOnScrolledListener() {
        if (!isOnScrolledListenerAttached) return

        tabsRecycler.removeOnScrollListener(onScrolledListener)
        isOnScrolledListenerAttached = false
    }

    private fun applyLayoutType(layoutType: LayoutType) {
        when (layoutType) {
            LayoutType.GRID -> {
                val columnCount = gridViewColumnCalculator.calculateNumberOfColumns(TAB_GRID_COLUMN_WIDTH_DP, TAB_GRID_MAX_COLUMN_COUNT)
                tabsRecycler.layoutManager = getGridLayoutManager(columnCount)
            }
            LayoutType.LIST -> {
                tabsRecycler.layoutManager = LinearLayoutManager(this@TabSwitcherActivity)
            }
        }
        tabsAdapter.onLayoutTypeChanged(layoutType)
        tabTouchHelper.onLayoutTypeChanged(layoutType)
        currentLayoutType = layoutType
    }

    private fun getGridLayoutManager(columnCount: Int): GridLayoutManager =
        GridLayoutManager(
            this,
            columnCount,
        ).apply {
            spanSizeLookup =
                object : SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (tabsAdapter.getTabSwitcherItem(position) is TrackersAnimationInfoPanel) {
                            columnCount
                        } else {
                            1
                        }
                }
        }

    private fun showGridLayoutButton() {
        layoutTypeMenuItem?.let { viewModeMenuItem ->
            viewModeMenuItem.setIcon(com.duckduckgo.mobile.android.R.drawable.ic_view_grid_24)
            viewModeMenuItem.title = getString(R.string.tabSwitcherGridViewMenu)
            viewModeMenuItem.setVisible(true)
        }
    }

    private fun showListLayoutButton() {
        layoutTypeMenuItem?.let { viewModeMenuItem ->
            viewModeMenuItem.setIcon(com.duckduckgo.mobile.android.R.drawable.ic_view_list_24)
            viewModeMenuItem.title = getString(R.string.tabSwitcherListViewMenu)
            viewModeMenuItem.setVisible(true)
        }
    }

    private fun scrollToActiveTab(items: List<TabSwitcherItem>): Boolean {
        val index = items.indexOfFirst {
            (it is NormalTab && it.isActive) || (it is DuckAiTab && it.isActive)
        }
        if (index == -1) return false
        scrollToPosition(index)
        return true
    }

    // Aligns the given item to the top
    private fun scrollPositionToTop(index: Int) {
        val layoutManager = tabsRecycler.layoutManager as? LinearLayoutManager ?: return
        val innerHeight = tabsRecycler.height - tabsRecycler.paddingTop - tabsRecycler.paddingBottom
        if (innerHeight <= 0) {
            tabsRecycler.doOnPreDraw { scrollPositionToTop(index) }
            return
        }
        layoutManager.scrollToPositionWithOffset(index, 0)
    }

    private fun scrollToPosition(index: Int) {
        val layoutManager = tabsRecycler.layoutManager as? LinearLayoutManager ?: return
        val innerHeight = tabsRecycler.height - tabsRecycler.paddingTop - tabsRecycler.paddingBottom
        if (innerHeight <= 0) {
            tabsRecycler.doOnPreDraw { scrollToPosition(index) }
            return
        }
        val rowHeight = tabsRecycler.children.firstOrNull {
            val pos = tabsRecycler.getChildAdapterPosition(it)
            pos != RecyclerView.NO_POSITION && tabsAdapter.getTabSwitcherItem(pos) !is TrackersAnimationInfoPanel
        }?.height ?: 0
        val centerOffset = (innerHeight - rowHeight) / 2
        val offset = if (rowHeight > 0) {
            val gridLayoutManager = layoutManager as? GridLayoutManager
            val spanCount = gridLayoutManager?.spanCount ?: 1
            val spanSizeLookup = gridLayoutManager?.spanSizeLookup
            val itemCount = tabsRecycler.adapter?.itemCount ?: 0
            val targetRow = spanSizeLookup?.getSpanGroupIndex(index, spanCount) ?: index
            val lastRow = if (itemCount > 0) {
                spanSizeLookup?.getSpanGroupIndex(itemCount - 1, spanCount) ?: (itemCount - 1)
            } else {
                targetRow
            }
            val rowsBelow = lastRow - targetRow
            val pinToBottomOffset = innerHeight - (rowsBelow + 1) * rowHeight - 20.toPx()
            maxOf(centerOffset, pinToBottomOffset)
        } else {
            centerOffset
        }
        layoutManager.scrollToPositionWithOffset(index, offset.coerceAtLeast(0))
    }

    // Returns the first fully visible item to use as the scroll anchor for a grid<->list swap
    private fun captureScrollAnchorPosition(): Int? {
        val layoutManager = tabsRecycler.layoutManager as? LinearLayoutManager ?: return null
        val completelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
        if (completelyVisible != RecyclerView.NO_POSITION) return completelyVisible
        return layoutManager.findFirstVisibleItemPosition().takeIf { it != RecyclerView.NO_POSITION }
    }

    private fun processCommand(command: Command) {
        when (command) {
            Close -> {
                finishAfterTransition()
            }
            is CloseAndShowUndoMessage -> {
                skipTabPurge = true
                setResult(
                    RESULT_OK,
                    Intent().apply {
                        putStringArrayListExtra(EXTRA_KEY_DELETED_TAB_IDS, ArrayList(command.deletedTabIds))
                    },
                )
                finishAfterTransition()
            }
            is CloseAllTabsRequest -> showCloseAllTabsConfirmation(command.numTabs)
            is ShareLinks -> launchShareMultipleLinkChooser(command.links)
            is ShareLink -> launchShareLinkChooser(command.link, command.title)
            is BookmarkTabsRequest -> showBookmarkTabsConfirmation(command.tabIds)
            is ShowUndoBookmarkMessage -> showBookmarkSnackbarWithUndo(command.numBookmarks)
            is CloseTabsRequest -> showCloseSelectedTabsConfirmation(command.tabIds, command.isClosingOtherTabs)
            is ShowUndoDeleteTabsMessage -> showTabsDeletedSnackbar(command.tabIds)
            ShowAnimatedTileDismissalDialog -> showAnimatedTileDismissalDialog()
            DismissAnimatedTileDismissalDialog -> tabSwitcherAnimationTileRemovalDialog!!.dismiss()
            Command.ShowFireBottomSheet -> onFireButtonClicked()
            Command.DismissSnackbar -> lastSnackbar?.dismiss()
            Command.SwitchToRegularMode -> fadeOutTabsThenRecreate(BrowserMode.REGULAR)
        }
    }

    private fun showBookmarkSnackbarWithUndo(numBookmarks: Int) {
        val message = resources.getQuantityString(R.plurals.tabSwitcherBookmarkToast, numBookmarks, numBookmarks)
        lastSnackbar = DefaultSnackbar(
            parentView = binding.root,
            message = message,
            anchor = snackbarAnchorView,
            action = getString(R.string.undoSnackbarAction),
            showAction = numBookmarks > 0,
            actionTextColor = getColorFromAttr(CommonR.attr.daxColorAccentBlue),
            onAction = viewModel::undoBookmarkAction,
            onDismiss = viewModel::finishBookmarkAction,
        )
        lastSnackbar?.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_tab_switcher_activity, menu)

        val popupBinding = PopupTabsMenuBinding.bind(popupMenu.contentView)
        val viewState = viewModel.viewState.value

        val numSelectedTabs = viewModel.viewState.value.numSelectedTabs
        menu.createDynamicInterface(
            numSelectedTabs = numSelectedTabs,
            popupMenu = popupBinding,
            toolbar = toolbar,
            dynamicMenu = viewState.dynamicInterface,
            navigationBar = binding.navigationBar,
        )

        return true
    }

    private fun initMenuClickListeners() {
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.gridLayoutMenuItem)) { viewModel.onGridLayoutSelected() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.listLayoutMenuItem)) { viewModel.onListLayoutSelected() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.selectAllMenuItem)) { viewModel.onSelectAllTabs() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.deselectAllMenuItem)) { viewModel.onDeselectAllTabs() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.shareSelectedLinksMenuItem)) { viewModel.onShareSelectedTabs() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.bookmarkSelectedTabsMenuItem)) { viewModel.onBookmarkSelectedTabs() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.selectTabsMenuItem)) { viewModel.onSelectionModeRequested() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.closeSelectedTabsMenuItem)) {
            viewModel.onCloseSelectedTabsRequested(
                fromOverflowMenu = true,
            )
        }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.closeOtherTabsMenuItem)) { viewModel.onCloseOtherTabsRequested() }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.closeAllTabsMenuItem)) { viewModel.onCloseAllTabsRequested() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.fireToolbarButton -> viewModel.onFireButtonTapped()
            R.id.popupMenuToolbarButton -> showPopupMenu(item.itemId)
            R.id.newTabToolbarButton -> onNewTabRequested(fromOverflowMenu = false)
            R.id.duckAIToolbarButton -> viewModel.onDuckAIButtonClicked()
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
        viewModel.onMenuOpened()
    }

    override fun onMenuOpened(
        featureId: Int,
        menu: Menu,
    ): Boolean {
        if (featureId == FEATURE_SUPPORT_ACTION_BAR) {
            viewModel.onMenuOpened()
        }
        return super.onMenuOpened(featureId, menu)
    }

    private fun onFireButtonClicked() {
        lifecycleScope.launch {
            val dialog = fireDialogProvider.createFireDialog(TabSwitcher)
            dialog.show(supportFragmentManager)
        }
    }

    override fun onNewTabRequested(fromOverflowMenu: Boolean) {
        // clear observers early to stop view updates
        removeObservers()

        viewModel.onNewTabRequested(fromOverflowMenu)
    }

    override fun onTabSelected(tabId: String) {
        launch { viewModel.onTabSelected(tabId) }
    }

    override fun onTabDeleted(
        position: Int,
        deletedBySwipe: Boolean,
    ) {
        tabsAdapter.getTabSwitcherItem(position)?.let { tab ->
            when (tab) {
                is NormalTab -> {
                    viewModel.onTabCloseInNormalModeRequested(tab, swipeGestureUsed = deletedBySwipe)
                }
                is DuckAiTab -> {
                    viewModel.onTabCloseInNormalModeRequested(tab, swipeGestureUsed = deletedBySwipe)
                }
                is TrackersAnimationInfoPanel -> Unit
                is SelectableTab -> Unit
            }
        }
    }

    override fun onTabMoved(
        from: Int,
        to: Int,
    ) {
        val isTrackerAnimationInfoPanelVisible = viewModel.tabSwitcherItems.firstOrNull() is TrackersAnimationInfoPanel
        val canSwapFromIndex = if (isTrackerAnimationInfoPanelVisible) 1 else 0
        val tabSwitcherItemCount = viewModel.tabSwitcherItems.size

        val canSwap = from in canSwapFromIndex..<tabSwitcherItemCount && to in canSwapFromIndex..<tabSwitcherItemCount
        if (canSwap) {
            tabsAdapter.onTabMoved(from, to)
            // Adjust indices if animation feature is enabled to account for the TrackerAnimationTile at index 0
            viewModel.onTabMoved(from - canSwapFromIndex, to - canSwapFromIndex)
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

    private fun showTabsDeletedSnackbar(tabIds: List<String>) {
        lastSnackbar = DefaultSnackbar(
            parentView = binding.root,
            message = resources.getQuantityString(R.plurals.tabSwitcherCloseTabsSnackbar, tabIds.size, tabIds.size),
            anchor = snackbarAnchorView,
            action = getString(R.string.tabClosedUndo),
            showAction = true,
            onAction = { launch { viewModel.onUndoDeleteTabs(tabIds) } },
            onDismiss = { launch { viewModel.onUndoDeleteSnackbarDismissed(tabIds) } },
        )
        lastSnackbar?.show()
    }

    private fun launchShareLinkChooser(
        url: String,
        title: String,
    ) {
        val intent =
            Intent(Intent.ACTION_SEND).also {
                it.type = "text/plain"
                it.putExtra(Intent.EXTRA_TEXT, url)
                it.putExtra(Intent.EXTRA_SUBJECT, title)
                it.putExtra(Intent.EXTRA_TITLE, title)
            }
        try {
            startActivity(Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "Activity not found: ${e.asLog()}" }
        }
    }

    private fun launchShareMultipleLinkChooser(urls: List<String>) {
        val title = getString(R.string.shareMultipleLinksTitle, urls.size)
        val intent =
            Intent(Intent.ACTION_SEND).also {
                it.type = "text/plain"
                it.putExtra(Intent.EXTRA_TEXT, urls.mapIndexed { index, url -> "${index + 1}. $url" }.joinToString("\n"))
                it.putExtra(Intent.EXTRA_SUBJECT, title)
                it.putExtra(Intent.EXTRA_TITLE, title)
            }
        try {
            startActivity(Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "Activity not found: ${e.asLog()}" }
        }
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
        removeObservers()
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeObservers()

        // we don't want to purge during device rotation
        if (isFinishing) {
            launch {
                if (!skipTabPurge) {
                    viewModel.purgeDeletableTabs()
                }
            }
        }
    }

    private fun removeObservers() {
        viewModel.tabSwitcherItemsLiveData.removeObservers(this)
    }

    private fun showCloseAllTabsConfirmation(numTabs: Int) {
        val title = resources.getQuantityString(R.plurals.tabSwitcherCloseAllTabsDialogTitle, numTabs, numTabs)
        val message = resources.getQuantityString(R.plurals.tabSwitcherCloseAllTabsDialogDescription, numTabs, numTabs)
        val closeTabButton = resources.getQuantityString(R.plurals.closeTabsConfirmationDialogCloseTabs, numTabs, numTabs)
        TextAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(closeTabButton, DESTRUCTIVE)
            .setNegativeButton(R.string.closeAppTabsConfirmationDialogCancel, GHOST_ALT)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onCloseAllTabsConfirmed()
                    }
                },
            ).show()
    }

    private fun showCloseSelectedTabsConfirmation(
        tabIds: List<String>,
        isClosingOtherTabs: Boolean,
    ) {
        val numTabs = tabIds.size
        val title =
            if (isClosingOtherTabs) {
                resources.getQuantityString(R.plurals.tabSwitcherCloseOtherTabsDialogTitle, numTabs, numTabs)
            } else {
                resources.getQuantityString(R.plurals.tabSwitcherCloseTabsDialogTitle, numTabs, numTabs)
            }
        val description = resources.getQuantityString(R.plurals.tabSwitcherCloseTabsDialogDescription, numTabs, numTabs)
        val closeTabButton = resources.getQuantityString(R.plurals.closeTabsConfirmationDialogCloseTabs, numTabs, numTabs)
        TextAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(description)
            .setPositiveButton(closeTabButton, DESTRUCTIVE)
            .setNegativeButton(R.string.closeAppTabsConfirmationDialogCancel, GHOST_ALT)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onCloseTabsConfirmed(tabIds)
                    }
                },
            ).show()
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
            ).show()
    }

    private fun showBookmarkTabsConfirmation(tabIds: List<String>) {
        val numTabs = tabIds.size
        val title = resources.getQuantityString(R.plurals.tabSwitcherBookmarkDialogTitle, numTabs, numTabs)
        TextAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(R.string.tabSwitcherBookmarkDialogDescription)
            .setPositiveButton(R.string.tabSwitcherBookmarkDialogPositiveButton, ButtonType.PRIMARY)
            .setNegativeButton(R.string.cancel, GHOST_ALT)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onBookmarkTabsConfirmed(tabIds)
                    }
                },
            ).show()
    }

    private fun showAnimatedTileDismissalDialog() {
        tabSwitcherAnimationTileRemovalDialog =
            TextAlertDialogBuilder(this)
                .setTitle(R.string.tabSwitcherAnimationTileRemovalDialogTitle)
                .setMessage(R.string.tabSwitcherAnimationTileRemovalDialogBody)
                .setPositiveButton(R.string.tabSwitcherAnimationTileRemovalDialogPositiveButton)
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
                ).build()
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
                }
            },
        )
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, TabSwitcherActivity::class.java)

        const val EXTRA_KEY_DELETED_TAB_IDS = "deletedTabIds"
        const val EXTRA_KEY_DUCK_AI_URL = "duckAIUrl"

        private const val TAB_GRID_COLUMN_WIDTH_DP = 180
        private const val TAB_GRID_MAX_COLUMN_COUNT = 4
        private const val MODE_SWITCH_FADE_OUT_MS = 180L
        private const val MODE_SWITCH_FADE_IN_MS = 220L

        private const val ANCHOR_REPIN_WINDOW_MS = 1000L
        private const val KEY_FADE_IN_AFTER_RECREATE = "fadeInAfterModeSwitchRecreate"
    }
}
