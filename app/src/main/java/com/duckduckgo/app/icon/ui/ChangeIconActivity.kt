/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.icon.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import kotlinx.android.synthetic.main.content_app_icons.appIconsList
import kotlinx.android.synthetic.main.include_toolbar.toolbar

class ChangeIconActivity : DuckDuckGoActivity() {

    private val viewModel: ChangeIconViewModel by bindViewModel()
    private val iconsAdapter: AppIconsAdapter = AppIconsAdapter { icon ->
        viewModel.onIconSelected(this, icon)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, ChangeIconActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_icons)
        setupActionBar()
        configureRecycler()

        observeViewModel()
    }

    private fun setupActionBar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun configureRecycler() {
        appIconsList.layoutManager = GridLayoutManager(this, 4)
        appIconsList.addItemDecoration(ItemOffsetDecoration(this, R.dimen.changeAppIconListPadding))
        appIconsList.adapter = iconsAdapter
    }

    private fun observeViewModel() {

        viewModel.viewState.observe(this, Observer<ChangeIconViewModel.ViewState> { viewState ->
            viewState?.let {
                render(it)
            }
        })

        viewModel.command.observe(this, Observer {
            processCommand(it)
        })

        viewModel.start()
    }

    private fun render(viewState: ChangeIconViewModel.ViewState) {
        iconsAdapter.notifyChanges(viewState.appIcons)
    }

    private fun processCommand(it: ChangeIconViewModel.Command?) {
        when (it) {
            is ChangeIconViewModel.Command.IconChanged -> {
                finish()
            }
        }
    }
}


