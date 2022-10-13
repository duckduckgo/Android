/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.view.dialog

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.BottomSheetActionBinding
import com.duckduckgo.mobile.android.ui.view.show
import com.google.android.material.bottomsheet.BottomSheetDialog

class ActionBottomSheetDialog(builder: Builder) : BottomSheetDialog(builder.context, R.style.Widget_DuckDuckGo_BottomSheetDialog) {

    abstract class EventListener {
        open fun onBottomSheetShown() {}
        open fun onBottomSheetDismissed() {}
        open fun onPrimaryItemClicked() {}
        open fun onSecondaryItemClicked() {}
    }

    internal class DefaultEventListener : EventListener()

    private val binding: BottomSheetActionBinding = BottomSheetActionBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)

        setOnDismissListener { builder.listener.onBottomSheetDismissed() }
        setOnShowListener { builder.listener.onBottomSheetShown() }
        binding.actionBottomSheetDialogPrimaryItem.setOnClickListener { builder.listener.onPrimaryItemClicked() }
        binding.actionBottomSheetDialogSecondaryItem.setOnClickListener { builder.listener.onSecondaryItemClicked() }

        builder.titleText?.let {
            binding.actionBottomSheetDialogTitle.text = it
            binding.actionBottomSheetDialogTitle.show()
        }

        binding.actionBottomSheetDialogPrimaryItem.setPrimaryText(builder.primaryItemText)
        builder.primaryItemIcon?.let { binding.actionBottomSheetDialogPrimaryItem.setLeadingIcon(it) }
        builder.primaryItemTextColor?.let { binding.actionBottomSheetDialogPrimaryItem.setPrimaryTextColor(it) }

        binding.actionBottomSheetDialogSecondaryItem.setPrimaryText(builder.secondaryItemText)
        builder.secondaryItemIcon?.let { binding.actionBottomSheetDialogSecondaryItem.setLeadingIcon(it) }
        builder.secondaryItemTextColor?.let { binding.actionBottomSheetDialogSecondaryItem.setPrimaryTextColor(it) }
    }

    class Builder(val context: Context) {
        var listener: EventListener = DefaultEventListener()
        var titleText: String? = null
        var primaryItemText: String = ""
        var primaryItemIcon: Int? = null
        var primaryItemTextColor: Int? = null
        var secondaryItemText: String = ""
        var secondaryItemIcon: Int? = null
        var secondaryItemTextColor: Int? = null

        fun addEventListener(eventListener: EventListener): Builder {
            listener = eventListener
            return this
        }

        fun setTitle(title: String): Builder {
            titleText = title
            return this
        }

        fun onPrimaryItem(
            text: String,
            @DrawableRes icon: Int? = null,
            @ColorRes color: Int? = null
        ): Builder {
            primaryItemText = text
            primaryItemIcon = icon
            primaryItemTextColor = color
            return this
        }

        fun onSecondaryItem(
            text: String,
            @DrawableRes icon: Int? = null,
            @ColorRes color: Int? = null
        ): Builder {
            secondaryItemText = text
            secondaryItemIcon = icon
            secondaryItemTextColor = color
            return this
        }

        fun show() {
            val dialog = ActionBottomSheetDialog(this)
            dialog.show()
        }
    }
}
