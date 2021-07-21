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

import android.os.Bundle
import android.view.View
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.ui.SavedSiteDialogFragment
import com.duckduckgo.app.browser.R

class EditBookmarkFolderDialogFragment : SavedSiteDialogFragment() {

    interface EditBookmarkFolderListener {
        fun onBookmarkFolderUpdated(bookmarkFolder: BookmarkFolder)
    }

    var listener: EditBookmarkFolderListener? = null

    override fun configureUI() {
        setToolbarTitle(getString(R.string.editFolder))
        arguments?.getSerializable(BookmarkFoldersActivity.KEY_CURRENT_FOLDER)?.let {
            binding.titleInput.setText((it as BookmarkFolder).name)
        }
        configureFieldVisibility()
    }

    private fun configureFieldVisibility() {
        binding.savedSiteLocationContainer.visibility = View.VISIBLE
        binding.savedSiteUrlInputContainer.visibility = View.GONE
    }

    override fun onBackNavigation() {
        arguments?.getLong(KEY_PARENT_FOLDER_ID)?.let {
            val name = binding.titleInput.text.toString()
            if (name.isNotBlank()) {
                val bookmarkFolder = arguments?.getSerializable(BookmarkFoldersActivity.KEY_CURRENT_FOLDER) as BookmarkFolder
                listener?.onBookmarkFolderUpdated(bookmarkFolder.copy(name = name, parentId = it))
            }
        }
    }

    companion object {
        const val KEY_PARENT_FOLDER_ID = "KEY_PARENT_FOLDER_ID"
        const val KEY_PARENT_FOLDER_NAME = "KEY_PARENT_FOLDER_NAME"

        fun instance(parentFolderId: Long, parentFolderName: String, bookmarkFolder: BookmarkFolder): EditBookmarkFolderDialogFragment {
            val dialogFragment = EditBookmarkFolderDialogFragment()
            val bundle = Bundle()
            bundle.putLong(KEY_PARENT_FOLDER_ID, parentFolderId)
            bundle.putString(KEY_PARENT_FOLDER_NAME, parentFolderName)
            bundle.putSerializable(BookmarkFoldersActivity.KEY_CURRENT_FOLDER, bookmarkFolder)
            dialogFragment.arguments = bundle
            return dialogFragment
        }
    }
}
