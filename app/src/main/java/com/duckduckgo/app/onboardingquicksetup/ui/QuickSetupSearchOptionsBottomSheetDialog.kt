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
import com.duckduckgo.app.browser.databinding.BottomSheetQuickSetupSearchOptionsBinding
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.duckduckgo.app.browser.R as BrowserR

@SuppressLint("NoBottomSheetDialog")
class QuickSetupSearchOptionsBottomSheetDialog(
    private val context: Context,
    initialWithAi: Boolean = true,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetQuickSetupSearchOptionsBinding =
        BottomSheetQuickSetupSearchOptionsBinding.inflate(LayoutInflater.from(context))

    var eventListener: EventListener? = null

    private var withAi: Boolean = initialWithAi

    init {
        setContentView(binding.root)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false

        with(binding.inputScreenPicker) {
            setSelection(this@QuickSetupSearchOptionsBottomSheetDialog.withAi, BrandDesignInputScreenPicker.Transition.ANIMATE)
            setOnSelectionChangedListener { selected ->
                this@QuickSetupSearchOptionsBottomSheetDialog.withAi = selected
            }
        }

        binding.quickSetupSearchOptionsDoneButton.setOnClickListener {
            eventListener?.onDoneClicked(withAi)
            dismiss()
        }
        binding.quickSetupSearchOptionsCloseButton.setOnClickListener {
            cancel()
        }

        setOnShowListener(::setRoundCorners)
        setOnDismissListener { binding.inputScreenPicker.cancelLottieAnimations() }
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
        fun onDoneClicked(withAi: Boolean)
    }
}
