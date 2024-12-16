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

package com.duckduckgo.app.privacy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityAllowlistBinding
import com.duckduckgo.app.browser.databinding.DialogEditAllowlistBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.privacy.model.UserAllowListedDomain
import com.duckduckgo.app.privacy.ui.AllowListViewModel.Command.*
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder.EventListener
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class AllowListActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var faviconManager: FaviconManager

    private lateinit var adapter: WebsitesAdapter
    private val binding: ActivityAllowlistBinding by viewBinding()
    private val viewModel: AllowListViewModel by bindViewModel()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val recycler
        get() = binding.recycler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)
        setupRecycler()
        observeViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.allowlist_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add -> viewModel.onAddRequested()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecycler() {
        adapter = WebsitesAdapter(viewModel, this, faviconManager)
        recycler.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this) { viewState ->
            viewState?.let { renderViewState(it) }
        }

        viewModel.command.observe(this) {
            processCommand(it)
        }
    }

    private fun renderViewState(viewState: AllowListViewModel.ViewState) {
        adapter.entries = viewState.allowList
        if (viewState.showAllowList) {
            invalidateOptionsMenu()
        }
    }

    private fun processCommand(command: AllowListViewModel.Command) {
        when (command) {
            is ShowAdd -> showAddDialog()
            is ShowEdit -> showEditDialog(command.entry)
            is ConfirmDelete -> showDeleteDialog(command.entry)
            is ShowAllowListFormatError -> showAllowListFormatError()
        }
    }

    private fun showAddDialog() {
        val inputBinding = DialogEditAllowlistBinding.inflate(layoutInflater)
        CustomAlertDialogBuilder(this)
            .setTitle(R.string.dialogAddTitle)
            .setPositiveButton(com.duckduckgo.mobile.android.R.string.dialogSave)
            .setNegativeButton(R.string.cancel)
            .setView(inputBinding)
            .addEventListener(
                object : CustomAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        val newText = inputBinding.customDialogTextInput.text
                        viewModel.onEntryAdded(UserAllowListedDomain(newText))
                    }
                },
            )
            .show()
    }

    private fun showEditDialog(entry: UserAllowListedDomain) {
        val inputBinding = DialogEditAllowlistBinding.inflate(layoutInflater)
        inputBinding.customDialogTextInput.text = entry.domain
        CustomAlertDialogBuilder(this)
            .setTitle(R.string.dialogEditTitle)
            .setPositiveButton(com.duckduckgo.mobile.android.R.string.dialogSave)
            .setNegativeButton(R.string.cancel)
            .setView(inputBinding)
            .addEventListener(
                object : CustomAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        val newText = inputBinding.customDialogTextInput.text
                        viewModel.onEntryEdited(entry, UserAllowListedDomain(newText))
                    }
                },
            )
            .show()
    }

    private fun showDeleteDialog(entry: UserAllowListedDomain) {
        TextAlertDialogBuilder(this)
            .setTitle(com.duckduckgo.mobile.android.R.string.dialogConfirmTitle)
            .setMessage(getString(R.string.allowlistEntryDeleteConfirmMessage, entry.domain).html(this))
            .setPositiveButton(android.R.string.yes)
            .setNegativeButton(android.R.string.no)
            .addEventListener(
                object : EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onEntryDeleted(entry)
                    }
                },
            )
            .show()
    }

    private fun showAllowListFormatError() {
        Toast.makeText(this, R.string.allowlistFormatError, Toast.LENGTH_LONG).show()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AllowListActivity::class.java)
        }
    }
}
