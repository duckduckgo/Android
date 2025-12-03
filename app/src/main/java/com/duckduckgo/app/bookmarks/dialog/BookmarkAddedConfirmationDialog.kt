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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.bookmarks.BookmarkAddedDialogPlugin
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.BottomSheetAddBookmarkBinding
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.logcat
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.R as MaterialR

@SuppressLint("NoBottomSheetDialog")
class BookmarkAddedConfirmationDialog(
    context: Context,
    private val bookmarkFolder: BookmarkFolder?,
    private val promoPlugins: PluginPoint<BookmarkAddedDialogPlugin>,
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

        addInteractionListeners()

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

        watchForPromoViewChanges()
        updatePromoViews()

        startAutoDismissTimer()
        super.show()
    }

    private fun updatePromoViews() {
        lifecycleScope.launch {
            val viewsToInclude = promoPlugins.getPlugins().mapNotNull { it.getView() }
            logcat { "Sync-promo: updating promo views. Found ${viewsToInclude.size} promos" }

            with(binding.promotionContainer) {
                removeAllViews()
                viewsToInclude.forEach { addView(it) }
            }
        }
    }

    private fun addInteractionListeners() {
        // any touches anywhere in the dialog will cancel auto-dismiss
        binding.root.onTouchObserved = { cancelDialogAutoDismiss() }
    }

    private fun watchForPromoViewChanges() {
        with(binding.promotionContainer) {
            viewTreeObserver.addOnGlobalLayoutListener {
                if (binding.promotionContainer.children.any { it.isVisible }) {
                    binding.promotionContainer.show()
                } else {
                    binding.promotionContainer.gone()
                }
            }
        }
    }

    private fun startAutoDismissTimer() {
        autoDismissDialogJob += lifecycleScope.launch {
            delay(BOOKMARKS_BOTTOM_SHEET_DURATION)
            if (isShowing && isActive) {
                dismiss()
            }
        }
    }

    private fun cancelDialogAutoDismiss() {
        autoDismissDialogJob.cancel()
    }

    private fun getBookmarksBottomSheetTitle(context: Context, bookmarkFolder: BookmarkFolder?): SpannableString {
        val folderName = bookmarkFolder?.name ?: ""

        val fullText = context.getString(R.string.addBookmarkDialogBookmarkAddedInFolder, folderName)
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
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(MaterialR.id.design_bottom_sheet)
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

/**
 * A LinearLayout that observes all touch events flowing through it
 * without interfering with child view touch handling.
 */
class TouchObservingLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var onTouchObserved: (() -> Unit)? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            onTouchObserved?.invoke()
        }
        return super.dispatchTouchEvent(ev)
    }
}
