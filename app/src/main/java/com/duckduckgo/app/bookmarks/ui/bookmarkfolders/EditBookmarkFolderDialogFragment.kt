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
import com.duckduckgo.app.bookmarks.ui.SavedSiteDialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.savedsites.api.models.BookmarkFolder

class EditBookmarkFolderDialogFragment : SavedSiteDialogFragment() {

    interface EditBookmarkFolderListener {
        fun onBookmarkFolderUpdated(bookmarkFolder: BookmarkFolder)

        fun onDeleteBookmarkFolderRequestedFromEdit(bookmarkFolder: BookmarkFolder)
    }

    var listener: EditBookmarkFolderListener? = null

    override fun configureUI() {
        setToolbarTitle(getString(R.string.editFolder))
        showAddFolderMenu = true
        getBookmarkFolder()?.let {
            binding.titleInput.text = it.name
        }
        configureFieldVisibility()
    }

    private fun configureFieldVisibility() {
        binding.savedSiteLocationContainer.visibility = View.VISIBLE
        binding.urlInput.visibility = View.GONE
        val toolbar = binding.savedSiteAppBar.toolbar
        toolbar.menu.findItem(R.id.action_delete).isVisible = true
    }

    private fun getBookmarkFolder(): BookmarkFolder? =
        requireArguments().getSerializable(BookmarkFoldersActivity.KEY_CURRENT_FOLDER) as BookmarkFolder?

    override fun onConfirmation() {
        arguments?.getString(KEY_PARENT_FOLDER_ID)?.let {
            val name = binding.titleInput.text
            if (name.isNotBlank()) {
                val bookmarkFolder = arguments?.getSerializable(BookmarkFoldersActivity.KEY_CURRENT_FOLDER) as BookmarkFolder
                listener?.onBookmarkFolderUpdated(bookmarkFolder.copy(name = name, parentId = it))
            }
        }
    }

    override fun deleteConfirmationTitle() = getString(R.string.deleteFolder, getBookmarkFolder()?.name)

    override fun deleteConfirmationMessage() = getBookmarkFolder()?.name?.let { folderName ->
        getString(R.string.deleteBookmarkFolderConfirmationDialogDescription, folderName).html(requireContext())
    }

    override fun onDeleteConfirmed() {
        getBookmarkFolder()?.let {
            listener?.onDeleteBookmarkFolderRequestedFromEdit(it)
        }
        dismiss()
    }

    companion object {
        const val KEY_PARENT_FOLDER_ID = "KEY_PARENT_FOLDER_ID"
        const val KEY_PARENT_FOLDER_NAME = "KEY_PARENT_FOLDER_NAME"

        fun instance(
            parentFolderId: String,
            parentFolderName: String,
            bookmarkFolder: BookmarkFolder,
        ): EditBookmarkFolderDialogFragment {
            val dialogFragment = EditBookmarkFolderDialogFragment()
            val bundle = Bundle()
            bundle.putString(KEY_PARENT_FOLDER_ID, parentFolderId)
            bundle.putString(KEY_PARENT_FOLDER_NAME, parentFolderName)
            bundle.putSerializable(BookmarkFoldersActivity.KEY_CURRENT_FOLDER, bookmarkFolder)
            dialogFragment.arguments = bundle
            return dialogFragment
        }
    }
}
