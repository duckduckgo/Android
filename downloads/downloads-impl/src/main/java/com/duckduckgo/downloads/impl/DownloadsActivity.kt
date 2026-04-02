/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.downloads.impl

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.SearchBar
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.DownloadsScreens.DownloadsScreenNoParams
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.impl.databinding.ActivityDownloadsBinding
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DownloadsScreenNoParams::class)
class DownloadsActivity : DuckDuckGoActivity() {

    private val viewModel: DownloadsViewModel by bindViewModel()
    private val binding: ActivityDownloadsBinding by viewBinding()

    @Inject
    lateinit var downloadsAdapter: DownloadsAdapter

    @Inject
    lateinit var downloadsFileActions: DownloadsFileActions

    private val toolbar
        get() = binding.toolbar

    private val searchBar
        get() = binding.searchBar

    private var searchMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)
        setupRecyclerView()

        lifecycleScope.launch {
            viewModel.viewState
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { render(it) }
        }

        lifecycleScope.launch {
            viewModel.commands()
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { processCommands(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncDownloads()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.downloads_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.downloads_delete_all -> viewModel.deleteAllDownloadedItems()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        searchMenuItem = menu.findItem(R.id.action_search)
        initializeSearchBar()
        return super.onPrepareOptionsMenu(menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (searchBar.isVisible) {
            hideSearchBar()
        } else {
            super.onBackPressed()
        }
    }

    private fun processCommands(command: DownloadsViewModel.Command) {
        when (command) {
            is DownloadsViewModel.Command.OpenFile -> showOpen(command)
            is DownloadsViewModel.Command.ShareFile -> showShare(command)
            is DownloadsViewModel.Command.DisplayMessage -> showSnackbar(command.messageId, command.arg)
            is DownloadsViewModel.Command.DisplayUndoMessage -> showUndo(command)
            is DownloadsViewModel.Command.CancelDownload -> cancelDownload(command)
        }
    }

    private fun showOpen(command: DownloadsViewModel.Command.OpenFile) {
        val file = File(command.item.filePath)
        if (file.exists()) {
            val result = downloadsFileActions.openFile(this@DownloadsActivity, file)
            if (!result) {
                showSnackbar(R.string.downloadsCannotOpenFileErrorMessage)
            }
        } else {
            viewModel.delete(command.item)
        }
    }

    private fun showShare(command: DownloadsViewModel.Command.ShareFile) {
        downloadsFileActions.shareFile(this@DownloadsActivity, File(command.item.filePath))
    }

    private fun showUndo(command: DownloadsViewModel.Command.DisplayUndoMessage) {
        Snackbar.make(
            binding.root,
            getString(command.messageId, command.arg),
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.downloadsUndoActionName) {
            // noop, handled in onDismissed callback
        }.addCallback(
            object : Snackbar.Callback() {
                override fun onDismissed(
                    transientBottomBar: Snackbar?,
                    event: Int,
                ) {
                    when (event) {
                        // handle the UNDO action here as we only have one
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_ACTION -> viewModel.insert(command.items)
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_SWIPE,
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_MANUAL,
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_TIMEOUT,
                        -> handleDeleteAll(command.items)
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_CONSECUTIVE -> { /* noop */ }
                    }
                }
            },
        ).show()
    }

    private fun handleDeleteAll(items: List<DownloadItem>) {
        viewModel.removeFromDiskAndFromDownloadManager(items)
    }

    private fun cancelDownload(command: DownloadsViewModel.Command.CancelDownload) {
        viewModel.removeFromDownloadManager(command.item.downloadId)
    }

    private fun showSnackbar(@StringRes messageId: Int, arg: String = "") {
        Snackbar.make(binding.root, getString(messageId, arg), Snackbar.LENGTH_LONG).show()
    }

    private fun render(viewState: DownloadsViewModel.ViewState) {
        downloadsAdapter.updateData(viewState.filteredItems)
        searchMenuItem?.isVisible = itemsAvailable(viewState)
    }

    private fun itemsAvailable(viewState: DownloadsViewModel.ViewState): Boolean {
        // The empty view is part of the list.
        return viewState.filteredItems.size > 1
    }

    private fun setupRecyclerView() {
        downloadsAdapter.setListener(viewModel)
        binding.downloadsContentView.layoutManager = LinearLayoutManager(this)
        binding.downloadsContentView.adapter = downloadsAdapter
    }

    private fun initializeSearchBar() {
        searchMenuItem?.setOnMenuItemClickListener {
            showSearchBar()
            return@setOnMenuItemClickListener true
        }

        searchBar.onAction {
            when (it) {
                is SearchBar.Action.PerformUpAction -> hideSearchBar()
                is SearchBar.Action.PerformSearch -> viewModel.onQueryTextChange(it.searchText)
            }
        }
    }

    private fun showSearchBar() {
        toolbar.gone()
        searchBar.handle(SearchBar.Event.ShowSearchBar)
        searchBar.showKeyboard()
    }

    private fun hideSearchBar() {
        toolbar.show()
        searchBar.handle(SearchBar.Event.DismissSearchBar)
        searchBar.hideKeyboard()
    }
}
