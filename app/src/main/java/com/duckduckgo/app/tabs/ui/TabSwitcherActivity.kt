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
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.core.view.drawToBitmap
import androidx.lifecycle.Lifecycle
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

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
        }

    // we need to scroll to show selected tab, but only if it is the first time loading the tabs.
    private var firstTimeLoadingTabsList = true

    private var currentLayoutType: LayoutType? = null

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

    private var modeSwitch: ModeSwitch? = null

    /**
     * Holds the bitmap overlay covering the recycler
     * and a reference to the items list visible at fade-out time; the observer compares against
     * [staleItems] to detect when the new mode's tabs have arrived.
     */
    private data class ModeSwitch(
        val overlay: ImageView?,
        val staleItems: List<TabSwitcherItem>,
    )

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

        tabsAdapter.setAnimationTileCloseClickListener {
            viewModel.onTrackerAnimationInfoPanelClicked()
        }

        configureViewReferences()
        setupToolbar(toolbar)
        configureBrowserModeToggle()
        configureRecycler()
        configureNavigationBar()

        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }

        configureObservers()
        configureOnBackPressedListener()

        initMenuClickListeners()
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

        // Seed the LayoutManager from the cached layoutType before observers start. Without
        // this, the layoutType collector's first emission on rotation would swap the LM and
        // discard the viewState collector's pending scrollToActiveTab.
        viewModel.layoutType.value?.let {
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

    private fun configureBrowserModeToggle() {
        if (!viewModel.isBrowserModeToggleVisible) return

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
            fadeOutAndSwitchMode(mode)
        }

        applyViewState(viewModel.viewState.value)
    }

    private fun applyViewState(state: TabSwitcherViewModel.ViewState) {
        browserModeToggle?.setMode(state.browserMode)
        state.regularTabCount?.let { browserModeToggle?.setRegularTabCount(it) }
        updateToolbarTitle(state.mode, state.tabs.size)
    }

    // Snapshot the recycler and overlay it; the recycler updates underneath, hidden by the
    // bitmap. The observer reveals once fresh items arrive.
    private fun fadeOutAndSwitchMode(newMode: BrowserMode) {
        if (modeSwitch != null) return

        val overlay = runCatching { tabsRecycler.drawToBitmap() }.getOrNull()?.let { bitmap ->
            ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_XY
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }.also(tabsContainer::addView)
        }
        if (overlay != null) {
            tabsRecycler.alpha = 0f
        }

        val thisSwitch = ModeSwitch(overlay, viewModel.viewState.value.tabSwitcherItems)
        modeSwitch = thisSwitch

        // Suppress diff animations across the swap so items don't keep shuffling
        tabsRecycler.itemAnimator = null

        browserModeToggle?.setMode(newMode)
        viewModel.onBrowserModeToggled(newMode)

        // Fallback for when fresh items never arrive (e.g. both modes empty). Identity-checked so
        // a stale timer from a previous switch can't reveal the current one.
        tabsRecycler.postDelayed({
            if (modeSwitch === thisSwitch) revealNewMode()
        }, FADE_IN_FALLBACK_MS)
    }

    private fun revealNewMode() {
        val state = modeSwitch ?: return
        modeSwitch = null
        tabsRecycler.itemAnimator = DefaultItemAnimator()

        tabsRecycler.animate()
            .alpha(1f)
            .setDuration(FADE_DURATION_MS)
            .start()
        state.overlay?.animate()
            ?.alpha(0f)
            ?.setDuration(FADE_DURATION_MS)
            ?.withEndAction { tabsContainer.removeView(state.overlay) }
            ?.start()
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
                tabsRecycler.invalidateItemDecorations()

                val staleItems = modeSwitch?.staleItems
                val freshAfterModeSwitch = staleItems != null && it.tabSwitcherItems !== staleItems
                val shouldTryScroll = it.tabs.isNotEmpty() && (firstTimeLoadingTabsList || freshAfterModeSwitch)

                tabsAdapter.updateData(it.tabSwitcherItems) {
                    // Scroll inside the commit callback so the new items are committed first.
                    // If no active tab yet (race with flowSelectedTab), retry on next emission.
                    val scrolled = shouldTryScroll && scrollToActiveTab(it.tabSwitcherItems)
                    if (scrolled) firstTimeLoadingTabsList = false
                    if (freshAfterModeSwitch) {
                        tabsRecycler.post(::revealNewMode)
                    }
                }

                applyViewState(it)
                updateTabGridItemDecorator()

                tabTouchHelper.mode = it.mode

                invalidateOptionsMenu()
            }
        }

        lifecycleScope.launch {
            viewModel.layoutType.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).filterNotNull().collect {
                updateLayoutType(it)
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
        tabsRecycler.hide()
        detachOnScrolledListener()

        val centerOffsetPercent = getCurrentCenterOffset()

        applyLayoutType(layoutType)

        if (centerOffsetPercent.isFinite()) {
            scrollToPreviousCenterOffset(
                centerOffsetPercent = centerOffsetPercent,
                onScrollCompleted = {
                    attachOnScrolledListener()
                },
            )
        } else {
            scrollToActiveTab(viewModel.viewState.value.tabSwitcherItems)
            attachOnScrolledListener()
        }

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

    private fun updateTabGridItemDecorator() {
        tabsRecycler.invalidateItemDecorations()
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
        private const val FADE_DURATION_MS = 180L
        private const val FADE_IN_FALLBACK_MS = 1200L
    }
}
