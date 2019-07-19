/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui.old

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.ClearPersonalDataAction
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherListener
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.Close
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.DisplayMessage
import kotlinx.android.synthetic.main.content_tab_switcher.*
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.anko.longToast
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class TabSwitcherActivityLegacy : DuckDuckGoActivity(), TabSwitcherListener, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + Dispatchers.Main

    @Inject
    lateinit var clearPersonalDataAction: ClearPersonalDataAction

    @Inject
    lateinit var variantManager: VariantManager

    private val viewModel: TabSwitcherViewModel by bindViewModel()

    private val tabsAdapter = TabSwitcherAdapterLegacy(this, this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tab_switcher_legacy)
        configureToolbar()
        configureRecycler()
        configureObservers()
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun configureRecycler() {
        tabsRecycler.layoutManager = LinearLayoutManager(this)
        tabsRecycler.adapter = tabsAdapter
    }

    private fun configureObservers() {
        viewModel.tabs.observe(this, Observer<List<TabEntity>> {
            render()
        })
        viewModel.selectedTab.observe(this, Observer<TabEntity> {
            render()
        })
        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    private fun render() {
        tabsAdapter.updateData(viewModel.tabs.value, viewModel.selectedTab.value)
    }

    private fun processCommand(command: Command?) {
        when (command) {
            is DisplayMessage -> applicationContext?.longToast(command.messageId)
            is Close -> finish()
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
        val dialog = FireDialog(context = this, clearPersonalDataAction = clearPersonalDataAction)
        dialog.clearComplete = { viewModel.onClearComplete() }
        dialog.show()
    }

    override fun onNewTabRequested() {
        launch { viewModel.onNewTabRequested() }
    }

    override fun onTabSelected(tab: TabEntity) {
        launch { viewModel.onTabSelected(tab) }
    }

    override fun onTabDeleted(tab: TabEntity) {
        launch { viewModel.onTabDeleted(tab) }
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
    }

    private fun clearObserversEarlyToStopViewUpdates() {
        viewModel.tabs.removeObservers(this)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, TabSwitcherActivityLegacy::class.java)
        }
    }
}
