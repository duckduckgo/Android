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
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.EditSavedSiteBinding
import com.duckduckgo.app.global.view.showKeyboard

class EditSavedSiteDialogFragment : DialogFragment() {

    interface EditSavedSiteListener {
        fun onSavedSiteEdited(savedSite: SavedSite)
    }

    var listener: EditSavedSiteListener? = null

    private var _binding: EditSavedSiteBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.SavedSiteFullScreenDialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.attributes?.windowAnimations = android.R.style.Animation_Dialog
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = EditSavedSiteBinding.inflate(inflater, container, false)

        validateBundleArguments()
        configureToolbar()
        populateFields(binding.titleInput, binding.urlInput)
        showKeyboard(binding.titleInput)

        return binding.root
    }

    private fun configureToolbar() {
        val toolbar = binding.savedSiteAppBar.toolbar
        setToolbarTitle(toolbar)
        configureUpNavigation(toolbar)
    }

    private fun setToolbarTitle(toolbar: Toolbar) {
        if (getSavedSite() is SavedSite.Favorite) {
            toolbar.title = getString(R.string.favoriteDialogTitleEdit)
        } else {
            toolbar.title = getString(R.string.bookmarkDialogTitleEdit)
            binding.savedSiteLocationContainer.visibility = View.VISIBLE
        }
    }

    private fun configureUpNavigation(toolbar: Toolbar) {
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener {
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        userNavigatedBack(binding.titleInput, binding.urlInput)
        super.onCancel(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        userNavigatedBack(binding.titleInput, binding.urlInput)
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun userNavigatedBack(titleInput: EditText, urlInput: EditText) {
        val savedSite = getSavedSite()

        val updatedTitle = validateInput(titleInput.text.toString(), savedSite.title)
        val updatedUrl = validateInput(urlInput.text.toString(), savedSite.url)

        when (savedSite) {
            is SavedSite.Bookmark -> {
                listener?.onSavedSiteEdited(
                    savedSite.copy(title = updatedTitle, url = updatedUrl)
                )
            }
            is SavedSite.Favorite -> {
                listener?.onSavedSiteEdited(
                    savedSite.copy(title = updatedTitle, url = updatedUrl)
                )
            }
        }
        hideKeyboard()
    }

    private fun validateInput(newValue: String, existingValue: String) =
        if (newValue.isNotBlank()) newValue else existingValue

    private fun showKeyboard(titleInput: EditText) {
        titleInput.setSelection(titleInput.text.length)
        titleInput.showKeyboard()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun hideKeyboard() {
        requireActivity().window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }

    private fun populateFields(titleInput: EditText, urlInput: EditText) {
        titleInput.setText(getExistingTitle())
        urlInput.setText(getExistingUrl())
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

        fun instance(savedSite: SavedSite): EditSavedSiteDialogFragment {
            val dialog = EditSavedSiteDialogFragment()
            val bundle = Bundle()
            bundle.putSerializable(KEY_SAVED_SITE, savedSite)
            dialog.arguments = bundle
            return dialog
        }
    }
}
