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
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityWhitelistBinding
import com.duckduckgo.app.browser.databinding.EditWhitelistBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.privacy.model.UserWhitelistedDomain
import com.duckduckgo.app.privacy.ui.WhitelistViewModel.Command.*
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import javax.inject.Inject

class WhitelistActivity : DuckDuckGoActivity() {

    @Inject lateinit var faviconManager: FaviconManager

    private lateinit var adapter: WebsitesAdapter

    private val binding: ActivityWhitelistBinding by viewBinding()

    private var dialog: AlertDialog? = null
    private val viewModel: WhitelistViewModel by bindViewModel()

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.whitelist_activity_menu, menu)
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
        viewModel.viewState.observe(
            this, Observer { viewState -> viewState?.let { renderViewState(it) } })

        viewModel.command.observe(this, Observer { processCommand(it) })
    }

    private fun renderViewState(viewState: WhitelistViewModel.ViewState) {
        adapter.entries = viewState.whitelist
        if (viewState.showWhitelist) {
            invalidateOptionsMenu()
        }
    }

    private fun processCommand(command: WhitelistViewModel.Command?) {
        when (command) {
            is ShowAdd -> showAddDialog()
            is ShowEdit -> showEditDialog(command.entry)
            is ConfirmDelete -> showDeleteDialog(command.entry)
            is ShowWhitelistFormatError -> showWhitelistFormatError()
        }
    }

    private fun showAddDialog() {
        val dialogBinding = EditWhitelistBinding.inflate(layoutInflater)
        val addDialog =
            AlertDialog.Builder(this)
                .apply {
                    setTitle(R.string.dialogAddTitle)
                    setView(dialogBinding.root)
                    setPositiveButton(R.string.dialogSaveAction) { _, _ ->
                        val newText = dialogBinding.textInput.text.toString()
                        viewModel.onEntryAdded(UserWhitelistedDomain(newText))
                    }
                    setNegativeButton(android.R.string.no) { _, _ -> }
                }
                .create()

        dialog?.dismiss()
        dialog = addDialog
        addDialog.show()
    }

    private fun showEditDialog(entry: UserWhitelistedDomain) {
        val dialogBinding = EditWhitelistBinding.inflate(layoutInflater)
        val editDialog =
            AlertDialog.Builder(this)
                .apply {
                    setTitle(R.string.dialogEditTitle)
                    setView(dialogBinding.root)
                    setPositiveButton(R.string.dialogSaveAction) { _, _ ->
                        val newText = dialogBinding.textInput.text.toString()
                        viewModel.onEntryEdited(entry, UserWhitelistedDomain(newText))
                    }
                    setNegativeButton(android.R.string.no) { _, _ -> }
                }
                .create()

        dialog?.dismiss()
        dialog = editDialog
        editDialog.show()

        dialogBinding.textInput.setText(entry.domain)
        dialogBinding.textInput.setSelection(entry.domain.length)
    }

    private fun showDeleteDialog(entry: UserWhitelistedDomain) {
        val deleteDialog =
            AlertDialog.Builder(this)
                .apply {
                    setTitle(R.string.dialogConfirmTitle)
                    setMessage(
                        getString(R.string.whitelistEntryDeleteConfirmMessage, entry.domain)
                            .html(this.context))
                    setPositiveButton(android.R.string.yes) { _, _ ->
                        viewModel.onEntryDeleted(entry)
                    }
                    setNegativeButton(android.R.string.no) { _, _ -> }
                }
                .create()

        dialog?.dismiss()
        dialog = deleteDialog
        deleteDialog.show()
    }

    private fun showWhitelistFormatError() {
        Toast.makeText(this, R.string.whitelistFormatError, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, WhitelistActivity::class.java)
        }
    }
}
