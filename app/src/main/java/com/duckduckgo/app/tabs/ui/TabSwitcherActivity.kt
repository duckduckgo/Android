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
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.downloads.DownloadsActivity
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.Close
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.CloseAllTabsRequest
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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

    private val viewModel: TabSwitcherViewModel by bindViewModel()

    private val tabsAdapter: TabSwitcherAdapter by lazy { TabSwitcherAdapter(this, webViewPreviewPersister, this, faviconManager) }

    // we need to scroll to show selected tab, but only if it is the first time loading the tabs.
    private var firstTimeLoadingTabsList = true

    private var selectedTabId: String? = null

    private lateinit var tabsRecycler: RecyclerView
    private lateinit var tabGridItemDecorator: TabGridItemDecorator
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tab_switcher)
        extractIntentExtras()
        configureViewReferences()
        setupToolbar(toolbar)
        configureRecycler()
        configureObservers()
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
        val layoutManager = GridLayoutManager(this, numberColumns)
        tabsRecycler.layoutManager = layoutManager
        tabsRecycler.adapter = tabsAdapter

        val swipeListener = ItemTouchHelper(
            TabTouchHelper(numberColumns, this::onTabDeleted, this::onTabMoved, this::onTabDraggingStarted, this::onTabDraggingFinished),
        )
        swipeListener.attachToRecyclerView(tabsRecycler)

        tabGridItemDecorator = TabGridItemDecorator(this, selectedTabId)
        tabsRecycler.addItemDecoration(tabGridItemDecorator)
    }

    private fun configureObservers() {
        viewModel.tabs.observe(this) { tabs ->
            render(tabs)

            val noTabSelected = tabs.none { it.tabId == tabGridItemDecorator.selectedTabId }
            if (noTabSelected && tabs.isNotEmpty()) {
                updateTabGridItemDecorator(tabs.last())
            }
        }
        viewModel.activeTab.observe(this) { tab ->
            if (tab != null && tab.tabId != tabGridItemDecorator.selectedTabId && !tab.deletable) {
                updateTabGridItemDecorator(tab)
            }
        }
        viewModel.deletableTabs.observe(this) {
            if (it.isNotEmpty()) {
                onDeletableTab(it.last())
            }
        }
        viewModel.command.observe(this) {
            processCommand(it)
        }
    }

    private fun render(tabs: List<TabEntity>) {
        tabsAdapter.updateData(tabs)

        if (firstTimeLoadingTabsList) {
            firstTimeLoadingTabsList = false

            scrollToShowCurrentTab()
        }
    }

    private fun scrollToShowCurrentTab() {
        val index = tabsAdapter.adapterPositionForTab(selectedTabId)
        tabsRecycler.post { tabsRecycler.scrollToPosition(index) }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Close -> finishAfterTransition()
            is CloseAllTabsRequest -> showCloseAllTabsConfirmation()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_tab_switcher_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.fire -> onFire()
            R.id.newTab, R.id.newTabOverflow -> onNewTabRequested()
            R.id.closeAllTabs -> closeAllTabs()
            R.id.downloads -> showDownloads()
            R.id.settings -> showSettings()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val closeAllTabsMenuItem = menu?.findItem(R.id.closeAllTabs)
        closeAllTabsMenuItem?.isVisible = viewModel.tabs.value?.isNotEmpty() == true
        return super.onPrepareOptionsMenu(menu)
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
        )
        dialog.show()
    }

    override fun onNewTabRequested() {
        clearObserversEarlyToStopViewUpdates()
        launch { viewModel.onNewTabRequested() }
    }

    override fun onTabSelected(tab: TabEntity) {
        selectedTabId = tab.tabId
        updateTabGridItemDecorator(tab)
        launch { viewModel.onTabSelected(tab) }
    }

    private fun updateTabGridItemDecorator(tab: TabEntity) {
        tabGridItemDecorator.selectedTabId = tab.tabId
        tabsRecycler.invalidateItemDecorations()
    }

    override fun onTabDeleted(position: Int) {
        val tab = tabsAdapter.getTab(position)
        launch { viewModel.onMarkTabAsDeletable(tab) }
    }

    override fun onTabMoved(from: Int, to: Int) {
        launch {
            val tabCount = viewModel.tabs.value?.size ?: 0
            val canSwap = from in 0..< tabCount && to in 0..< tabCount
            if (canSwap) {
                viewModel.onTabMoved(from, to)
            }
        }
    }

    private fun onTabDraggingStarted() {
        tabsAdapter.onDraggingStarted()

        // remove the tab selection border while dragging because it doesn't scale well
        while (tabsRecycler.itemDecorationCount > 1) {
            tabsRecycler.removeItemDecorationAt(1)
        }
    }

    private fun onTabDraggingFinished() {
        tabsAdapter.onDraggingFinished()

        tabsRecycler.addItemDecoration(tabGridItemDecorator)
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
    }

    private fun showSettings() {
        startActivity(SettingsActivity.intent(this))
    }

    override fun finish() {
        clearObserversEarlyToStopViewUpdates()
        super.finish()
        overridePendingTransition(R.anim.slide_from_bottom, R.anim.tab_anim_fade_out)
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
            .setDestructiveButtons(true)
            .setPositiveButton(R.string.closeAppTabsConfirmationDialogClose)
            .setNegativeButton(R.string.closeAppTabsConfirmationDialogCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onCloseAllTabsConfirmed()
                    }
                },
            )
            .show()
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
    }
}
