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

package com.duckduckgo.app.browser.tabs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import androidx.transition.TransitionValues
import androidx.transition.Visibility
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Opening: the new tab grows into place from [NewTabMotion.startScale] at its offset while the tab underneath scales
 * back, slides left and dims. Closing ([isClosing]) plays the same motion backwards.
 */
class NewTabScaleTransition(
    private val motion: NewTabMotion,
    private val isClosing: Boolean,
) : Visibility() {

    init {
        duration = motion.durationMs
        interpolator = newTabInterpolator()
    }

    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator = if (isClosing) view.newTabCoverAnimator(motion, reverse = true) else animateForeground(view, reverse = false)

    override fun onDisappear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator = if (isClosing) animateForeground(view, reverse = true) else view.newTabCoverAnimator(motion, reverse = false)

    /** The tab growing in, or shrinking away when [reverse]. */
    private fun animateForeground(
        view: View,
        reverse: Boolean,
    ): Animator {
        val screenRadius = view.screenCornerRadius()
        val shadow = SHADOW_DP.toPx().toFloat()
        var cornerRadius = screenRadius / motion.startScale
        val originalOutlineProvider = view.outlineProvider
        val originalClipToOutline = view.clipToOutline
        var originalBackground: Drawable? = null
        val roundedOutline = object : ViewOutlineProvider() {
            override fun getOutline(
                view: View,
                outline: Outline,
            ) = outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
        }

        return progressAnimator(
            reverse,
            onStart = {
                view.outlineProvider = roundedOutline
                view.clipToOutline = true
                // A new tab's page content arrives asynchronously; without a background it would be see-through until then.
                originalBackground = view.background
                view.setBackgroundColor(view.context.getColorFromAttr(CommonR.attr.daxColorBackground))
            },
            onEnd = {
                view.resetTransform()
                view.translationZ = 0f
                view.outlineProvider = originalOutlineProvider
                view.clipToOutline = originalClipToOutline
                view.background = originalBackground
            },
        ) { progress ->
            view.scaleX = lerp(motion.startScale, 1f, progress)
            view.scaleY = view.scaleX
            view.translationX = lerp(motion.offsetXFraction * view.width, 0f, progress)
            view.translationY = lerp(motion.offsetYFraction * view.height, 0f, progress)
            view.alpha = (progress / FADE_IN_END).coerceAtMost(1f)
            // Kept above zero to the end so this tab draws over its sibling even when it was added before it.
            view.translationZ = RAISED_Z + lerp(shadow, 0f, progress)
            cornerRadius = lerp(screenRadius / motion.startScale, screenRadius, progress)
            view.invalidateOutline()
        }
    }

    private fun View.screenCornerRadius(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
            if (radius > 0) return radius.toFloat()
        }
        return DEFAULT_SCREEN_CORNER_RADIUS_DP.toPx().toFloat()
    }

    private companion object {
        const val FADE_IN_END = 0.3f
        const val SHADOW_DP = 24
        const val RAISED_Z = 1f
        const val DEFAULT_SCREEN_CORNER_RADIUS_DP = 16
    }
}

fun newTabInterpolator(): TimeInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

/**
 * The page being covered by a new tab: scales back, slides left and dims; or is uncovered when [reverse]. The caller
 * sets duration and interpolator (a transition does it for its animators).
 */
fun View.newTabCoverAnimator(
    motion: NewTabMotion,
    reverse: Boolean,
): ValueAnimator {
    val view = this
    val dim = ColorDrawable(Color.BLACK).apply { alpha = 0 }

    return progressAnimator(
        reverse,
        onStart = {
            dim.setBounds(0, 0, view.width, view.height)
            view.overlay.add(dim)
        },
        onEnd = {
            view.resetTransform()
            view.overlay.remove(dim)
        },
    ) { progress ->
        view.scaleX = lerp(1f, motion.backgroundScale, progress)
        view.scaleY = view.scaleX
        view.translationX = -motion.slideLeftFraction * view.width * progress
        dim.alpha = (motion.dimAlpha * (progress / DIM_IN_END).coerceAtMost(1f) * 255).toInt()
    }
}

// One eased 0..1 progress drives every property, so keyframes are fractions of the eased progress, as in the artifact.
// Setup waits for the start, so a transition cancelled before it runs never touches the view.
private fun progressAnimator(
    reverse: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    update: (Float) -> Unit,
): ValueAnimator {
    val animator = if (reverse) ValueAnimator.ofFloat(1f, 0f) else ValueAnimator.ofFloat(0f, 1f)
    animator.addListener(
        object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) = onStart()

            // Also runs after a cancel, so an interrupted transition leaves the view untouched.
            override fun onAnimationEnd(animation: Animator) = onEnd()
        },
    )
    animator.addUpdateListener { update(it.animatedValue as Float) }
    return animator
}

private fun View.resetTransform() {
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    alpha = 1f
}

private fun lerp(
    from: Float,
    to: Float,
    progress: Float,
): Float = from + (to - from) * progress

private const val DIM_IN_END = 0.35f
