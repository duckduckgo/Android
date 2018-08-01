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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.ClearPersonalDataAction
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.Close
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.DisplayMessage
import kotlinx.android.synthetic.main.content_tab_switcher.*
import kotlinx.android.synthetic.main.include_toolbar.*
import org.jetbrains.anko.longToast
import javax.inject.Inject

class TabSwitcherActivity : DuckDuckGoActivity(), TabSwitcherAdapter.TabSwitchedListener {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var clearPersonalDataAction: ClearPersonalDataAction

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var webDataManager: WebDataManager

    private val viewModel: TabSwitcherViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(TabSwitcherViewModel::class.java)
    }
    private val tabsAdapter = TabSwitcherAdapter(this, this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tab_switcher)
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
            render(it!!)
        })
        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    private fun render(tabs: List<TabEntity>) {
        tabsAdapter.updateData(tabs)
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
            R.id.newTab -> onNewTabRequested()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onFire() {
        val dialog = FireDialog(context = this, pixel = pixel, clearPersonalDataAction = clearPersonalDataAction)
        dialog.clearStarted = { viewModel.onClearRequested() }
        dialog.clearComplete = { viewModel.onClearComplete() }
        dialog.show()
    }

    override fun onNewTabRequested() {
        viewModel.onNewTabRequested()
    }

    override fun onTabSelected(tab: TabEntity) {
        viewModel.onTabSelected(tab)
    }

    override fun onTabDeleted(tab: TabEntity) {
        viewModel.onTabDeleted(tab)
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
            return Intent(context, TabSwitcherActivity::class.java)
        }
    }
}
