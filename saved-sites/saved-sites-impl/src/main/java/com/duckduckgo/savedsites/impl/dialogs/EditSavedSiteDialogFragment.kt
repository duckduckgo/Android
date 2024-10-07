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

package com.duckduckgo.savedsites.impl.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.view.View
import com.duckduckgo.common.ui.view.listitem.DaxListItem.ImageBackground.Circular
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.ui.view.text.DaxTextInput
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.duckduckgo.saved.sites.impl.R
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
            updateFavorite: Boolean,
        )
        fun onFavoriteAdded()
        fun onFavoriteRemoved()
    }

    interface DeleteBookmarkListener {
        fun onSavedSiteDeleted(
            savedSite: SavedSite,
        )

        fun onSavedSiteDeleteCancelled()

        fun onSavedSiteDeleteRequested()
    }

    var listener: EditSavedSiteListener? = null
    var deleteBookmarkListener: DeleteBookmarkListener? = null
    private var isFavorite = false

    override fun configureUI() {
        validateBundleArguments()
        val savedSite = getSavedSite()

        if (savedSite is Favorite) {
            setToolbarTitle(getString(R.string.favoriteDialogTitleEdit))
        } else {
            setToolbarTitle(getString(R.string.bookmarkDialogTitleEdit))
            binding.savedSiteLocationContainer.visibility = View.VISIBLE
            binding.addToFavoritesBottomDivider.visibility = View.VISIBLE

            binding.addToFavoritesPrimaryItem.setLeadingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_favorite_24)
            binding.addToFavoritesPrimaryItem.setLeadingIconBackgroundType(Circular)

            isFavorite = (savedSite as Bookmark).isFavorite

            binding.addToFavoritesSwitch.quietlySetIsChecked(
                isFavorite,
            ) { _, isChecked ->
                isFavorite = isChecked
                favoriteChanged = savedSite.isFavorite != isFavorite
                setConfirmationVisibility()
                if (isFavorite) {
                    listener?.onFavoriteAdded()
                } else {
                    listener?.onFavoriteRemoved()
                }
            }
            binding.addToFavoritesPrimaryItem.setClickListener {
                isFavorite = !isFavorite
                binding.addToFavoritesSwitch.isChecked = isFavorite
                favoriteChanged = savedSite.isFavorite != isFavorite
                setConfirmationVisibility()
            }
            binding.addToFavoritesItem.visibility = View.VISIBLE
        }
        configureMenuItems()

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
                val updatedBookmark = savedSite.copy(
                    title = updatedTitle,
                    url = updatedUrl,
                    parentId = parentId,
                    isFavorite = if (favoriteChanged) !savedSite.isFavorite else savedSite.isFavorite,
                )
                listener?.onBookmarkEdited(updatedBookmark, savedSite.parentId, favoriteChanged)
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

    private fun configureMenuItems() {
        val toolbar = binding.savedSiteAppBar.toolbar
        toolbar.menu.findItem(R.id.action_delete).isVisible = true
        toolbar.menu.findItem(R.id.action_confirm_changes).isEnabled = false
    }

    override fun deleteConfirmationTitle(): String {
        val isFavorite = (getSavedSite() as? Favorite != null)
        return if (isFavorite) getString(R.string.deleteFavoriteConfirmationDialogTitle) else getString(R.string.deleteBookmark, getExistingTitle())
    }

    override fun deleteConfirmationMessage(): Spanned? {
        val isFavorite = (getSavedSite() as? Favorite != null)
        val messageId = if (isFavorite) R.string.deleteFavoriteConfirmationDialogDescription else R.string.deleteBookmarkConfirmationDescription
        return getString(messageId, getExistingTitle()).html(requireContext())
    }

    override fun onDeleteConfirmed() {
        deleteBookmarkListener?.onSavedSiteDeleted(getSavedSite())
        dismiss()
    }

    override fun onDeleteCancelled() {
        deleteBookmarkListener?.onSavedSiteDeleteCancelled()
        dismiss()
    }

    override fun onDeleteRequested() {
        deleteBookmarkListener?.onSavedSiteDeleteRequested()
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
