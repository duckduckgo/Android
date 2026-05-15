/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.onboardingquicksetup.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.duckduckgo.app.browser.databinding.BottomSheetQuickSetupAddressBarPositionBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.duckduckgo.app.browser.R as BrowserR

@SuppressLint("NoBottomSheetDialog")
class QuickSetupAddressBarPositionBottomSheetDialog(
    private val context: Context,
    initialSelection: OmnibarType = OmnibarType.SINGLE_TOP,
    showSplitOption: Boolean = false,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetQuickSetupAddressBarPositionBinding =
        BottomSheetQuickSetupAddressBarPositionBinding.inflate(LayoutInflater.from(context))

    var eventListener: EventListener? = null

    private var currentSelection: OmnibarType = initialSelection

    init {
        setContentView(binding.root)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false

        with(binding.addressBarPicker) {
            isSplitOptionVisible = showSplitOption
            setSelection(currentSelection)
            setOnSelectionChangedListener { selected -> currentSelection = selected }
        }

        binding.quickSetupAddressBarPositionDoneButton.setOnClickListener {
            eventListener?.onDoneClicked(currentSelection)
            dismiss()
        }
        binding.quickSetupAddressBarPositionCloseButton.setOnClickListener {
            cancel()
        }

        setOnShowListener(::setRoundCorners)
        setOnCancelListener { eventListener?.onCanceled() }
    }

    private fun setRoundCorners(dialogInterface: DialogInterface) {
        val bottomSheet = (dialogInterface as BottomSheetDialog).findViewById<FrameLayout>(R.id.design_bottom_sheet)
        val shapeDrawable = MaterialShapeDrawable.createWithElevationOverlay(context)
        shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(CornerFamily.ROUNDED, context.resources.getDimension(BrowserR.dimen.onboardingBottomSheetCornerRadius))
            .setTopRightCorner(CornerFamily.ROUNDED, context.resources.getDimension(BrowserR.dimen.onboardingBottomSheetCornerRadius))
            .build()
        bottomSheet?.background = shapeDrawable
    }

    interface EventListener {
        fun onDoneClicked(selectedPosition: OmnibarType)
        fun onCanceled()
    }
}
