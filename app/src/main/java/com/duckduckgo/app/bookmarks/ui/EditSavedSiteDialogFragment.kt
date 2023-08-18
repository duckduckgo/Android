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
import android.text.Editable
import android.view.View
import android.view.WindowManager
import androidx.appcompat.widget.Toolbar
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.AddBookmarkFolderDialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.extensions.html
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.text.DaxTextInput
import com.duckduckgo.mobile.android.ui.view.text.DaxTextView
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames

class EditSavedSiteDialogFragment : SavedSiteDialogFragment() {

    interface EditSavedSiteListener {
        fun onFavouriteEdited(favorite: Favorite)
        fun onBookmarkEdited(
            bookmark: Bookmark,
            oldFolderId: String,
        )
    }

    interface DeleteBookmarkListener {
        fun onBookmarkDeleted(
            bookmark: Bookmark,
        )
    }

    var listener: EditSavedSiteListener? = null
    var deleteBookmarkListener: DeleteBookmarkListener? = null

    override fun configureUI() {
        validateBundleArguments()

        if (getSavedSite() is SavedSite.Favorite) {
            setToolbarTitle(getString(R.string.favoriteDialogTitleEdit))
        } else {
            setToolbarTitle(getString(R.string.bookmarkDialogTitleEdit))
            configureDeleteBookmark(binding.savedSiteAppBar.toolbar)
            binding.savedSiteLocationContainer.visibility = View.VISIBLE
        }
        showAddFolderMenu = true

        populateFields(binding.titleInput, binding.urlInput, binding.savedSiteLocation)

        binding.urlInput.addTextChangedListener(urlTextWatcher)
    }

    private fun validateInput(
        newValue: String,
        existingValue: String,
    ) =
        if (newValue.isNotBlank()) newValue else existingValue

    private fun populateFields(
        titleInput: DaxTextInput,
        urlInput: DaxTextInput,
        savedLocation: DaxTextView,
    ) {
        titleInput.text = getExistingTitle()
        urlInput.text = getExistingUrl()
        getExistingBookmarkFolderName()?.let {
            if (it.isNotEmpty()) savedLocation.text = it
        }
    }

    override fun onConfirmation() {
        val savedSite = getSavedSite()

        val updatedTitle = validateInput(binding.titleInput.text, savedSite.title)
        val updatedUrl = validateInput(binding.urlInput.text, savedSite.url)

        when (savedSite) {
            is Bookmark -> {
                val parentId = arguments?.getString(AddBookmarkFolderDialogFragment.KEY_PARENT_FOLDER_ID) ?: SavedSitesNames.BOOKMARKS_ROOT
                val updatedBookmark = savedSite.copy(title = updatedTitle, url = updatedUrl, parentId = parentId)
                listener?.onBookmarkEdited(updatedBookmark, savedSite.parentId)
            }

            is Favorite -> {
                listener?.onFavouriteEdited(
                    savedSite.copy(title = updatedTitle, url = updatedUrl),
                )
            }
        }
    }

    private val urlTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            when {
                editable.toString().isBlank() -> {
                    setConfirmationVisibility(ValidationState.INVALID)
                }

                editable.toString() != getSavedSite().url -> {
                    setConfirmationVisibility(ValidationState.CHANGED)
                }

                else -> {
                    setConfirmationVisibility(ValidationState.UNCHANGED)
                }
            }
        }
    }

    private fun getSavedSite(): SavedSite = requireArguments().getSerializable(KEY_SAVED_SITE) as SavedSite
    private fun getExistingTitle(): String = getSavedSite().title
    private fun getExistingUrl(): String = getSavedSite().url
    private fun getExistingBookmarkFolderName(): String? =
        requireArguments().getSerializable(AddBookmarkFolderDialogFragment.KEY_PARENT_FOLDER_NAME) as String?

    private fun validateBundleArguments() {
        if (arguments == null) throw IllegalArgumentException("Missing arguments bundle")
        val args = requireArguments()
        if (!args.containsKey(KEY_SAVED_SITE)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_PREEXISTING_TITLE, KEY_PREEXISTING_URL]")
        }
    }

    private fun configureDeleteBookmark(toolbar: Toolbar) {
        binding.savedSiteAppBar.toolbar.menu.findItem(R.id.action_delete_saved_site).isVisible = true
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete_saved_site -> {
                    showDeleteBookmarkConfirmation(getExistingTitle())
                    hideKeyboard()
                    return@setOnMenuItemClickListener true
                }
            }
            false
        }
    }

    private fun showDeleteBookmarkConfirmation(title: String) {
        TextAlertDialogBuilder(requireContext())
            .setTitle(R.string.deleteBookmarkConfirmationDialogTitle)
            .setMessage(getString(R.string.deleteBookmarkConfirmationDialogDescription, title).html(requireContext()))
            .setDestructiveButtons(true)
            .setPositiveButton(R.string.deleteBookmarkConfirmationDialogDelete)
            .setNegativeButton(R.string.deleteBookmarkConfirmationDialogCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onDeleteBookmarkConfirmed()
                    }
                },
            )
            .show()
    }

    private fun onDeleteBookmarkConfirmed() {
        deleteBookmarkListener?.onBookmarkDeleted(getSavedSite() as Bookmark)
        dismiss()
    }

    private fun hideKeyboard() {
        activity?.let {
            it.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
    }

    companion object {
        const val KEY_SAVED_SITE = "KEY_SAVED_SITE"

        fun instance(
            savedSite: SavedSite,
            parentFolderId: String = SavedSitesNames.BOOKMARKS_ROOT,
            parentFolderName: String? = null,
        ): EditSavedSiteDialogFragment {
            val dialog = EditSavedSiteDialogFragment()
            val bundle = Bundle()
            bundle.putSerializable(KEY_SAVED_SITE, savedSite)
            bundle.putString(AddBookmarkFolderDialogFragment.KEY_PARENT_FOLDER_ID, parentFolderId)
            bundle.putString(AddBookmarkFolderDialogFragment.KEY_PARENT_FOLDER_NAME, parentFolderName)
            dialog.arguments = bundle
            return dialog
        }
    }
}
