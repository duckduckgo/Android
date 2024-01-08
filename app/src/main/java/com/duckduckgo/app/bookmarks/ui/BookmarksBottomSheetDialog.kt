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

package com.duckduckgo.app.bookmarks.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.duckduckgo.app.browser.databinding.BottomSheetAddBookmarkBinding
import com.duckduckgo.common.ui.view.show
import com.google.android.material.bottomsheet.BottomSheetDialog

@SuppressLint("NoBottomSheetDialog")
class BookmarksBottomSheetDialog(builder: Builder) : BottomSheetDialog(builder.context) {

    abstract class EventListener {
        /** Sets a listener to be invoked when the bottom sheet is shown */
        open fun onBottomSheetShown() {}

        /** Sets a listener to be invoked when the bottom sheet is dismiss */
        open fun onBottomSheetDismissed() {}

        /** Sets a listener to be invoked when primary item is clicked */
        open fun onPrimaryItemClicked() {}

        /** Sets a listener to be invoked when secondary item is clicked */
        open fun onSecondaryItemClicked() {}
    }

    internal class DefaultEventListener : EventListener()

    private val binding: BottomSheetAddBookmarkBinding = BottomSheetAddBookmarkBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)

        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        setOnDismissListener { builder.listener.onBottomSheetDismissed() }
        setOnShowListener { builder.listener.onBottomSheetShown() }
        binding.bookmarksBottomSheetDialogPrimaryItem.setOnClickListener {
            builder.listener.onPrimaryItemClicked()
            setOnDismissListener(null)
        }
        binding.bookmarksBottomSheetDialogSecondaryItem.setOnClickListener {
            builder.listener.onSecondaryItemClicked()
            setOnDismissListener(null)
        }

        builder.titleText?.let {
            binding.bookmarksBottomSheetDialogTitle.text = it
            binding.bookmarksBottomSheetDialogTitle.show()
        }

        binding.bookmarksBottomSheetDialogPrimaryItem.setPrimaryText(builder.primaryItemText)
        builder.primaryItemIcon?.let {
            binding.bookmarksBottomSheetDialogPrimaryItem.setLeadingIconDrawable(ContextCompat.getDrawable(context, it)!!)
        }
        builder.primaryItemTextColor?.let { binding.bookmarksBottomSheetDialogPrimaryItem.setPrimaryTextColor(it) }

        binding.bookmarksBottomSheetDialogSecondaryItem.setPrimaryText(builder.secondaryItemText)
        builder.secondaryItemIcon?.let {
            binding.bookmarksBottomSheetDialogSecondaryItem.setLeadingIconDrawable(ContextCompat.getDrawable(context, it)!!)
        }
        builder.secondaryItemTextColor?.let { binding.bookmarksBottomSheetDialogSecondaryItem.setPrimaryTextColor(it) }

        binding.bookmarksBottomSheetSwitch.setOnClickListener {
            builder.listener.onPrimaryItemClicked()
        }
    }

    fun toggleSwitch(value: Boolean) {
        binding.bookmarksBottomSheetSwitch.isChecked = value
    }

    /**
     * Creates a builder for an action bottom sheet dialog that uses
     * the default bottom sheet dialog theme.
     *
     * @param context the parent context
     */
    class Builder(val context: Context) {
        var dialog: BookmarksBottomSheetDialog? = null
        var listener: EventListener = DefaultEventListener()
            private set
        var titleText: SpannableString? = null
            private set
        var primaryItemText: String = ""
            private set
        var primaryItemIcon: Int? = null
            private set
        var primaryItemTextColor: Int? = null
            private set
        var secondaryItemText: String = ""
            private set
        var secondaryItemIcon: Int? = null
            private set
        var secondaryItemTextColor: Int? = null
            private set

        /** Sets event listener for the bottom sheet dialog */
        fun addEventListener(eventListener: EventListener): Builder {
            listener = eventListener
            return this
        }

        /** Sets title text for the bottom sheet dialog (optional) */
        fun setTitle(title: SpannableString): Builder {
            titleText = title
            return this
        }

        /** Sets primary item for the bottom sheet dialog
         * @param text primary item text
         * @param icon primary item leading icon (optional)
         * @param color primary item text color (optional)
         **/
        fun setPrimaryItem(
            text: String,
            @DrawableRes icon: Int? = null,
            @ColorRes color: Int? = null,
        ): Builder {
            primaryItemText = text
            primaryItemIcon = icon
            primaryItemTextColor = color
            return this
        }

        /** Sets secondary item for the bottom sheet dialog
         * @param text secondary item text
         * @param icon secondary item leading icon (optional)
         * @param color secondary item text color (optional)
         **/
        fun setSecondaryItem(
            text: String,
            @DrawableRes icon: Int? = null,
            @ColorRes color: Int? = null,
        ): Builder {
            secondaryItemText = text
            secondaryItemIcon = icon
            secondaryItemTextColor = color
            return this
        }

        /** Start the dialog and display it on screen */
        fun show() {
            dialog = BookmarksBottomSheetDialog(this)
            dialog?.show()
        }
    }
}
