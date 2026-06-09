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

package com.duckduckgo.app.browser.newaddressbaroption

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat.getString
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.BottomSheetNewAddressBarPickerBinding
import com.duckduckgo.app.onboardingquicksetup.ui.BrandDesignInputScreenPicker.Transition
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.utils.extensions.html
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

@SuppressLint("NoBottomSheetDialog")
class NewAddressBarPickerBottomSheetDialog(
    private val context: Context,
    private val isLightMode: Boolean,
    private val callback: NewAddressBarCallback?,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetNewAddressBarPickerBinding =
        BottomSheetNewAddressBarPickerBinding.inflate(LayoutInflater.from(context))

    private var searchAndDuckAiSelected = true
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var leftWingRunnable: Runnable? = null
    private var contentFadeInAnimatorSet: AnimatorSet? = null

    init {
        setContentView(binding.root)

        this.behavior.isDraggable = false
        this.behavior.isHideable = false
        this.behavior.peekHeight = 0

        setCancelable(false)

        with(binding.inputScreen) {
            inputScreenDescription.text = context.getString(R.string.preOnboardingInputScreenDescription).html(context)
            with(inputScreenPicker) {
                setLightMode(isLightMode)
                setSelection(withAi = true, transition = Transition.NONE)
                setOnSelectionChangedListener { withAi ->
                    searchAndDuckAiSelected = withAi
                    setSelection(withAi, transition = Transition.CROSSFADE_ANIMATE)
                }
            }
        }

        setOnShowListener {
            if (!isWindowValid()) return@setOnShowListener
            (it as BottomSheetDialog).setRoundCorners()
            callback?.onDisplayed()
            playLeftWingAnimation()
            revealContent()
        }

        binding.confirmButton.setOnClickListener {
            callback?.onConfirmed(searchAndDuckAiSelected)
            restoreOrientation()
            dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockOrientationToPortrait()
        this.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun revealContent() {
        binding.inputScreen.root.isVisible = true
        binding.inputScreen.inputScreenTitle.startOnboardingTypingAnimation(
            getString(context, R.string.preOnboardingInputScreenTitleUpdated),
        ) {
            contentFadeInAnimatorSet = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(
                        binding.inputScreen.inputScreenPicker,
                        View.ALPHA,
                        1f,
                    ).setDuration(CONTENT_FADE_IN_DURATION),
                    ObjectAnimator.ofFloat(
                        binding.inputScreen.inputScreenDescription,
                        View.ALPHA,
                        1f,
                    ).setDuration(CONTENT_FADE_IN_DURATION),
                    ObjectAnimator.ofFloat(binding.confirmButton, View.ALPHA, 1f)
                        .setDuration(CONTENT_FADE_IN_DURATION),
                )
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            binding.inputScreen.inputScreenPicker.startWithAiAnimation(delayedStart = true)
                        }
                    },
                )
                start()
            }
        }
    }

    private fun playLeftWingAnimation() {
        binding.leftWing.apply {
            alpha = 0f
            setMaxProgress(WING_STOP_PROGRESS)
            leftWingRunnable = postDelayed(WING_START_DELAY) {
                animate().alpha(1f).setDuration(WING_FADE_IN_DURATION).start()
                playAnimation()
            }
        }
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        contentFadeInAnimatorSet?.cancel()
        leftWingRunnable?.let { binding.leftWing.removeCallbacks(it) }
        binding.leftWing.cancelAnimation()
        binding.inputScreen.inputScreenPicker.cancelLottieAnimations()
        restoreOrientation()
    }

    private fun TypeAnimationTextView.startOnboardingTypingAnimation(
        text: String,
        afterAnimation: () -> Unit = {},
    ) {
        typingDelayInMs = TYPING_DELAY_MS
        delayAfterAnimationInMs = TYPING_POST_DELAY_MS
        startTypingAnimation(text, isCancellable = true, afterAnimation = afterAnimation)
    }

    private fun isWindowValid(): Boolean = (context as? Activity)?.let { activity ->
        !activity.isFinishing && !activity.isDestroyed && activity.window?.decorView?.isAttachedToWindow == true
    } ?: false

    private companion object {
        private const val WING_STOP_PROGRESS = 0.5f
        private const val WING_START_DELAY = 300L
        private const val WING_FADE_IN_DURATION = 150L
        private const val TYPING_DELAY_MS = 20L
        private const val TYPING_POST_DELAY_MS = 20L
        private const val CONTENT_FADE_IN_DURATION = 200L
    }
}
