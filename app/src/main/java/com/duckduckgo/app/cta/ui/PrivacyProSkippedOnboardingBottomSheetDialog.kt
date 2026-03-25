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

package com.duckduckgo.app.cta.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat.getString
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.BottomSheetPrivacyProSkippedOnboardingBinding
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.R as MaterialR

@SuppressLint("NoBottomSheetDialog")
class PrivacyProSkippedOnboardingBottomSheetDialog(
    private val context: Context,
    private val isFreeTrialCopy: Boolean,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetPrivacyProSkippedOnboardingBinding =
        BottomSheetPrivacyProSkippedOnboardingBinding.inflate(LayoutInflater.from(context))

    var eventListener: EventListener? = null

    init {
        setContentView(binding.root)
        this.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        this.behavior.isDraggable = false

        binding.privacyProSkippedOnboardingTitle.text =
            getString(context, R.string.onboardingSkippedPrivacyProDaxDialogTitle).preventWidows()
        binding.privacyProSkippedOnboardingSubTitle.text =
            getString(context, R.string.onboardingPrivacyProDaxDialogDescription).html(context)
        binding.privacyProSkippedOnboardingPrimaryButton.text =
            if (isFreeTrialCopy) {
                getString(context, R.string.onboardingPrivacyProDaxDialogFreeTrialOkButton)
            } else {
                getString(context, R.string.onboardingPrivacyProDaxDialogOkButton)
            }

        setOnShowListener { dialogInterface ->
            setRoundCorners(dialogInterface)
            eventListener?.onShown()
        }
        setOnCancelListener {
            eventListener?.onCanceled()
        }
        binding.privacyProSkippedOnboardingPrimaryButton.setOnClickListener {
            eventListener?.onPrimaryButtonClicked()
        }
        binding.privacyProSkippedOnboardingGhostButton.setOnClickListener {
            eventListener?.onNotNowButtonClicked()
        }
        binding.privacyProSkippedOnboardingCloseButton.setOnClickListener {
            eventListener?.onCanceled()
        }
    }

    private fun setRoundCorners(dialogInterface: DialogInterface) {
        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(MaterialR.id.design_bottom_sheet)

        val shapeDrawable = MaterialShapeDrawable.createWithElevationOverlay(context)
        shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(CornerFamily.ROUNDED, context.resources.getDimension(CommonR.dimen.dialogBorderRadius))
            .setTopRightCorner(CornerFamily.ROUNDED, context.resources.getDimension(CommonR.dimen.dialogBorderRadius))
            .build()
        bottomSheet?.background = shapeDrawable
    }

    interface EventListener {
        fun onShown()
        fun onPrimaryButtonClicked()
        fun onNotNowButtonClicked()
        fun onCanceled()
    }
}
