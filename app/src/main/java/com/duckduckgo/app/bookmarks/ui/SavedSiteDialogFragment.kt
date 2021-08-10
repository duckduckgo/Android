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

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersActivity
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersActivity.Companion.KEY_BOOKMARK_FOLDER_ID
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersActivity.Companion.KEY_BOOKMARK_FOLDER_NAME
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersActivity.Companion.KEY_CURRENT_FOLDER
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.EditBookmarkFolderDialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.DialogFragmentSavedSiteBinding
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.mobile.android.ui.view.showKeyboard
import kotlinx.android.synthetic.main.include_find_in_page.*

abstract class SavedSiteDialogFragment : DialogFragment() {

    abstract fun onConfirmation()
    abstract fun configureUI()

    private var _binding: DialogFragmentSavedSiteBinding? = null
    protected val binding get() = _binding!!

    private var initialTitle: String? = null
    private var titleChanged = false
    private var initialParentFolderId: Long? = null
    private var folderChanged = false

    var launcher = registerForActivityResult(StartActivityForResult()) { result ->
        result.data?.let { data ->
            storeFolderIdFromIntent(data)
            populateFolderNameFromIntent(data)
        }
    }

    private fun populateFolderNameFromIntent(data: Intent) {
        data.getStringExtra(KEY_BOOKMARK_FOLDER_NAME)?.let { name ->
            binding.savedSiteLocation.setText(name)
        }
    }

    private fun storeFolderIdFromIntent(data: Intent) {
        val parentId = data.getLongExtra(KEY_BOOKMARK_FOLDER_ID, 0)
        folderChanged = parentId != initialParentFolderId
        setConfirmationVisibility()
        arguments?.putLong(KEY_BOOKMARK_FOLDER_ID, parentId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.SavedSiteFullScreenDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DialogFragmentSavedSiteBinding.inflate(inflater, container, false)
        configureClickListeners()
        arguments?.getString(KEY_BOOKMARK_FOLDER_NAME)?.let { name ->
            binding.savedSiteLocation.setText(name)
        }
        configureToolbar(binding.savedSiteAppBar.toolbar)
        configureUI()
        initialTitle = binding.titleInput.text.toString()
        initialParentFolderId = arguments?.getLong(EditBookmarkFolderDialogFragment.KEY_PARENT_FOLDER_ID)
        addTextWatchers()
        showKeyboard(binding.titleInput)
        return binding.root
    }

    private fun addTextWatchers() {
        binding.titleInput.addTextChangedListener(titleTextWatcher)
    }

    private fun configureToolbar(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.edit_saved_site_menu)
        toolbar.setOnMenuItemClickListener(
            Toolbar.OnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_confirm_changes -> {
                        onConfirmation()
                        hideKeyboard()
                        dismiss()
                        return@OnMenuItemClickListener true
                    }
                }
                false
            }
        )
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
        toolbar.setNavigationIcon(R.drawable.ic_back_24)
        toolbar.setNavigationOnClickListener {
            dismiss()
        }
    }

    private fun configureClickListeners() {
        binding.savedSiteLocation.setOnClickListener {
            context?.let { context ->
                arguments?.getLong(KEY_BOOKMARK_FOLDER_ID)?.let {
                    if (arguments?.getSerializable(KEY_CURRENT_FOLDER) != null) {
                        launcher.launch(BookmarkFoldersActivity.intent(context, it, arguments?.getSerializable(KEY_CURRENT_FOLDER) as BookmarkFolder))
                    } else {
                        launcher.launch(BookmarkFoldersActivity.intent(context, it))
                    }
                }
            }
        }
    }

    private fun showKeyboard(inputEditText: EditText) {
        inputEditText.setSelection(inputEditText.text.length)
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

    protected fun setConfirmationVisibility(isVisible: Boolean = false) {
        binding.savedSiteAppBar.toolbar.menu.findItem(R.id.action_confirm_changes).isVisible = isVisible || titleChanged || folderChanged
    }

    private val titleTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            titleChanged = editable.toString() != initialTitle
            setConfirmationVisibility()
        }
    }
}
