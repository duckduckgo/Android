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
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.Close
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.DisplayMessage
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.anko.contentView
import org.jetbrains.anko.longToast
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class TabSwitcherActivity : DuckDuckGoActivity(), TabSwitcherListener, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + Dispatchers.Main

    @Inject
    lateinit var clearPersonalDataAction: ClearDataAction

    @Inject
    lateinit var gridViewColumnCalculator: GridViewColumnCalculator

    @Inject
    lateinit var webViewPreviewPersister: WebViewPreviewPersister

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var ctaViewModel: CtaViewModel

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var userEventsStore: UserEventsStore

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
            SwipeToCloseTabListener(
                tabsAdapter, numberColumns,
                object : SwipeToCloseTabListener.OnTabSwipedListener {
                    override fun onSwiped(tab: TabEntity) {
                        onTabDeleted(tab)
                    }
                }
            )
        )
        swipeListener.attachToRecyclerView(tabsRecycler)

        tabGridItemDecorator = TabGridItemDecorator(this, selectedTabId)
        tabsRecycler.addItemDecoration(tabGridItemDecorator)
    }

    private fun configureObservers() {
        viewModel.tabs.observe(
            this,
            Observer<List<TabEntity>> {
                render(it)
            }
        )
        viewModel.deletableTabs.observe(
            this,
            {
                if (it.isNotEmpty()) {
                    onDeletableTab(it.last())
                }
            }
        )
        viewModel.command.observe(
            this,
            Observer {
                processCommand(it)
            }
        )
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
        tabsRecycler.scrollToPosition(index)
    }

    private fun processCommand(command: Command?) {
        when (command) {
            is DisplayMessage -> applicationContext?.longToast(command.messageId)
            is Close -> finishAfterTransition()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_tab_switcher_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.fire -> onFire()
            R.id.newTab, R.id.newTabOverflow -> onNewTabRequested()
            R.id.closeAllTabs -> closeAllTabs()
            R.id.settings -> showSettings()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onFire() {
        pixel.fire(Pixel.PixelName.FORGET_ALL_PRESSED_TABSWITCHING)
        val dialog = FireDialog(
            context = this,
            clearPersonalDataAction = clearPersonalDataAction,
            ctaViewModel = ctaViewModel,
            pixel = pixel,
            settingsDataStore = settingsDataStore,
            userEventsStore = userEventsStore
        )
        dialog.clearComplete = { viewModel.onClearComplete() }
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

    override fun onTabDeleted(tab: TabEntity) {
        launch { viewModel.onMarkTabAsDeletable(tab) }
    }

    private fun onDeletableTab(tab: TabEntity) {
        Snackbar.make(
            contentView!!,
            getString(R.string.tabClosed),
            Snackbar.LENGTH_LONG
        )
            .setDuration(3500) // 3.5 seconds
            .setAction(R.string.tabClosedUndo) {
                // noop, handled in onDismissed callback
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    when (event) {
                        // handle the UNDO action here as we only have one
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_ACTION -> launch { viewModel.undoDeletableTab(tab) }
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_SWIPE,
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_TIMEOUT -> launch { viewModel.purgeDeletableTabs() }
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_CONSECUTIVE,
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_MANUAL -> { /* noop */ }
                    }
                }
            })
            .apply { view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).maxLines = 1 }
            .show()
    }

    private fun closeAllTabs() {
        launch {
            viewModel.tabs.value?.forEach {
                viewModel.onTabDeleted(it)
            }
        }
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

    companion object {
        fun intent(context: Context, selectedTabId: String? = null): Intent {
            val intent = Intent(context, TabSwitcherActivity::class.java)
            intent.putExtra(EXTRA_KEY_SELECTED_TAB, selectedTabId)
            return intent
        }

        const val EXTRA_KEY_SELECTED_TAB = "selected"

        private const val TAB_GRID_COLUMN_WIDTH_DP = 180
        private const val TAB_GRID_MAX_COLUMN_COUNT = 4
    }
}
