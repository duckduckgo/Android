/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.folders

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.ActivityBookmarkFoldersBinding
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.dialogs.AddBookmarkFolderDialogFragment
import timber.log.Timber

@InjectWith(ActivityScope::class)
class BookmarkFoldersActivity : DuckDuckGoActivity() {

    private lateinit var binding: ActivityBookmarkFoldersBinding
    private lateinit var adapter: BookmarkFolderStructureAdapter

    private val viewModel: BookmarkFoldersViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarkFoldersBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setupToolbar(binding.appBarLayout.toolbar)
        observeViewModel()
        setupAdapter()

        val currentFolder = intent.extras?.getSerializable(KEY_CURRENT_FOLDER)
        Timber.d("Saved sites: $currentFolder")

        viewModel.fetchBookmarkFolders(
            intent.extras?.getString(KEY_BOOKMARK_FOLDER_ID) ?: SavedSitesNames.BOOKMARKS_ROOT,
            currentFolder as BookmarkFolder?,
        )
    }

    private fun setupAdapter() {
        adapter = BookmarkFolderStructureAdapter(viewModel, resources.displayMetrics.widthPixels)
        binding.bookmarkFolderStructure.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(
            this,
        ) { viewState ->
            viewState?.let {
                adapter.submitList(it.folderStructure)
            }
        }

        viewModel.command.observe(
            this,
        ) {
            when (it) {
                is BookmarkFoldersViewModel.Command.SelectFolder -> setSelectedFolderResult(it.selectedBookmarkFolder)
                is BookmarkFoldersViewModel.Command.NewFolderCreatedUpdateTheStructure -> setNewlyCreatedSelectedFolderResult()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val addFolderFlag = intent.extras?.getBoolean(KEY_ADD_FOLDER_FLAG) ?: false
        if (addFolderFlag) menuInflater.inflate(R.menu.bookmark_folders_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_add_folder -> {
                val dialog = AddBookmarkFolderDialogFragment.instance(
                    SavedSitesNames.BOOKMARKS_ROOT,
                    getString(R.string.bookmarksActivityTitle),
                )
                dialog.show(supportFragmentManager, ADD_BOOKMARK_FOLDER_FRAGMENT_TAG)
                dialog.listener = viewModel
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setSelectedFolderResult(bookmarkFolder: BookmarkFolder?) {
        bookmarkFolder?.let {
            val result = Intent()
            result.putExtra(KEY_BOOKMARK_FOLDER_ID, it.id)
            result.putExtra(KEY_BOOKMARK_FOLDER_NAME, it.name)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    private fun setNewlyCreatedSelectedFolderResult() {
        viewModel.newFolderAdded(
            selectedFolderId = intent.extras?.getString(KEY_BOOKMARK_FOLDER_ID) ?: SavedSitesNames.BOOKMARKS_ROOT,
            currentFolder = intent.extras?.getSerializable(KEY_CURRENT_FOLDER) as BookmarkFolder?,
        )
    }

    companion object {
        const val KEY_BOOKMARK_FOLDER_ID = "KEY_PARENT_FOLDER_ID"
        const val KEY_BOOKMARK_FOLDER_NAME = "KEY_PARENT_FOLDER_NAME"
        const val KEY_CURRENT_FOLDER = "KEY_CURRENT_FOLDER"
        const val KEY_ADD_FOLDER_FLAG = "KEY_ADD_FOLDER_FLAG"

        private const val ADD_BOOKMARK_FOLDER_FRAGMENT_TAG = "ADD_BOOKMARK_FOLDER"

        fun intent(
            context: Context,
            parentFolderId: String,
            currentFolder: BookmarkFolder? = null,
            showAddFolderMenu: Boolean = false,
        ): Intent {
            val intent = Intent(context, BookmarkFoldersActivity::class.java)
            val bundle = Bundle()
            bundle.putString(KEY_BOOKMARK_FOLDER_ID, parentFolderId)
            bundle.putBoolean(KEY_ADD_FOLDER_FLAG, showAddFolderMenu)
            currentFolder?.let {
                bundle.putSerializable(KEY_CURRENT_FOLDER, it)
            }
            intent.putExtras(bundle)
            return intent
        }
    }
}
