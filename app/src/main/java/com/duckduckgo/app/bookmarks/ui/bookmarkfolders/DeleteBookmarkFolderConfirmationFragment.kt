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

package com.duckduckgo.app.bookmarks.ui.bookmarkfolders

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.browser.R

class DeleteBookmarkFolderConfirmationFragment : DialogFragment() {

    interface DeleteBookmarkFolderListener {
        fun onBookmarkFolderDeleted(bookmarkFolder: BookmarkFolder)
    }

    var listener: DeleteBookmarkFolderListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(requireContext())
                    .setMessage(getMessageString())
                    .setTitle(R.string.delete)
                    .setPositiveButton(R.string.delete) { _,_ -> listener?.onBookmarkFolderDeleted(arguments?.getSerializable(BOOKMARK_FOLDER_KEY) as BookmarkFolder)}
                    .setNegativeButton(R.string.cancel) { _,_ -> }
                    .create()

    fun getMessageString(): SpannableString {
        val bookmarkFolder = arguments?.getSerializable(BOOKMARK_FOLDER_KEY) as BookmarkFolder
        val string = SpannableString(resources.getQuantityString(R.plurals.bookmarkFolderDeleteDialogMessage, bookmarkFolder.numItems, bookmarkFolder.name, bookmarkFolder.numItems))
        string.setSpan(StyleSpan(Typeface.BOLD), MESSAGE_LENGTH, MESSAGE_LENGTH + bookmarkFolder.name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return string
    }

    companion object {

        private const val BOOKMARK_FOLDER_KEY = "BOOKMARK_FOLDER_KEY"
        private const val MESSAGE_LENGTH = 32

        fun instance(bookmarkFolder: BookmarkFolder): DeleteBookmarkFolderConfirmationFragment {
            val fragment = DeleteBookmarkFolderConfirmationFragment()
            val bundle = Bundle()
            bundle.putSerializable(BOOKMARK_FOLDER_KEY, bookmarkFolder)
            fragment.arguments = bundle
            return fragment
        }
    }
}
