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

package com.duckduckgo.app.bookmarks.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.databinding.BottomSheetAddBookmarkBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.duckduckgo.mobile.android.R as CommonR

@SuppressLint("NoBottomSheetDialog")
class BookmarkAddedConfirmationDialog(
    context: Context,
    private val bookmarkFolder: BookmarkFolder?,
) : BottomSheetDialog(context) {

    abstract class EventListener {
        /** Sets a listener to be invoked when favorite state is changed */
        open fun onFavoriteStateChangeClicked(isFavorited: Boolean) {}

        /** Sets a listener to be invoked when edit bookmarks is clicked */
        open fun onEditBookmarkClicked() {}
    }

    private var listener: EventListener? = null

    private val binding = BottomSheetAddBookmarkBinding.inflate(LayoutInflater.from(context))

    private val autoDismissDialogJob = ConflatedJob()

    override fun show() {
        setContentView(binding.root)

        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false
        roundCornersAlways(this)
        binding.bookmarksBottomSheetDialogTitle.text = getBookmarksBottomSheetTitle(context, bookmarkFolder)

        binding.setAsFavorite.setOnClickListener {
            cancelDialogAutoDismiss()
            binding.setAsFavoriteSwitch.isChecked = !binding.setAsFavoriteSwitch.isChecked
            listener?.onFavoriteStateChangeClicked(binding.setAsFavoriteSwitch.isChecked)
        }
        binding.setAsFavoriteSwitch.setOnClickListener {
            cancelDialogAutoDismiss()
            listener?.onFavoriteStateChangeClicked(binding.setAsFavoriteSwitch.isChecked)
        }
        binding.editBookmark.setOnClickListener {
            cancelDialogAutoDismiss()
            listener?.onEditBookmarkClicked()
            dismiss()
        }

        autoDismissDialogJob += lifecycleScope.launch {
            delay(BOOKMARKS_BOTTOM_SHEET_DURATION)
            dismiss()
        }
        super.show()
    }

    private fun cancelDialogAutoDismiss() {
        autoDismissDialogJob.cancel()
    }

    private fun getBookmarksBottomSheetTitle(context: Context, bookmarkFolder: BookmarkFolder?): SpannableString {
        val folderName = bookmarkFolder?.name ?: ""
        val fullText = context.getString(com.duckduckgo.saved.sites.impl.R.string.bookmarkAddedInBookmarks, folderName)
        val spannableString = SpannableString(fullText)

        val boldStart = fullText.indexOf(folderName)
        val boldEnd = boldStart + folderName.length
        spannableString.setSpan(StyleSpan(Typeface.BOLD), boldStart, boldEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannableString
    }

    /** Sets event listener for the bottom sheet dialog */
    fun addEventListener(eventListener: EventListener) {
        listener = eventListener
    }

    // TODO: Use a style when bookmarks is moved to its own module
    private fun roundCornersAlways(dialog: BottomSheetDialog) {
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(R.id.design_bottom_sheet)
            bottomSheet?.background = MaterialShapeDrawable(
                ShapeAppearanceModel.builder().apply {
                    setTopLeftCorner(CornerFamily.ROUNDED, context.resources.getDimension(CommonR.dimen.dialogBorderRadius))
                    setTopRightCorner(CornerFamily.ROUNDED, context.resources.getDimension(CommonR.dimen.dialogBorderRadius))
                }.build(),
            )
        }
    }

    private companion object {
        private const val BOOKMARKS_BOTTOM_SHEET_DURATION = 3_500L
    }
}
