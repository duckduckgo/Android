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

package com.duckduckgo.app.bookmarks.ui.bookmarkfolders

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityBookmarkFoldersBinding
import com.duckduckgo.app.global.DuckDuckGoActivity

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

        viewModel.fetchBookmarkFolders(
            intent.extras?.getLong(KEY_BOOKMARK_FOLDER_ID) ?: 0,
            getString(R.string.bookmarksSectionTitle)
        )
    }

    private fun setupAdapter() {
        adapter = BookmarkFolderStructureAdapter(viewModel, resources.displayMetrics.widthPixels)
        binding.bookmarkFolderStructure.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(
            this,
            { viewState ->
                viewState?.let {
                    adapter.submitList(it.folderStructure)
                    setSelectedFolderResult(it.selectedBookmarkFolder)
                }
            }
        )
    }

    private fun setSelectedFolderResult(bookmarkFolder: BookmarkFolder?) {
        bookmarkFolder?.let {
            val result = Intent()
            result.putExtra(KEY_BOOKMARK_FOLDER_ID, it.id)
            result.putExtra(KEY_BOOKMARK_FOLDER_NAME, it.name)
            setResult(Activity.RESULT_OK, result)
        }
    }

    companion object {
        const val KEY_BOOKMARK_FOLDER_ID = "KEY_PARENT_FOLDER_ID"
        const val KEY_BOOKMARK_FOLDER_NAME = "KEY_PARENT_FOLDER_NAME"
        const val KEY_CURRENT_FOLDER_ID = "KEY_CURRENT_FOLDER_ID"

        fun intent(context: Context, parentFolderId: Long, currentFolderId: Long? = null): Intent {
            val intent = Intent(context, BookmarkFoldersActivity::class.java)
            val bundle = Bundle()
            bundle.putLong(KEY_BOOKMARK_FOLDER_ID, parentFolderId)
            currentFolderId?.let {
                bundle.putLong(KEY_CURRENT_FOLDER_ID, it)
            }
            intent.putExtras(bundle)
            return intent
        }
    }
}
