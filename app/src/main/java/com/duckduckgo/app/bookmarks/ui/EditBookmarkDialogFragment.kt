/*
 * Copyright (c) 2018 DuckDuckGo
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
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.view.hideKeyboard
import com.duckduckgo.app.global.view.showKeyboard

class EditBookmarkDialogFragment : DialogFragment() {

    interface EditBookmarkListener {
        fun onSavedSiteEdited(savedSite: SavedSite)
    }

    var listener: EditBookmarkListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView = View.inflate(activity, R.layout.edit_bookmark, null)
        val titleInput = rootView.findViewById<EditText>(R.id.titleInput)
        val urlInput = rootView.findViewById<EditText>(R.id.urlInput)

        val alertBuilder = AlertDialog.Builder(requireActivity())
            .setView(rootView)
            .setTitle(R.string.bookmarkDialogTitleEdit)
            .setPositiveButton(R.string.dialogSave) { _, _ ->
                userAcceptedDialog(titleInput, urlInput)
            }

        validateBundleArguments()

        populateFields(titleInput, urlInput)

        val alert = alertBuilder.create()
        showKeyboard(titleInput, alert)
        return alert
    }

    private fun userAcceptedDialog(titleInput: EditText, urlInput: EditText) {
        when (val savedSite = getSavedSite()) {
            is SavedSite.Bookmark -> {
                listener?.onSavedSiteEdited(
                    savedSite.copy(title = titleInput.text.toString(), url = urlInput.text.toString())
                )
            }
            is SavedSite.Favorite -> {
                listener?.onSavedSiteEdited(
                    savedSite.copy(title = titleInput.text.toString(), url = urlInput.text.toString())
                )
            }
        }
        titleInput.hideKeyboard()
    }

    private fun showKeyboard(titleInput: EditText, alert: AlertDialog) {
        titleInput.setSelection(titleInput.text.length)
        titleInput.showKeyboard()
        alert.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun populateFields(titleInput: EditText, urlInput: EditText) {
        titleInput.setText(getExistingTitle())
        urlInput.setText(getExistingUrl())
    }

    private fun getSavedSite(): SavedSite = requireArguments().getSerializable(KEY_SAVED_SITE) as SavedSite
    private fun getExistingId(): Long = getSavedSite().id
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

        fun instance(savedSite: SavedSite): EditBookmarkDialogFragment {

            val dialog = EditBookmarkDialogFragment()
            val bundle = Bundle()

            bundle.putSerializable(KEY_SAVED_SITE, savedSite)
            dialog.arguments = bundle
            return dialog
        }
    }

}
