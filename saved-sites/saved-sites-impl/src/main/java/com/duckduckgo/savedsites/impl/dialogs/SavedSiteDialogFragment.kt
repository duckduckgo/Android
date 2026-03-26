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

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.ui.view.text.DaxTextInput
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.DialogFragmentSavedSiteBinding
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.impl.folders.BookmarkFoldersActivity
import com.duckduckgo.savedsites.impl.folders.BookmarkFoldersActivity.Companion.KEY_BOOKMARK_FOLDER_ID
import com.duckduckgo.savedsites.impl.folders.BookmarkFoldersActivity.Companion.KEY_BOOKMARK_FOLDER_NAME
import com.duckduckgo.savedsites.impl.folders.BookmarkFoldersActivity.Companion.KEY_CURRENT_FOLDER
import com.duckduckgo.mobile.android.R as CommonR

abstract class SavedSiteDialogFragment : DialogFragment() {

    abstract fun onConfirmation()
    abstract fun configureUI()
    open fun deleteConfirmationTitle(): String = ""
    open fun deleteConfirmationMessage(): Spanned? = null
    open fun onDeleteConfirmed() = Unit
    open fun onDeleteCancelled() = Unit
    open fun onDeleteRequested() = Unit

    private var _binding: DialogFragmentSavedSiteBinding? = null
    protected val binding get() = _binding!!

    private var initialTitle: String? = null
    private var titleState = ValidationState.UNCHANGED

    private var initialParentFolderId: String? = null
    private var folderChanged = false
    protected var favoriteChanged = false

    var showAddFolderMenu: Boolean = false

    var launcher = registerForActivityResult(StartActivityForResult()) { result ->
        result.data?.let { data ->
            storeFolderIdFromIntent(data)
            populateFolderNameFromIntent(data)
        }
    }

    private fun populateFolderNameFromIntent(data: Intent) {
        data.getStringExtra(KEY_BOOKMARK_FOLDER_NAME)?.let { name ->
            binding.savedSiteLocation.text = name
        }
    }

    private fun storeFolderIdFromIntent(data: Intent) {
        val parentId = data.getStringExtra(KEY_BOOKMARK_FOLDER_ID)
        folderChanged = parentId != initialParentFolderId
        setConfirmationVisibility()
        arguments?.putString(KEY_BOOKMARK_FOLDER_ID, parentId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, CommonR.style.Widget_DuckDuckGo_DialogFullScreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = DialogFragmentSavedSiteBinding.inflate(inflater, container, false)
        configureClickListeners()
        arguments?.getString(KEY_BOOKMARK_FOLDER_NAME)?.let { name ->
            binding.savedSiteLocation.text = name
        }
        configureToolbar(binding.savedSiteAppBar.toolbar)
        configureUI()
        initialTitle = binding.titleInput.text.toString()
        initialParentFolderId = arguments?.getString(EditBookmarkFolderDialogFragment.KEY_PARENT_FOLDER_ID)
        addTextWatchers()
        showKeyboard(binding.titleInput)
        return binding.root
    }

    private fun addTextWatchers() {
        binding.titleInput.addTextChangedListener(titleTextWatcher)
    }

    private fun configureToolbar(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.edit_saved_site_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_confirm_changes -> {
                    onConfirmation()
                    hideKeyboard()
                    dismiss()
                    true
                }
                R.id.action_delete -> {
                    onDeleteRequested()
                    showDeleteConfirmation()
                    hideKeyboard()
                    true
                }
                else -> false
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.attributes?.windowAnimations = android.R.style.Animation_Dialog
        return dialog
    }

    override fun onPause() {
        super.onPause()
        dialog?.window?.setWindowAnimations(android.R.style.Animation)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun configureUpNavigation(toolbar: Toolbar) {
        toolbar.setNavigationIcon(CommonR.drawable.ic_arrow_left_24)
        toolbar.setNavigationOnClickListener {
            dismiss()
        }
    }

    private fun configureClickListeners() {
        binding.savedSiteLocation.setOnClickListener {
            context?.let { context ->
                arguments?.getString(KEY_BOOKMARK_FOLDER_ID)?.let {
                    if (arguments?.getSerializable(KEY_CURRENT_FOLDER) != null) {
                        launcher.launch(
                            BookmarkFoldersActivity.intent(
                                context,
                                it,
                                arguments?.getSerializable(KEY_CURRENT_FOLDER) as BookmarkFolder,
                                showAddFolderMenu,
                            ),
                        )
                    } else {
                        launcher.launch(BookmarkFoldersActivity.intent(context, it, showAddFolderMenu = showAddFolderMenu))
                    }
                }
            }
        }
    }

    private fun showKeyboard(inputEditText: DaxTextInput) {
        inputEditText.showKeyboard()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun hideKeyboard() {
        activity?.let {
            it.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
    }

    protected fun setToolbarTitle(title: String) {
        val toolbar = binding.savedSiteAppBar.toolbar
        toolbar.title = title
        configureUpNavigation(toolbar)
    }

    protected fun setConfirmationVisibility(inputState: ValidationState = ValidationState.UNCHANGED) {
        binding.savedSiteAppBar.toolbar.menu.findItem(R.id.action_confirm_changes).isEnabled =
            (inputState == ValidationState.CHANGED || titleState == ValidationState.CHANGED || folderChanged || favoriteChanged) &&
            (inputState != ValidationState.INVALID && titleState != ValidationState.INVALID)
    }

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

    private fun showDeleteConfirmation() {
        val title = deleteConfirmationTitle()
        val message = deleteConfirmationMessage() ?: ""

        TextAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.deleteSavedSiteConfirmationDialogDelete, DESTRUCTIVE)
            .setNegativeButton(R.string.deleteSavedSiteConfirmationDialogCancel, GHOST_ALT)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onDeleteConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        onDeleteCancelled()
                    }
                },
            )
            .show()
    }
}

enum class ValidationState {
    CHANGED,
    UNCHANGED,
    INVALID,
}
