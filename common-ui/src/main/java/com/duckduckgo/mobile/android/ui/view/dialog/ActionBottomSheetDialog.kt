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
import androidx.annotation.DrawableRes
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.BottomSheetActionBinding
import com.duckduckgo.mobile.android.ui.view.show
import com.google.android.material.bottomsheet.BottomSheetDialog

class ActionBottomSheetDialog(context: Context) : BottomSheetDialog(context, R.style.Widget_DuckDuckGo_BottomSheetDialog) {

    abstract class EventListener {
        open fun onBottomSheetShown() {}
        open fun onBottomSheetDismissed() {}
        open fun onPrimaryItemClicked() {}
        open fun onSecondaryItemClicked() {}
    }

    private val binding: BottomSheetActionBinding

    init {
        binding = BottomSheetActionBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)
    }

    fun addEventListener(eventListener: EventListener) {
        setOnDismissListener { eventListener.onBottomSheetDismissed() }
        setOnShowListener { eventListener.onBottomSheetShown() }
        binding.actionBottomSheetDialogPrimaryItem.setOnClickListener { eventListener.onPrimaryItemClicked() }
        binding.actionBottomSheetDialogSecondaryItem.setOnClickListener { eventListener.onSecondaryItemClicked() }
    }

    fun setTitle(title: String) {
        binding.actionBottomSheetDialogTitle.text = title
        binding.actionBottomSheetDialogTitle.show()
    }

    fun onPrimaryItem(primaryText: String, @DrawableRes primaryIcon: Int? = null) {
        binding.actionBottomSheetDialogPrimaryItem.setPrimaryText(primaryText)
        primaryIcon?.let { binding.actionBottomSheetDialogPrimaryItem.setLeadingIcon(it) }
    }

    fun onSecondaryItem(secondaryText: String, @DrawableRes secondaryIcon: Int? = null) {
        binding.actionBottomSheetDialogSecondaryItem.setPrimaryText(secondaryText)
        secondaryIcon?.let { binding.actionBottomSheetDialogSecondaryItem.setLeadingIcon(it) }
    }
}
