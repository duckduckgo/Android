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

package com.duckduckgo.app.bookmarks.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.AddBookmarkFolderDialogFragment
import com.duckduckgo.app.browser.R

class EditSavedSiteDialogFragment : SavedSiteDialogFragment() {

    interface EditSavedSiteListener {
        fun onSavedSiteEdited(savedSite: SavedSite)
    }

    var listener: EditSavedSiteListener? = null

    override fun configureUI() {
        validateBundleArguments()

        if (getSavedSite() is SavedSite.Favorite) {
            setToolbarTitle(getString(R.string.favoriteDialogTitleEdit))
        } else {
            setToolbarTitle(getString(R.string.bookmarkDialogTitleEdit))
            binding.savedSiteLocationContainer.visibility = View.VISIBLE
        }

        populateFields(binding.titleInput, binding.urlInput)
    }

    private fun validateInput(newValue: String, existingValue: String) =
        if (newValue.isNotBlank()) newValue else existingValue

    private fun populateFields(titleInput: EditText, urlInput: EditText) {
        titleInput.setText(getExistingTitle())
        urlInput.setText(getExistingUrl())
    }

    override fun onBackNavigation() {
        val savedSite = getSavedSite()

        val updatedTitle = validateInput(binding.titleInput.text.toString(), savedSite.title)
        val updatedUrl = validateInput(binding.urlInput.text.toString(), savedSite.url)

        when (savedSite) {
            is SavedSite.Bookmark -> {
                val parentId = arguments?.getLong(AddBookmarkFolderDialogFragment.KEY_PARENT_FOLDER_ID) ?: 0
                listener?.onSavedSiteEdited(
                    savedSite.copy(title = updatedTitle, url = updatedUrl, parentId = parentId)
                )
            }
            is SavedSite.Favorite -> {
                listener?.onSavedSiteEdited(
                    savedSite.copy(title = updatedTitle, url = updatedUrl)
                )
            }
        }
    }

    private fun getSavedSite(): SavedSite = requireArguments().getSerializable(KEY_SAVED_SITE) as SavedSite
    private fun getExistingTitle(): String = getSavedSite().title
    private fun getExistingUrl(): String = getSavedSite().url

    private fun validateBundleArguments() {
        if (arguments == null) throw IllegalArgumentException("Missing arguments bundle")
        val args = requireArguments()
        if (!args.containsKey(KEY_SAVED_SITE)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_PREEXISTING_TITLE, KEY_PREEXISTING_URL]")
        }
    }

    companion object {
        private const val KEY_SAVED_SITE = "KEY_SAVED_SITE"

        fun instance(savedSite: SavedSite, parentFolderId: Long = 0, parentFolderName: String? = null): EditSavedSiteDialogFragment {
            val dialog = EditSavedSiteDialogFragment()
            val bundle = Bundle()
            bundle.putSerializable(KEY_SAVED_SITE, savedSite)
            bundle.putLong(AddBookmarkFolderDialogFragment.KEY_PARENT_FOLDER_ID, parentFolderId)
            bundle.putString(AddBookmarkFolderDialogFragment.KEY_PARENT_FOLDER_NAME, parentFolderName)
            dialog.arguments = bundle
            return dialog
        }
    }
}
