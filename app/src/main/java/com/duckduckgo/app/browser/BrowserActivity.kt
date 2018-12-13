/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_TEXT
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Observer
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.BrowserViewModel.Command.Query
import com.duckduckgo.app.browser.BrowserViewModel.Command.Refresh
import com.duckduckgo.app.feedback.ui.FeedbackActivity
import com.duckduckgo.app.fire.DataClearer
import com.duckduckgo.app.global.ApplicationClearDataState
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.intentText
import com.duckduckgo.app.global.view.*
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherActivity
import kotlinx.android.synthetic.main.activity_browser.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.longToast
import timber.log.Timber
import javax.inject.Inject

class BrowserActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var clearPersonalDataAction: ClearPersonalDataAction

    @Inject
    lateinit var dataClearer: DataClearer

    private var currentTab: BrowserTabFragment? = null

    private val viewModel: BrowserViewModel by bindViewModel()

    private var instanceStateBundles: CombinedInstanceState? = null

    private var lastIntent: Intent? = null

    private lateinit var renderer: BrowserStateRenderer

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.daggerInject()

        renderer = BrowserStateRenderer()

        Timber.i("onCreate called. freshAppLaunch: ${dataClearer.isFreshAppLaunch}, savedInstanceState: $savedInstanceState")

        val newInstanceState = if (dataClearer.isFreshAppLaunch) null else savedInstanceState
        instanceStateBundles = CombinedInstanceState(originalInstanceState = savedInstanceState, newInstanceState = newInstanceState)

        super.onCreate(savedInstanceState = newInstanceState, daggerInject = false)
        setContentView(R.layout.activity_browser)
        viewModel.viewState.observe(this, Observer {
            renderer.renderBrowserViewState(it)
        })
        viewModel.awaitClearDataFinishedNotification()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Timber.i("onNewIntent: $intent")

        if (dataClearer.dataClearerState.value == ApplicationClearDataState.FINISHED) {
            Timber.i("Automatic data clearer has finished, so processing intent now")
            launchNewSearchOrQuery(intent)
        } else {
            Timber.i("Automatic data clearer not yet finished, so deferring processing of intent")
            lastIntent = intent
        }
    }

    private fun openNewTab(tabId: String, url: String? = null) {
        Timber.i("Opening new tab, url: $url, tabId: $tabId")
        val fragment = BrowserTabFragment.newInstance(tabId, url)
        val transaction = supportFragmentManager.beginTransaction()
        val tab = currentTab
        if (tab == null) {
            transaction.replace(R.id.fragmentContainer, fragment, tabId)
        } else {
            transaction.hide(tab)
            transaction.add(R.id.fragmentContainer, fragment, tabId)
        }
        transaction.commit()
        currentTab = fragment
    }

    private fun selectTab(tab: TabEntity?) {
        Timber.i("Select tab: $tab")

        if (tab == null) return

        if (tab.tabId == currentTab?.tabId) return

        val fragment = supportFragmentManager.findFragmentByTag(tab.tabId) as? BrowserTabFragment
        if (fragment == null) {
            openNewTab(tab.tabId, tab.url)
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
        currentTab?.let {
            transaction.hide(it)
        }
        transaction.show(fragment)
        transaction.commit()
        currentTab = fragment
    }

    private fun removeTabs(fragments: List<BrowserTabFragment>) {
        val transaction = supportFragmentManager.beginTransaction()
        fragments.forEach { transaction.remove(it) }
        transaction.commit()
    }

    private fun launchNewSearchOrQuery(intent: Intent?) {

        Timber.i("launchNewSearchOrQuery: $intent")

        if (intent == null) {
            return
        }

        if (intent.getBooleanExtra(PERFORM_FIRE_ON_ENTRY_EXTRA, false)) {

            Timber.i("Clearing everything as a result of $PERFORM_FIRE_ON_ENTRY_EXTRA flag being set")
            GlobalScope.launch {
                clearPersonalDataAction.clearTabsAndAllDataAsync(appInForeground = true, shouldFireDataClearPixel = true)
                clearPersonalDataAction.setAppUsedSinceLastClearFlag(false)
                clearPersonalDataAction.killAndRestartProcess()
            }

            return
        }

        if (intent.getBooleanExtra(LAUNCHED_FROM_FIRE_EXTRA, false)) {
            Timber.i("Launched from fire")
            Toast.makeText(applicationContext, R.string.fireDataCleared, Toast.LENGTH_LONG).show()
        }

        if (launchNewSearch(intent)) {
            Timber.w("new tab requested")
            viewModel.onNewTabRequested()
            return
        }

        val sharedText = intent.intentText
        if (sharedText != null) {
            Timber.w("opening in new tab requested for $sharedText")
            viewModel.onOpenInNewTabRequested(sharedText)
            return
        }
    }

    private fun configureObservers() {
        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
        viewModel.selectedTab.observe(this, Observer {
            if (it != null) selectTab(it)
        })
        viewModel.tabs.observe(this, Observer {
            clearStaleTabs(it)
            viewModel.onTabsUpdated(it)
        })
    }

    private fun removeObservers() {
        viewModel.command.removeObservers(this)
        viewModel.selectedTab.removeObservers(this)
        viewModel.tabs.removeObservers(this)
    }

    private fun clearStaleTabs(updatedTabs: List<TabEntity>?) {
        if (updatedTabs == null) {
            return
        }

        val stale = supportFragmentManager
            .fragments.mapNotNull { it as? BrowserTabFragment }
            .filter { fragment -> updatedTabs.none { it.tabId == fragment.tabId } }

        if (stale.isNotEmpty()) {
            removeTabs(stale)
        }
    }

    private fun processCommand(command: Command?) {
        Timber.i("Processing command: $command")
        when (command) {
            is Query -> currentTab?.submitQuery(command.query)
            is Refresh -> currentTab?.refresh()
            is Command.DisplayMessage -> applicationContext?.longToast(command.messageId)
        }
    }

    private fun launchNewSearch(intent: Intent): Boolean {
        return intent.getBooleanExtra(NEW_SEARCH_EXTRA, false) || intent.action == Intent.ACTION_ASSIST
    }

    fun launchPrivacyDashboard() {
        currentTab?.tabId?.let {
            startActivityForResult(PrivacyDashboardActivity.intent(this, it), DASHBOARD_REQUEST_CODE)
        }
    }

    fun launchFire() {
        val dialog = FireDialog(context = this, clearPersonalDataAction = clearPersonalDataAction)
        dialog.clearStarted = {
            clearingInProgressView.show()
        }
        dialog.clearComplete = { viewModel.onClearComplete() }
        dialog.show()
    }

    fun launchTabSwitcher() {
        startActivity(TabSwitcherActivity.intent(this))
    }

    fun launchNewTab() {
        viewModel.onNewTabRequested()
    }

    fun openInNewTab(query: String) {
        viewModel.onOpenInNewTabRequested(query)
    }

    fun launchBrokenSiteFeedback(url: String?) {
        startActivity(FeedbackActivity.intent(this, true, url))
    }

    fun launchSettings() {
        startActivity(SettingsActivity.intent(this))
    }

    fun launchBookmarks() {
        startActivity(BookmarksActivity.intent(this))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == DASHBOARD_REQUEST_CODE) {
            viewModel.receivedDashboardResult(resultCode)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {
        if (currentTab?.onBackPressed() != true) {
            super.onBackPressed()
        }
    }

    companion object {

        fun intent(context: Context, queryExtra: String? = null, newSearch: Boolean = false, launchedFromFireAction: Boolean = false): Intent {
            val intent = Intent(context, BrowserActivity::class.java)
            intent.putExtra(EXTRA_TEXT, queryExtra)
            intent.putExtra(NEW_SEARCH_EXTRA, newSearch)
            intent.putExtra(LAUNCHED_FROM_FIRE_EXTRA, launchedFromFireAction)
            return intent
        }

        const val NEW_SEARCH_EXTRA = "NEW_SEARCH_EXTRA"
        const val PERFORM_FIRE_ON_ENTRY_EXTRA = "PERFORM_FIRE_ON_ENTRY_EXTRA"
        const val LAUNCHED_FROM_FIRE_EXTRA = "LAUNCHED_FROM_FIRE_EXTRA"
        private const val DASHBOARD_REQUEST_CODE = 100
    }

    inner class BrowserStateRenderer {

        private var lastSeenBrowserState: BrowserViewModel.ViewState? = null
        private var processedOriginalIntent = false

        fun renderBrowserViewState(viewState: BrowserViewModel.ViewState) {
            renderIfChanged(viewState, lastSeenBrowserState) {
                lastSeenBrowserState = viewState

                if (viewState.hideWebContent) {
                    hideWebContent()
                } else {
                    showWebContent()
                }
            }
        }

        private fun showWebContent() {
            Timber.d("BrowserActivity can now start displaying web content. instance state is $instanceStateBundles")
            configureObservers()
            clearingInProgressView.gone()

            if (lastIntent != null) {
                Timber.i("There was a deferred intent to process; handling now")
                launchNewSearchOrQuery(lastIntent)
                lastIntent = null
                return
            }

            if (!processedOriginalIntent && instanceStateBundles?.originalInstanceState == null) {
                Timber.i("Original instance state is null, so will inspect intent for actions to take. $intent")
                launchNewSearchOrQuery(intent)
                processedOriginalIntent = true
            }
        }
    }

    private fun hideWebContent() {
        Timber.d("Hiding web view content")
        removeObservers()
        clearingInProgressView.show()
    }

    private data class CombinedInstanceState(val originalInstanceState: Bundle?, val newInstanceState: Bundle?)
}