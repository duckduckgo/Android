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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.databinding.ViewBrandDesignInputScreenPickerBinding
import com.duckduckgo.mobile.android.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BrandDesignInputScreenPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /**
     * - [NONE]: snap drawables; do not start the Lottie. Used for the initial paint when the host
     *   plans to call [startWithAiAnimation] later (e.g. after a fade-in completes).
     * - [ANIMATE]: snap drawables; start the Lottie loop immediately.
     * - [CROSSFADE_ANIMATE]: crossfade through the selection states; the Lottie keeps playing from
     *   the current progress so the transition feels continuous.
     */
    enum class Transition { NONE, ANIMATE, CROSSFADE_ANIMATE }

    private val binding: ViewBrandDesignInputScreenPickerBinding =
        ViewBrandDesignInputScreenPickerBinding.inflate(LayoutInflater.from(context), this)

    private var lottieRepeatJob: Job? = null

    private var currentWithAi: Boolean = true
    private var selectionListener: ((Boolean) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        binding.inputScreenSearchOnlyContainer.setOnClickListener { onOptionClicked(withAi = false) }
        binding.inputScreenWithAiContainer.setOnClickListener { onOptionClicked(withAi = true) }
    }

    fun setSelection(withAi: Boolean, transition: Transition = Transition.NONE) {
        currentWithAi = withAi
        val isLight = isLightMode()
        val withoutAiImageRes = withoutAiImageRes(withAi, isLight)
        val withAiAnimationRes = withAiAnimationRes(withAi, isLight)

        when (transition) {
            Transition.NONE -> {
                binding.inputScreenSearchOnlyImageFront.setImageResource(withoutAiImageRes)
                binding.inputScreenWithAiAnimationFront.setAnimation(withAiAnimationRes)
            }
            Transition.ANIMATE -> {
                binding.inputScreenSearchOnlyImageFront.setImageResource(withoutAiImageRes)
                binding.inputScreenWithAiAnimationFront.setAnimation(withAiAnimationRes)
                playWithAiLottie(binding.inputScreenWithAiAnimationFront)
            }
            Transition.CROSSFADE_ANIMATE -> {
                crossfadeStaticImage(
                    front = binding.inputScreenSearchOnlyImageFront,
                    back = binding.inputScreenSearchOnlyImageBack,
                    newRes = withoutAiImageRes,
                )
                crossfadeLottie(
                    front = binding.inputScreenWithAiAnimationFront,
                    back = binding.inputScreenWithAiAnimationBack,
                    newRes = withAiAnimationRes,
                )
            }
        }

        binding.inputScreenSearchOnlyCheck.isChecked = !withAi
        binding.inputScreenWithAiCheck.isChecked = withAi
    }

    fun setOnSelectionChangedListener(listener: (Boolean) -> Unit) {
        selectionListener = listener
    }

    fun startWithAiAnimation(delayedStart: Boolean = false) {
        playWithAiLottie(binding.inputScreenWithAiAnimationFront, delayedStart = delayedStart)
    }

    fun cancelLottieAnimations() {
        lottieRepeatJob?.cancel()
        lottieRepeatJob = null
        binding.inputScreenWithAiAnimationFront.apply {
            removeAllAnimatorListeners()
            cancelAnimation()
        }
        binding.inputScreenWithAiAnimationBack.apply {
            removeAllAnimatorListeners()
            cancelAnimation()
        }
    }

    private fun onOptionClicked(withAi: Boolean) {
        if (currentWithAi != withAi) {
            setSelection(withAi, Transition.CROSSFADE_ANIMATE)
        }
        selectionListener?.invoke(withAi)
    }

    private fun playWithAiLottie(
        view: LottieAnimationView,
        fromProgress: Float? = null,
        delayedStart: Boolean = false,
    ) {
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        lottieRepeatJob?.cancel()
        view.removeAllAnimatorListeners()
        view.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                val replayScope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
                lottieRepeatJob = replayScope.launch {
                    delay(LOTTIE_REPEAT_DELAY)
                    view.playAnimation()
                }
            }
        })
        if (fromProgress != null) {
            view.progress = fromProgress
        }
        lottieRepeatJob = scope.launch {
            if (delayedStart) {
                delay(LOTTIE_INITIAL_DELAY)
            }
            if (fromProgress != null) view.resumeAnimation() else view.playAnimation()
        }
    }

    private fun crossfadeStaticImage(front: ImageView, back: ImageView, newRes: Int) {
        back.setImageDrawable(front.drawable)
        front.setImageResource(newRes)
        crossfadeAlpha(front, back)
    }

    private fun crossfadeLottie(front: LottieAnimationView, back: LottieAnimationView, newRes: Int) {
        val currentProgress = front.progress
        front.composition?.let { back.setComposition(it) }
        back.removeAllAnimatorListeners()
        back.progress = currentProgress
        back.resumeAnimation()
        front.setAnimation(newRes)
        playWithAiLottie(front, fromProgress = currentProgress)
        crossfadeAlpha(front, back)
    }

    private fun crossfadeAlpha(front: View, back: View) {
        front.animate().cancel()
        back.animate().cancel()
        back.alpha = 1f
        front.alpha = 0f
        front.animate().alpha(1f).setDuration(CROSSFADE_DURATION).setListener(null)
        back.animate().alpha(0f).setDuration(CROSSFADE_DURATION).setListener(null)
    }

    private fun isLightMode(): Boolean {
        val nightFlag = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightFlag != Configuration.UI_MODE_NIGHT_YES
    }

    private fun withoutAiImageRes(withAi: Boolean, isLight: Boolean): Int = when {
        withAi && isLight -> R.drawable.searchbox_withoutai_inactive_brand_design_update
        withAi -> R.drawable.searchbox_withoutai_inactive_dark_brand_design_update
        isLight -> R.drawable.searchbox_withoutai_active_brand_design_update
        else -> R.drawable.searchbox_withoutai_active_dark_brand_design_update
    }

    private fun withAiAnimationRes(withAi: Boolean, isLight: Boolean): Int = when {
        withAi && isLight -> R.raw.searchbox_with_ai_active
        withAi -> R.raw.searchbox_with_ai_active_dark
        isLight -> R.raw.searchbox_with_ai_inactive
        else -> R.raw.searchbox_with_ai_inactive_dark
    }

    companion object {
        private const val CROSSFADE_DURATION = 200L
        private const val LOTTIE_REPEAT_DELAY = 2000L
        private const val LOTTIE_INITIAL_DELAY = 2000L
    }
}
