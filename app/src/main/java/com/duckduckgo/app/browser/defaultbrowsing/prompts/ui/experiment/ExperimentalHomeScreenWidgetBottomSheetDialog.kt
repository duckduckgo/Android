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

package com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.experiment

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.duckduckgo.app.browser.databinding.BottomSheetExperimentHomeScreenWidgetBinding
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable

@SuppressLint("NoBottomSheetDialog")
class ExperimentalHomeScreenWidgetBottomSheetDialog(
    private val context: Context,
    isLightModeEnabled: Boolean,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetExperimentHomeScreenWidgetBinding =
        BottomSheetExperimentHomeScreenWidgetBinding.inflate(LayoutInflater.from(context))

    var eventListener: EventListener? = null

    init {
        setContentView(binding.root)
        // We need the dialog to always be expanded and not draggable because the content takes up a lot of vertical space and requires a scroll view,
        // especially in landscape aspect-ratios. If the dialog started as collapsed, the drag would interfere with internal scroll.
        this.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        this.behavior.isDraggable = false

        setOnShowListener { dialogInterface ->
            setRoundCorners(dialogInterface)
            eventListener?.onShown()
        }
        setOnCancelListener {
            eventListener?.onCanceled()
            dismiss()
        }
        binding.experimentHomeScreenWidgetBottomSheetDialogImage.setImageResource(
            if (isLightModeEnabled) {
                com.duckduckgo.app.browser.R.drawable.experiment_widget_promo_light
            } else {
                com.duckduckgo.app.browser.R.drawable.experiment_widget_promo_dark
            },
        )
        binding.experimentHomeScreenWidgetBottomSheetDialogPrimaryButton.setOnClickListener {
            eventListener?.onAddWidgetButtonClicked()
            dismiss()
        }
        binding.experimentHomeScreenWidgetBottomSheetDialogGhostButton.setOnClickListener {
            eventListener?.onNotNowButtonClicked()
            dismiss()
        }
    }

    /**
     * By default, when bottom sheet dialog is expanded, the corners become squared.
     * This function ensures that the bottom sheet dialog will have rounded corners even when in an expanded state.
     */
    private fun setRoundCorners(dialogInterface: DialogInterface) {
        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(R.id.design_bottom_sheet)

        val shapeDrawable = MaterialShapeDrawable.createWithElevationOverlay(context)
        shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(CornerFamily.ROUNDED, context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.dialogBorderRadius))
            .setTopRightCorner(CornerFamily.ROUNDED, context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.dialogBorderRadius))
            .build()
        bottomSheet?.background = shapeDrawable
    }

    interface EventListener {
        fun onShown()
        fun onCanceled()
        fun onAddWidgetButtonClicked()
        fun onNotNowButtonClicked()
    }
}
