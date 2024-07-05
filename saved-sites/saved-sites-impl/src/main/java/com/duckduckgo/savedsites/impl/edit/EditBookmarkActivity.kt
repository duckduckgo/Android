/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.edit

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.ui.view.text.TextChangedWatcher
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.ActivityEditBookmarkBinding
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.edit.EditBookmarkScreens.EditBookmarkScreen
import com.duckduckgo.savedsites.impl.edit.EditBookmarkViewModel.ValidationState
import com.duckduckgo.savedsites.impl.edit.EditBookmarkViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(EditBookmarkScreen::class)
class EditBookmarkActivity : DuckDuckGoActivity() {

    private val binding: ActivityEditBookmarkBinding by viewBinding()
    private val viewModel: EditBookmarkViewModel by bindViewModel()

    private var titleState = ValidationState.UNCHANGED
    private var folderChanged = false
    private var favoriteChanged = false

    private var bookmark: Bookmark? = null
    private var initialTitle: String? = null
    private var isFavorite = false
    private var parentId = SavedSitesNames.BOOKMARKS_ROOT
    private var parentName = SavedSitesNames.BOOKMARKS_NAME

    private fun bookmarkId(): String? = intent.getActivityParams(EditBookmarkScreen::class.java)?.bookmarkId

    private val titleTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            titleState = when {
                editable.toString().isBlank() -> {
                    ValidationState.INVALID
                }

                editable.toString() != initialTitle -> {
                    ValidationState.CHANGED
                }

                else -> {
                    ValidationState.UNCHANGED
                }
            }
            setConfirmationVisibility()
        }
    }

    private val urlTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            when {
                editable.toString().isBlank() -> {
                    setConfirmationVisibility(ValidationState.INVALID)
                }

                editable.toString() != bookmark?.url -> {
                    setConfirmationVisibility(ValidationState.CHANGED)
                }

                else -> {
                    setConfirmationVisibility(ValidationState.UNCHANGED)
                }
            }
        }
    }

    var launcher = registerForActivityResult(StartActivityForResult()) { result ->
        result.data?.let { data ->
            storeFolderIdFromIntent(data)
            populateFolderNameFromIntent(data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        configureUI()

        observeViewModel()
        loadBookmark()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)
    }

    private fun loadBookmark() {
        bookmarkId()?.let { viewModel.loadBookmark(it) }
    }

    private fun configureUI() {
        configureToolbar(binding.savedSiteAppBar.toolbar)
        configureClickListeners()
        addTextWatchers()
    }

    private fun configureToolbar(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.edit_saved_site_menu)
        toolbar.menu.findItem(R.id.action_delete).isVisible = true
        toolbar.menu.findItem(R.id.action_confirm_changes).isEnabled = false
        toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun addTextWatchers() {
        binding.titleInput.addTextChangedListener(titleTextWatcher)
        binding.urlInput.addTextChangedListener(urlTextWatcher)
    }

    private fun configureClickListeners() {
        binding.savedSiteLocation.setOnClickListener {
            // context?.let { context ->
            //     arguments?.getString(KEY_BOOKMARK_FOLDER_ID)?.let {
            //         if (arguments?.getSerializable(KEY_CURRENT_FOLDER) != null) {
            //             launcher.launch(
            //                 BookmarkFoldersActivity.intent(
            //                     context,
            //                     it,
            //                     arguments?.getSerializable(KEY_CURRENT_FOLDER) as BookmarkFolder,
            //                     true,
            //                 ),
            //             )
            //         } else {
            //             launcher.launch(BookmarkFoldersActivity.intent(context, it, showAddFolderMenu = true))
            //         }
            //     }
            // }
        }
    }

    private fun renderViewState(viewState: ViewState) {
        if (viewState.savedSite != null && viewState.bookmarkFolder != null) {
            bookmark = viewState.savedSite
            initialTitle = viewState.savedSite.title
            parentId = viewState.bookmarkFolder.id
            parentName = viewState.bookmarkFolder.name
            isFavorite = viewState.savedSite.isFavorite

            binding.savedSiteAppBar.toolbar.title = title
            binding.titleInput.text = viewState.savedSite.title
            binding.urlInput.text = viewState.savedSite.url
            binding.savedSiteLocation.text = viewState.bookmarkFolder.name

            binding.addToFavoritesSwitch.quietlySetIsChecked(
                viewState.savedSite.isFavorite,
            ) { _, isChecked ->
                isFavorite = isChecked
                favoriteChanged = viewState.savedSite.isFavorite != isFavorite
                setConfirmationVisibility()
            }
            binding.addToFavoritesPrimaryItem.setClickListener {
                isFavorite = !isFavorite
                binding.addToFavoritesSwitch.isChecked = isFavorite
                favoriteChanged = viewState.savedSite.isFavorite != isFavorite
                setConfirmationVisibility()
            }

            binding.savedSiteAppBar.toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_confirm_changes -> {
                        val updatedTitle = validateInput(binding.titleInput.text, viewState.savedSite.title)
                        val updatedUrl = validateInput(binding.urlInput.text, viewState.savedSite.url)

                        val updatedBookmark = viewState.savedSite.copy(
                            title = updatedTitle,
                            url = updatedUrl,
                            parentId = parentId,
                            isFavorite = if (favoriteChanged) !viewState.savedSite.isFavorite else viewState.savedSite.isFavorite,
                        )

                        viewModel.confirmChanges(updatedBookmark, viewState.savedSite.parentId, favoriteChanged)
                        finish()
                        true
                    }

                    R.id.action_delete -> {
                        showDeleteConfirmation(viewState.savedSite)
                        true
                    }

                    else -> false
                }
            }
        }
    }

    private fun showDeleteConfirmation(savedSite: Bookmark) {
        TextAlertDialogBuilder(this)
            .setTitle(getString(R.string.deleteBookmark, title))
            .setMessage(R.string.deleteBookmarkConfirmationDescription)
            .setDestructiveButtons(true)
            .setPositiveButton(R.string.deleteSavedSiteConfirmationDialogDelete)
            .setNegativeButton(R.string.deleteSavedSiteConfirmationDialogCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.delete(savedSite)
                        finish()
                    }
                },
            )
            .show()
    }

    private fun validateInput(
        newValue: String,
        existingValue: String,
    ) =
        if (newValue.isNotBlank()) newValue else existingValue

    private fun setConfirmationVisibility(inputState: ValidationState = ValidationState.UNCHANGED) {
        binding.savedSiteAppBar.toolbar.menu.findItem(R.id.action_confirm_changes).isEnabled =
            (inputState == ValidationState.CHANGED || titleState == ValidationState.CHANGED || folderChanged || favoriteChanged) &&
            (inputState != ValidationState.INVALID && titleState != ValidationState.INVALID)
    }

    private fun storeFolderIdFromIntent(data: Intent) {
        // val parentId = data.getStringExtra(KEY_BOOKMARK_FOLDER_ID)
        // folderChanged = parentFolderId() != initialParentFolderId
        // setConfirmationVisibility()
    }

    private fun populateFolderNameFromIntent(data: Intent) {
        // data.getStringExtra(KEY_BOOKMARK_FOLDER_NAME)?.let { name ->
        //     binding.savedSiteLocation.text = name
        // }
    }
}
