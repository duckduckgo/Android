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
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.view.hideKeyboard
import com.duckduckgo.app.global.view.showKeyboard
import org.jetbrains.anko.find


class EditBookmarkDialogFragment : DialogFragment() {

    interface EditBookmarkListener {
        fun onBookmarkEdited(id: Long, title: String, url: String)
    }

    var listener: EditBookmarkListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView = View.inflate(activity, R.layout.edit_bookmark, null)
        val titleInput = rootView.find<EditText>(R.id.titleInput)
        val urlInput = rootView.find<EditText>(R.id.urlInput)

        val alertBuilder = AlertDialog.Builder(activity!!)
            .setView(rootView)
            .setTitle(R.string.bookmarkTitleEdit)
            .setPositiveButton(R.string.bookmarkSave) { _, _ ->
                userAcceptedDialog(titleInput, urlInput)
            }

        validateBundleArguments()

        populateFields(titleInput, urlInput)

        val alert = alertBuilder.create()
        showKeyboard(titleInput, alert)
        return alert
    }

    private fun userAcceptedDialog(titleInput: EditText, urlInput: EditText) {
        listener?.onBookmarkEdited(
            getExistingId(),
            titleInput.text.toString(),
            urlInput.text.toString()
        )

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

    private fun getExistingId(): Long = arguments!!.getLong(KEY_BOOKMARK_ID)
    private fun getExistingTitle(): String? = arguments!!.getString(KEY_PREEXISTING_TITLE)
    private fun getExistingUrl(): String? = arguments!!.getString(KEY_PREEXISTING_URL)

    private fun validateBundleArguments() {
        if (arguments == null) throw IllegalArgumentException("Missing arguments bundle")
        val args = arguments!!
        if (!args.containsKey(KEY_PREEXISTING_TITLE) ||
            !args.containsKey(KEY_PREEXISTING_URL)
        ) {
            throw IllegalArgumentException("Bundle arguments required [KEY_PREEXISTING_TITLE, KEY_PREEXISTING_URL]")
        }
    }

    companion object {
        private const val KEY_BOOKMARK_ID = "KEY_BOOKMARK_ID"
        private const val KEY_PREEXISTING_TITLE = "KEY_PREEXISTING_TITLE"
        private const val KEY_PREEXISTING_URL = "KEY_PREEXISTING_URL"

        fun instance(bookmarkId: Long, title: String?, url: String?): EditBookmarkDialogFragment {

            val dialog = EditBookmarkDialogFragment()
            val bundle = Bundle()

            bundle.putLong(KEY_BOOKMARK_ID, bookmarkId)
            bundle.putString(KEY_PREEXISTING_TITLE, title)
            bundle.putString(KEY_PREEXISTING_URL, url)

            dialog.arguments = bundle
            return dialog
        }
    }

}