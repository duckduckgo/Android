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

package com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.BottomSheetNewAddressBarOptionBinding
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarSelection.SEARCH_AND_AI
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarSelection.SEARCH_ONLY
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable

enum class NewAddressBarSelection(val value: String) {
    SEARCH_ONLY("search_only"),
    SEARCH_AND_AI("search_and_ai"),
}

interface NewAddressBarCallback {
    fun onDisplayed()
    fun onConfirmed(selection: NewAddressBarSelection)
    fun onNotNow()
}

@SuppressLint("NoBottomSheetDialog")
class NewAddressBarOptionBottomSheetDialog(
    private val context: Context,
    private val isDarkThemeEnabled: Boolean,
    private val newAddressBarCallback: NewAddressBarCallback?,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetNewAddressBarOptionBinding =
        BottomSheetNewAddressBarOptionBinding.inflate(LayoutInflater.from(context))

    private var searchAndDuckAiSelected = true
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var isLottieLoaded = false
    private var pendingShow = false

    init {
        setContentView(binding.root)
        this.behavior.isDraggable = false
        this.behavior.peekHeight = 0
        this.behavior.maxHeight = MAX_HEIGHT_DP.toPx()

        setOnShowListener {
            setRoundCorners()
            newAddressBarCallback?.onDisplayed()
        }

        setOnCancelListener {
            restoreOrientation()
            dismiss()
        }

        setupLottieAnimation()
        setupSelectionLogic()

        binding.newAddressBarOptionBottomSheetDialogPrimaryButton.setOnClickListener {
            val selection = if (searchAndDuckAiSelected) SEARCH_AND_AI else SEARCH_ONLY
            newAddressBarCallback?.onConfirmed(selection)
            restoreOrientation()
            dismiss()
        }

        binding.newAddressBarOptionBottomSheetDialogGhostButton.setOnClickListener {
            newAddressBarCallback?.onNotNow()
            restoreOrientation()
            dismiss()
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        val bottomSheet = findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.layoutParams = bottomSheet?.layoutParams?.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        setRoundCorners()
        lockOrientationToPortrait()

        this.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun setupSelectionLogic() {
        updateSelectionState()

        binding.newAddressBarOptionBottomSheetDialogSearchOnlyButton.setOnClickListener {
            if (searchAndDuckAiSelected) {
                searchAndDuckAiSelected = false
                updateSelectionState()
            }
        }

        binding.newAddressBarOptionBottomSheetDialogSearchAndDuckAiButton.setOnClickListener {
            if (!searchAndDuckAiSelected) {
                searchAndDuckAiSelected = true
                updateSelectionState()
            }
        }
    }

    private fun updateSelectionState() {
        binding.newAddressBarOptionBottomSheetDialogSearchOnlyButton.isSelected =
            !searchAndDuckAiSelected
        binding.searchOnlyCheckbox.isChecked = !searchAndDuckAiSelected
        binding.searchOnlyCheckbox.isEnabled = !searchAndDuckAiSelected
        binding.newAddressBarOptionBottomSheetDialogSearchOnlyButton.background =
            if (!searchAndDuckAiSelected) {
                ContextCompat.getDrawable(
                    context,
                    R.drawable.background_new_address_bar_option_selected,
                )
            } else {
                ContextCompat.getDrawable(context, R.drawable.background_new_address_bar_option)
            }

        binding.newAddressBarOptionBottomSheetDialogSearchAndDuckAiButton.isSelected =
            searchAndDuckAiSelected
        binding.searchAndDuckAiCheckbox.isChecked = searchAndDuckAiSelected
        binding.searchAndDuckAiCheckbox.isEnabled = searchAndDuckAiSelected
        binding.newAddressBarOptionBottomSheetDialogSearchAndDuckAiButton.background =
            if (searchAndDuckAiSelected) {
                ContextCompat.getDrawable(
                    context,
                    R.drawable.background_new_address_bar_option_selected,
                )
            } else {
                ContextCompat.getDrawable(context, R.drawable.background_new_address_bar_option)
            }
    }

    private fun setupLottieAnimation() {
        val lottieView = binding.newAddressBarOptionBottomSheetDialogAnimation

        val animationResource = if (isDarkThemeEnabled) {
            R.raw.new_address_bar_option_animation_dark
        } else {
            R.raw.new_address_bar_option_animation_light
        }
        lottieView.setAnimation(animationResource)

        lottieView.addLottieOnCompositionLoadedListener { composition ->
            isLottieLoaded = true
            playIntroThenLoop(lottieView, composition.durationFrames.toInt())

            if (pendingShow) {
                pendingShow = false
                show()
            }
        }
    }

    private fun playIntroThenLoop(lottieView: LottieAnimationView, totalFrames: Int) {
        lottieView.setMinAndMaxFrame(0, 30)
        lottieView.repeatCount = 0
        lottieView.playAnimation()

        lottieView.addAnimatorListener(
            object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    lottieView.removeAnimatorListener(this)

                    lottieView.setMinAndMaxFrame(31, totalFrames - 1)
                    lottieView.repeatCount = LottieDrawable.INFINITE
                    lottieView.playAnimation()
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            },
        )
    }

    private fun setRoundCorners() {
        val bottomSheet =
            findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

        val shapeDrawable = MaterialShapeDrawable.createWithElevationOverlay(context)
        shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(
                CornerFamily.ROUNDED,
                context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.dialogBorderRadius),
            )
            .setTopRightCorner(
                CornerFamily.ROUNDED,
                context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.dialogBorderRadius),
            )
            .build()
        bottomSheet?.background = shapeDrawable
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun lockOrientationToPortrait() {
        (context as? Activity)?.let {
            originalOrientation = it.requestedOrientation
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun restoreOrientation() {
        (context as? Activity)?.let {
            it.requestedOrientation = originalOrientation
        }
    }

    override fun show() {
        val lottieView = binding.newAddressBarOptionBottomSheetDialogAnimation
        val composition = lottieView.composition
        if (isLottieLoaded || composition != null) {
            if (!isLottieLoaded) {
                isLottieLoaded = true
                composition?.let {
                    playIntroThenLoop(lottieView, it.durationFrames.toInt())
                }
            }
            super.show()
        } else {
            pendingShow = true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        restoreOrientation()
    }

    companion object {
        const val MAX_HEIGHT_DP = 900
    }
}
