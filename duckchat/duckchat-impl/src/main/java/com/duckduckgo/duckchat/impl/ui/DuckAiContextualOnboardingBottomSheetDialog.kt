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

package com.duckduckgo.duckchat.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.duckchat.api.DuckChatNativeSettingsNoParams
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.BottomSheetDuckAiContextualOnboardingBinding
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.duckduckgo.mobile.android.R as CommonR

@SuppressLint("NoBottomSheetDialog")
class DuckAiContextualOnboardingBottomSheetDialog(
    context: Context,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val globalActivityStarter: GlobalActivityStarter,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetDuckAiContextualOnboardingBinding =
        BottomSheetDuckAiContextualOnboardingBinding.inflate(LayoutInflater.from(context))

    var eventListener: EventListener? = null

    init {
        setContentView(binding.root)

        setCancelable(false)
        setCanceledOnTouchOutside(false)
        behavior.isDraggable = false

        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        setOnShowListener { dialogInterface ->
            setRoundCorners(dialogInterface)
        }

        binding.duckAiContextualOnboardingTitle.text =
            context.getString(R.string.duck_chat_contextual_onboarding_title)
        binding.duckAiContextualOnboardingBody.text =
            context.getString(R.string.duck_chat_contextual_onboarding_body)

        binding.duckAiContextualOnboardingPrimaryButton.setOnClickListener {
            coroutineScope.launch {
                duckChatFeatureRepository.setContextualOnboardingCompleted(true)
                eventListener?.onConfirmed()
            }
            dismiss()
        }

        binding.duckAiContextualOnboardingSecondaryButton.setOnClickListener {
            globalActivityStarter.start(context, DuckChatNativeSettingsNoParams)
            dismiss()
        }
    }

    /**
     * By default, when bottom sheet dialog is expanded, the corners become squared.
     * This function ensures that the bottom sheet dialog will have rounded corners even when in an expanded state.
     */
    private fun setRoundCorners(dialogInterface: DialogInterface) {
        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

        val shapeDrawable = MaterialShapeDrawable.createWithElevationOverlay(context)
        shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(CornerFamily.ROUNDED, context.resources.getDimension(CommonR.dimen.dialogBorderRadius))
            .setTopRightCorner(CornerFamily.ROUNDED, context.resources.getDimension(CommonR.dimen.dialogBorderRadius))
            .build()
        bottomSheet?.background = shapeDrawable
    }

    interface EventListener {
        fun onConfirmed()
    }
}
