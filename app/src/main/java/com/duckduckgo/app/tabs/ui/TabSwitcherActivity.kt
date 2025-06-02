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
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR
import androidx.appcompat.widget.Toolbar
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
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.settings.clear.OnboardingExperimentFireAnimationHelper
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.TabSwitcherAnimationFeature
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackerAnimationInfoPanel
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
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode.Selection
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.button.ButtonType
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toDp
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import java.util.ArrayList
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

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

    @Inject
    lateinit var visualDesignExperimentDataStore: VisualDesignExperimentDataStore

    @Inject
    lateinit var onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles

    @Inject
    lateinit var onboardingExperimentFireAnimationHelper: OnboardingExperimentFireAnimationHelper

    private val viewModel: TabSwitcherViewModel by bindViewModel()

    private val tabsAdapter: TabSwitcherAdapter by lazy {
        TabSwitcherAdapter(
            isVisualExperimentEnabled = visualDesignExperimentDataStore.isExperimentEnabled.value,
            itemClickListener = this,
            webViewPreviewPersister = webViewPreviewPersister,
            lifecycleOwner = this,
            faviconManager = faviconManager,
            dispatchers = dispatchers,
            trackerCountAnimator = trackerCountAnimator,
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
    private var skipTabPurge: Boolean = false

    private lateinit var tabTouchHelper: TabTouchHelper
    private lateinit var tabsRecycler: RecyclerView
    private lateinit var tabItemDecorator: TabItemDecorator
    private lateinit var toolbar: Toolbar

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
        configureFabs()
        configureObservers()
        configureOnBackPressedListener()

        if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            initMenuClickListeners()
        }
    }

    private fun configureFabs() {
        binding.mainFab.apply {
            setOnClickListener {
                viewModel.onFabClicked()
            }
        }

        binding.aiChatFab.setOnClickListener {
            viewModel.onDuckChatFabClicked()
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

        tabItemDecorator = TabItemDecorator(this, visualDesignExperimentDataStore)
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
            object : OnScrollListener() {
                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy.toDp(recyclerView.context) > FAB_SCROLL_THRESHOLD) {
                        binding.mainFab.shrink()
                    } else if (dy.toDp(recyclerView.context) < -FAB_SCROLL_THRESHOLD) {
                        binding.mainFab.extend()
                    }
                }
            },
        )
    }

    private fun updateToolbarTitle(mode: Mode, tabCount: Int) {
        toolbar.title = if (mode is Selection) {
            if (mode.selectedTabs.isEmpty()) {
                getString(R.string.selectTabsMenuItem)
            } else {
                getString(R.string.tabSelectionTitle, mode.selectedTabs.size)
            }
        } else {
            resources.getQuantityString(R.plurals.tabSwitcherTitle, tabCount, tabCount)
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
                viewModel.selectionViewState.flowWithLifecycle(lifecycle).collectLatest {
                    tabsRecycler.invalidateItemDecorations()
                    tabsAdapter.updateData(it.tabSwitcherItems)

                    updateToolbarTitle(it.mode, it.tabs.size)
                    updateTabGridItemDecorator()

                    tabTouchHelper.mode = it.mode

                    invalidateOptionsMenu()

                    if (firstTimeLoadingTabsList && it.tabs.isNotEmpty()) {
                        firstTimeLoadingTabsList = false
                        scrollToActiveTab()
                    }
                }
            }
        } else {
            viewModel.activeTab.observe(this) { tab ->
                if (tab != null && !tab.deletable) {
                    updateTabGridItemDecorator()
                }
            }

            viewModel.tabSwitcherItemsLiveData.observe(this) { tabSwitcherItems ->
                tabsAdapter.updateData(tabSwitcherItems)

                val noTabSelected = tabSwitcherItems.none { (it as? NormalTab)?.isActive == true }
                if (noTabSelected && tabSwitcherItems.isNotEmpty()) {
                    updateTabGridItemDecorator()
                }

                if (firstTimeLoadingTabsList) {
                    firstTimeLoadingTabsList = false
                    scrollToActiveTab()
                }
            }

            viewModel.deletableTabs.observe(this) {
                if (it.isNotEmpty()) {
                    showTabDeletedSnackbar(it.last())
                }
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
        tabsRecycler.removeOnScrollListener(onScrolledListener)

        val centerOffsetPercent = getCurrentCenterOffset()

        when (layoutType) {
            LayoutType.GRID -> {
                val columnCount = gridViewColumnCalculator.calculateNumberOfColumns(TAB_GRID_COLUMN_WIDTH_DP, TAB_GRID_MAX_COLUMN_COUNT)

                val gridLayoutManager = getGridLayoutManager(columnCount)
                tabsRecycler.layoutManager = gridLayoutManager

                if (!tabManagerFeatureFlags.multiSelection().isEnabled()) {
                    showListLayoutButton()
                }
            }
            LayoutType.LIST -> {
                tabsRecycler.layoutManager = LinearLayoutManager(this@TabSwitcherActivity)

                if (!tabManagerFeatureFlags.multiSelection().isEnabled()) {
                    showGridLayoutButton()
                }
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
            is CloseAllTabsRequest -> {
                if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
                    showCloseAllTabsConfirmation(command.numTabs)
                } else {
                    showCloseAllTabsConfirmation()
                }
            }
            is ShareLinks -> launchShareMultipleLinkChooser(command.links)
            is ShareLink -> launchShareLinkChooser(command.link, command.title)
            is BookmarkTabsRequest -> showBookmarkTabsConfirmation(command.tabIds)
            is ShowUndoBookmarkMessage -> showBookmarkSnackbarWithUndo(command.numBookmarks)
            is CloseTabsRequest -> showCloseSelectedTabsConfirmation(command.tabIds, command.isClosingOtherTabs)
            is ShowUndoDeleteTabsMessage -> showTabsDeletedSnackbar(command.tabIds)
            ShowAnimatedTileDismissalDialog -> showAnimatedTileDismissalDialog()
            DismissAnimatedTileDismissalDialog -> tabSwitcherAnimationTileRemovalDialog!!.dismiss()
        }
    }

    private fun showBookmarkSnackbarWithUndo(numBookmarks: Int) {
        val message = resources.getQuantityString(R.plurals.tabSwitcherBookmarkToast, numBookmarks, numBookmarks)
        TabSwitcherSnackbar(
            anchorView = toolbar,
            message = message,
            action = getString(R.string.undoSnackbarAction),
            showAction = numBookmarks > 0,
            onAction = viewModel::undoBookmarkAction,
            onDismiss = viewModel::finishBookmarkAction,
        ).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (tabManagerFeatureFlags.multiSelection().isEnabled()) {
            menuInflater.inflate(R.menu.menu_tab_switcher_activity_with_selection, menu)

            val popupBinding = PopupTabsMenuBinding.bind(popupMenu.contentView)
            val viewState = viewModel.selectionViewState.value

            val numSelectedTabs = viewModel.selectionViewState.value.numSelectedTabs
            menu.createDynamicInterface(
                numSelectedTabs,
                popupBinding,
                binding.mainFab,
                binding.aiChatFab,
                tabsRecycler,
                toolbar,
                viewState.dynamicInterface,
            )
        } else {
            menuInflater.inflate(R.menu.menu_tab_switcher_activity, menu)
            layoutTypeMenuItem = menu.findItem(R.id.layoutTypeMenuItem)

            when (viewModel.layoutType.value) {
                LayoutType.GRID -> showListLayoutButton()
                LayoutType.LIST -> showGridLayoutButton()
                null -> layoutTypeMenuItem?.isVisible = false
            }
        }

        return true
    }

    private fun initMenuClickListeners() {
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.newTabMenuItem)) { onNewTabRequested(fromOverflowMenu = true) }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.duckChatMenuItem)) { viewModel.onDuckChatMenuClicked() }
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
        viewModel.onMenuOpened()
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val duckChatMenuItem = menu?.findItem(R.id.duckChat)
        duckChatMenuItem?.isVisible = duckChat.showInBrowserMenu.value

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
            onboardingDesignExperimentToggles = onboardingDesignExperimentToggles,
            onboardingExperimentFireAnimationHelper = onboardingExperimentFireAnimationHelper,
        )
        dialog.show()
    }

    private fun onLayoutTypeToggled() {
        viewModel.onLayoutTypeToggled()
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

    override fun onTabDeleted(position: Int, deletedBySwipe: Boolean) {
        tabsAdapter.getTabSwitcherItem(position)?.let { tab ->
            when (tab) {
                is Tab -> {
                    viewModel.onTabCloseInNormalModeRequested(tab, swipeGestureUsed = deletedBySwipe)
                }
                is TrackerAnimationInfoPanel -> Unit
            }
        }
    }

    override fun onTabMoved(from: Int, to: Int) {
        if (tabSwitcherAnimationFeature.self().isEnabled()) {
            val isTrackerAnimationInfoPanelVisible = viewModel.tabSwitcherItems.firstOrNull() is TrackerAnimationInfoPanel
            val canSwapFromIndex = if (isTrackerAnimationInfoPanelVisible) 1 else 0
            val tabSwitcherItemCount = viewModel.tabSwitcherItems.size

            val canSwap = from in canSwapFromIndex..<tabSwitcherItemCount && to in canSwapFromIndex..<tabSwitcherItemCount
            if (canSwap) {
                tabsAdapter.onTabMoved(from, to)
                // Adjust indices if animation feature is enabled to account for the TrackerAnimationTile at index 0
                viewModel.onTabMoved(from - canSwapFromIndex, to - canSwapFromIndex)
            }
        } else {
            val tabCount = viewModel.tabSwitcherItems.size
            val canSwap = from in 0..< tabCount && to in 0..< tabCount
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

    private fun showTabDeletedSnackbar(tab: TabEntity) {
        TabSwitcherSnackbar(
            anchorView = toolbar,
            message = getString(R.string.tabClosed),
            action = getString(R.string.tabClosedUndo),
            showAction = true,
            onAction = { launch { viewModel.onUndoDeleteTab(tab) } },
            onDismiss = { launch { viewModel.purgeDeletableTabs() } },
        ).show()
    }

    private fun showTabsDeletedSnackbar(tabIds: List<String>) {
        TabSwitcherSnackbar(
            anchorView = toolbar,
            message = resources.getQuantityString(R.plurals.tabSwitcherCloseTabsSnackbar, tabIds.size, tabIds.size),
            action = getString(R.string.tabClosedUndo),
            showAction = true,
            onAction = { launch { viewModel.onUndoDeleteTabs(tabIds) } },
            onDismiss = { launch { viewModel.onUndoDeleteSnackbarDismissed(tabIds) } },
        ).show()
    }

    private fun launchShareLinkChooser(
        url: String,
        title: String,
    ) {
        val intent = Intent(Intent.ACTION_SEND).also {
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

    private fun launchShareMultipleLinkChooser(
        urls: List<String>,
    ) {
        val title = getString(R.string.shareMultipleLinksTitle, urls.size)
        val intent = Intent(Intent.ACTION_SEND).also {
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
                if (!tabManagerFeatureFlags.multiSelection().isEnabled() || !skipTabPurge) {
                    viewModel.purgeDeletableTabs()
                }
            }
        }
    }

    private fun removeObservers() {
        viewModel.tabSwitcherItemsLiveData.removeObservers(this)
        viewModel.deletableTabs.removeObservers(this)
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
            )
            .show()
    }

    private fun showCloseSelectedTabsConfirmation(tabIds: List<String>, isClosingOtherTabs: Boolean) {
        val numTabs = tabIds.size
        val title = if (isClosingOtherTabs) {
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
            )
            .show()
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
        const val EXTRA_KEY_DELETED_TAB_IDS = "deletedTabIds"

        private const val TAB_GRID_COLUMN_WIDTH_DP = 180
        private const val TAB_GRID_MAX_COLUMN_COUNT = 4
        private const val KEY_FIRST_TIME_LOADING = "FIRST_TIME_LOADING"
        private const val FAB_SCROLL_THRESHOLD = 7
    }
}
