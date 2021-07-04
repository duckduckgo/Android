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
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.DialogFragmentSavedSiteBinding
import com.duckduckgo.app.global.view.showKeyboard

abstract class SavedSiteDialogFragment : DialogFragment() {

    abstract fun onBackNavigation()
    abstract fun configureUI()

    private var _binding: DialogFragmentSavedSiteBinding? = null
    protected val binding get() = _binding!!

    var launcher = registerForActivityResult(StartActivityForResult()) {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.SavedSiteFullScreenDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DialogFragmentSavedSiteBinding.inflate(inflater, container, false)
        configureClickListeners()
        configureUI()
        showKeyboard(binding.titleInput)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.attributes?.windowAnimations = android.R.style.Animation_Dialog
        return dialog
    }

    override fun onCancel(dialogInterface: DialogInterface) {
        onBackNavigation()
        hideKeyboard()
        super.onCancel(dialogInterface)
    }

    override fun onDismiss(dialogInterface: DialogInterface) {
        onBackNavigation()
        hideKeyboard()
        super.onDismiss(dialogInterface)
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
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener {
            dismiss()
        }
    }

    private fun configureClickListeners() {
        binding.savedSiteLocation.setOnClickListener {
            context?.let {
                launcher.launch(BookmarkFoldersActivity.intent(it))
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
}
