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

package com.duckduckgo.duckchat.impl.contextual.suggestions

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R as CommonR

class ContextualSuggestionsLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dots: List<View>
    private val animators = mutableListOf<Animator>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ContextCompat.getDrawable(context, CommonR.drawable.duck_ai_prompt_background)
        minimumHeight = HEIGHT_DP.toPx()
        setPadding(HORIZONTAL_PADDING_DP.toPx(), 0, HORIZONTAL_PADDING_DP.toPx(), 0)

        val dotColor = context.getColorFromAttr(CommonR.attr.daxColorSecondaryText)
        dots = (0 until DOT_COUNT).map { index ->
            View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(dotColor)
                }
                alpha = RESTING_ALPHA
                scaleX = RESTING_SCALE
                scaleY = RESTING_SCALE
                layoutParams = LayoutParams(DOT_SIZE_DP.toPx(), DOT_SIZE_DP.toPx()).apply {
                    if (index < DOT_COUNT - 1) marginEnd = DOT_SPACING_DP.toPx()
                }
            }
        }
        dots.forEach { addView(it) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimating()
    }

    override fun onDetachedFromWindow() {
        stopAnimating()
        super.onDetachedFromWindow()
    }

    private fun startAnimating() {
        stopAnimating()
        if (isReduceMotionEnabled()) {
            dots.forEach {
                it.alpha = 1f
                it.scaleX = 1f
                it.scaleY = 1f
            }
            return
        }
        dots.forEachIndexed { index, dot ->
            val animator = ObjectAnimator.ofPropertyValuesHolder(
                dot,
                PropertyValuesHolder.ofFloat(ALPHA, RESTING_ALPHA, 1f),
                PropertyValuesHolder.ofFloat(SCALE_X, RESTING_SCALE, 1f),
                PropertyValuesHolder.ofFloat(SCALE_Y, RESTING_SCALE, 1f),
            ).apply {
                duration = HALF_CYCLE_MS
                startDelay = index * STAGGER_MS
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
            animators.add(animator)
            animator.start()
        }
    }

    private fun stopAnimating() {
        animators.forEach { it.cancel() }
        animators.clear()
    }

    private fun isReduceMotionEnabled(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private companion object {
        const val DOT_COUNT = 3
        const val DOT_SIZE_DP = 7
        const val DOT_SPACING_DP = 5
        const val HORIZONTAL_PADDING_DP = 14
        const val HEIGHT_DP = 36
        const val RESTING_ALPHA = 0.3f
        const val RESTING_SCALE = 0.7f
        const val HALF_CYCLE_MS = 500L
        const val STAGGER_MS = 200L
    }
}
