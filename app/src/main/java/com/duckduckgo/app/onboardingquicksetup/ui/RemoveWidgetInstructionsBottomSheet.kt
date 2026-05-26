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

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.app.browser.databinding.BottomSheetRemoveWidgetInstructionsBinding
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.duckduckgo.app.browser.R as BrowserR

class RemoveWidgetInstructionsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = BottomSheetRemoveWidgetInstructionsBinding.inflate(inflater, container, false)
        binding.removeWidgetInstructionsDoneButton.setOnClickListener { dismiss() }
        binding.removeWidgetInstructionsCloseButton.setOnClickListener { dismiss() }
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.isDraggable = false
        dialog.setOnShowListener(::setRoundCorners)
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (isAdded) {
            setFragmentResult(REQUEST_KEY, Bundle.EMPTY)
        }
        super.onDismiss(dialog)
    }

    private fun setRoundCorners(dialogInterface: DialogInterface) {
        val bottomSheet = (dialogInterface as BottomSheetDialog).findViewById<FrameLayout>(R.id.design_bottom_sheet)
        val ctx = requireContext()
        val shapeDrawable = MaterialShapeDrawable.createWithElevationOverlay(ctx)
        shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(CornerFamily.ROUNDED, ctx.resources.getDimension(BrowserR.dimen.onboardingBottomSheetCornerRadius))
            .setTopRightCorner(CornerFamily.ROUNDED, ctx.resources.getDimension(BrowserR.dimen.onboardingBottomSheetCornerRadius))
            .build()
        bottomSheet?.background = shapeDrawable
    }

    companion object {
        const val TAG = "RemoveWidgetInstructionsBottomSheetFragment"

        /** Fragment-result request key. The host registers a listener under this key. */
        const val REQUEST_KEY = "RemoveWidgetInstructionsResult"
    }
}
