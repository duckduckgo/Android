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

package com.duckduckgo.app.browser.duckchat

import android.animation.Animator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import com.duckduckgo.app.browser.databinding.ViewSwipeUpNewChatIndicatorBinding
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx

/**
 * The "New Chat" indicator shown when the user pulls up past the end of a Duck.ai chat.
 *
 * It sits behind the chat ([content]), which slides up as the user pulls and uncovers it. A ring fills as the pull
 * grows; once full, it turns into a compose button to show that letting go starts a new chat.
 */
class SwipeUpNewChatIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var content: View? = null

    private val binding = ViewSwipeUpNewChatIndicatorBinding.inflate(LayoutInflater.from(context), this)
    private val armOffsetPx = ARM_OFFSET_DP.toPx(context).toFloat()
    private val rubberBandLimitPx = RUBBER_BAND_LIMIT_DP.toPx(context).toFloat()
    private val spacingPx = INDICATOR_SPACING_DP.toPx(context).toFloat()
    private val revealedPx = spacingPx + CIRCLE_DIAMETER_DP.toPx(context)
    private val maxReleaseVelocityPx = MAX_RELEASE_VELOCITY_DP_PER_SEC.toPx(context).toFloat()
    private var offsetPx = 0f
    private var step = 0
    private var animator: Animator? = null
    private val restoreChat = Runnable { reset() }

    private val isArmed: Boolean get() = step == HAPTIC_STEPS

    init {
        // The compose button animation overshoots its size as it appears.
        clipChildren = false
        reset()
    }

    fun onPull(distancePx: Int) {
        if (distancePx == 0) {
            springBack()
            return
        }
        animator?.cancel()
        show()
        reveal(pullOffset(distancePx, rubberBandLimitPx))
        val ringProgress = ringProgress(offsetPx, armOffsetPx, revealedPx)
        binding.swipeUpNewChatColumn.alpha = (ringProgress / FADE_IN_PROGRESS).coerceAtMost(1f)
        binding.swipeUpNewChatRing.setProgressCompat((ringProgress * PROGRESS_MAX).toInt(), false)

        val wasArmed = isArmed
        val newStep = pullStep(offsetPx, armOffsetPx)
        if (newStep == HAPTIC_STEPS && !wasArmed) {
            performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
            )
        } else if (newStep in (step + 1) until HAPTIC_STEPS) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        step = newStep
        if (isArmed != wasArmed) showButton(isArmed)
    }

    /**
     * Starts a new chat through [onNewChat] if the pull reached the trigger and the finger let go deliberately,
     * otherwise puts the chat back.
     */
    fun onRelease(velocityPxPerSec: Float, onNewChat: () -> Unit) {
        if (startsNewChat(isArmed, velocityPxPerSec, maxReleaseVelocityPx)) commit(onNewChat) else springBack()
    }

    fun springBack() {
        val from = offsetPx
        val columnAlpha = binding.swipeUpNewChatColumn.alpha
        runAnimation(SPRING_BACK_DURATION_MS, DecelerateInterpolator(), onEnd = ::reset) { fraction ->
            reveal(from * (1 - fraction))
            binding.swipeUpNewChatColumn.alpha = columnAlpha * (1 - fraction)
        }
    }

    /** Puts the chat back immediately, mid-pull or mid-commit. */
    fun reset() {
        removeCallbacks(restoreChat)
        animator?.cancel()
        animator = null
        reveal(0f)
        content?.alpha = 1f
        step = 0
        binding.swipeUpNewChatColumn.alpha = 0f
        binding.swipeUpNewChatRing.setProgressCompat(0, false)
        binding.swipeUpNewChatButton.animate().cancel()
        binding.swipeUpNewChatButton.scaleX = COLLAPSED_SCALE
        binding.swipeUpNewChatButton.scaleY = COLLAPSED_SCALE
        gone()
    }

    /**
     * The chat goes up and fades, and stays hidden while the new tab takes a few frames to open over it: the
     * host calls [reset] once this tab is offscreen. If it never goes, the chat comes back on its own.
     */
    private fun commit(onNewChat: () -> Unit) {
        val from = offsetPx
        val columnAlpha = binding.swipeUpNewChatColumn.alpha
        runAnimation(
            COMMIT_DURATION_MS,
            AccelerateInterpolator(),
            onEnd = {
                onNewChat()
                postDelayed(restoreChat, RESTORE_TIMEOUT_MS)
            },
        ) { fraction ->
            content?.translationY = -(from + armOffsetPx * fraction)
            content?.alpha = 1 - fraction
            binding.swipeUpNewChatColumn.alpha = columnAlpha * (1 - fraction)
        }
    }

    private fun runAnimation(
        durationMs: Long,
        interpolator: TimeInterpolator,
        onEnd: () -> Unit,
        onUpdate: (fraction: Float) -> Unit,
    ) {
        animator?.cancel()
        var isCancelled = false
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            this.interpolator = interpolator
            addUpdateListener { onUpdate(it.animatedFraction) }
            doOnCancel { isCancelled = true }
            doOnEnd { if (!isCancelled) onEnd() }
            start()
        }
    }

    private fun reveal(px: Float) {
        offsetPx = px
        content?.translationY = -px
        val column = binding.swipeUpNewChatColumn
        column.translationY = columnTranslation(px, column.height.toFloat(), spacingPx)
    }

    private fun showButton(isShown: Boolean) {
        val scale = if (isShown) 1f else COLLAPSED_SCALE
        binding.swipeUpNewChatButton.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(ARM_DURATION_MS)
            .setInterpolator(if (isShown) OvershootInterpolator() else AccelerateInterpolator())
            .start()
    }

    companion object {
        // With the rubber band below, the chat slides this far after about 245dp of finger travel.
        private const val ARM_OFFSET_DP = 110
        private const val RUBBER_BAND_LIMIT_DP = 600
        private const val RUBBER_BAND_COEFFICIENT = 0.55f
        private const val CIRCLE_DIAMETER_DP = 48
        private const val INDICATOR_SPACING_DP = 4
        private const val HAPTIC_STEPS = 5
        private const val FADE_IN_PROGRESS = 0.3f
        private const val PROGRESS_MAX = 100
        private const val COLLAPSED_SCALE = 0f
        private const val SPRING_BACK_DURATION_MS = 350L
        private const val COMMIT_DURATION_MS = 200L
        private const val ARM_DURATION_MS = 250L
        private const val RESTORE_TIMEOUT_MS = 1000L

        // A finger still moving this fast as it lets go is flicking past the trigger, not choosing a new chat.
        private const val MAX_RELEASE_VELOCITY_DP_PER_SEC = 1000

        internal fun startsNewChat(isArmed: Boolean, velocityPxPerSec: Float, maxReleaseVelocityPx: Float): Boolean =
            isArmed && velocityPxPerSec < maxReleaseVelocityPx

        /**
         * How far the chat slides: an elastic rubber band, like scrolling past the end of a list. The chat lags the
         * finger from the start and each extra bit of pull moves it less, approaching [limitPx].
         */
        internal fun pullOffset(distancePx: Int, limitPx: Float): Float =
            limitPx * (1 - 1 / (RUBBER_BAND_COEFFICIENT * distancePx / limitPx + 1))

        /** The chat's slide in [HAPTIC_STEPS] steps toward [armOffsetPx]; the last one arms. */
        internal fun pullStep(offsetPx: Float, armOffsetPx: Float): Int =
            ((offsetPx / armOffsetPx).coerceIn(0f, 1f) * HAPTIC_STEPS).toInt()

        /** The ring only starts filling once the whole circle has come out from under the chat. */
        internal fun ringProgress(offsetPx: Float, armOffsetPx: Float, revealedPx: Float): Float =
            ((offsetPx - revealedPx) / (armOffsetPx - revealedPx)).coerceIn(0f, 1f)

        /** Centres the indicator in the gap, but never closer than [spacingPx] to the chat; any rest stays below the edge. */
        internal fun columnTranslation(offsetPx: Float, columnHeightPx: Float, spacingPx: Float): Float =
            maxOf(columnHeightPx - offsetPx + spacingPx, (columnHeightPx - offsetPx) / 2)
    }
}
