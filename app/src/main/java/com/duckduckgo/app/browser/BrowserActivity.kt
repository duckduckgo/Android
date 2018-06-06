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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_TEXT
import android.os.Bundle
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.BrowserViewModel.Command.Query
import com.duckduckgo.app.browser.BrowserViewModel.Command.Refresh
import com.duckduckgo.app.feedback.ui.FeedbackActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.intentText
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherActivity
import org.jetbrains.anko.longToast
import timber.log.Timber
import javax.inject.Inject


class BrowserActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private var currentTab: BrowserTabFragment? = null

    private val viewModel: BrowserViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(BrowserViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        if (savedInstanceState == null) {
            launchNewSearchOrQuery(intent)
        }
        configureObservers()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        launchNewSearchOrQuery(intent)
    }

    private fun openNewTab(tabId: String, url: String? = null) {
        val fragment = BrowserTabFragment.newInstance(tabId, url)
        val transaction = supportFragmentManager.beginTransaction()
        if (currentTab == null) {
            transaction.replace(R.id.fragmentContainer, fragment, tabId)
        } else {
            transaction.hide(currentTab)
            transaction.add(R.id.fragmentContainer, fragment, tabId)
        }
        transaction.commit()
        currentTab = fragment
    }

    private fun selectTab(tab: TabEntity?) {

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
        if (intent == null) {
            return
        }

        if (launchNewSearch(intent)) {
            viewModel.onNewTabRequested()
            return
        }

        val sharedText = intent.intentText
        if (sharedText != null) {
            viewModel.onOpenInNewTabRequested(sharedText)
        }
    }

    private fun configureObservers() {
        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
        viewModel.selectedTab.observe(this, Observer {
            selectTab(it)
        })
        viewModel.tabs.observe(this, Observer {
            clearStaleTabs(it)
            viewModel.onTabsUpdated(it)
        })
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
        FireDialog(context = this,
            clearStarted = { viewModel.onClearRequested() },
            clearComplete = { viewModel.onClearComplete() }
        ).show()
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

    fun launchFeedback() {
        startActivity(FeedbackActivity.intent(this))
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

        fun intent(context: Context, queryExtra: String? = null, newSearch: Boolean = false): Intent {
            val intent = Intent(context, BrowserActivity::class.java)
            intent.putExtra(EXTRA_TEXT, queryExtra)
            intent.putExtra(NEW_SEARCH_EXTRA, newSearch)
            return intent
        }

        private const val NEW_SEARCH_EXTRA = "NEW_SEARCH_EXTRA"
        private const val DASHBOARD_REQUEST_CODE = 100
    }
}