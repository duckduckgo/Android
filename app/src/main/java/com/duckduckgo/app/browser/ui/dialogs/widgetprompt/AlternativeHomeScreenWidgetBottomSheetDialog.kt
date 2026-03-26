/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.ui.dialogs.widgetprompt

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.BottomSheetHomeScreenWidgetBinding
import com.duckduckgo.common.ui.setRoundCorners
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

@SuppressLint("NoBottomSheetDialog")
class AlternativeHomeScreenWidgetBottomSheetDialog(
    private val context: Context,
    isLightModeEnabled: Boolean,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetHomeScreenWidgetBinding =
        BottomSheetHomeScreenWidgetBinding.inflate(LayoutInflater.from(context))

    var eventListener: EventListener? = null

    init {
        setContentView(binding.root)
        // We need the dialog to always be expanded and not draggable because the content takes up a lot of vertical space and requires a scroll view,
        // especially in landscape aspect-ratios. If the dialog started as collapsed, the drag would interfere with internal scroll.
        this.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        this.behavior.isDraggable = false

        setOnShowListener { dialogInterface ->
            (dialogInterface as BottomSheetDialog).setRoundCorners()
            eventListener?.onShown()
        }
        setOnCancelListener {
            eventListener?.onCanceled()
            dismiss()
        }
        binding.homeScreenWidgetBottomSheetDialogImage.setImageResource(
            if (isLightModeEnabled) {
                R.drawable.widget_promo_light
            } else {
                R.drawable.widget_promo_dark
            },
        )
        binding.homeScreenWidgetBottomSheetDialogPrimaryButton.setOnClickListener {
            eventListener?.onAddWidgetButtonClicked()
            dismiss()
        }
        binding.homeScreenWidgetBottomSheetDialogGhostButton.setOnClickListener {
            eventListener?.onNotNowButtonClicked()
            dismiss()
        }
    }

    interface EventListener {
        fun onShown()
        fun onCanceled()
        fun onAddWidgetButtonClicked()
        fun onNotNowButtonClicked()
    }
}
