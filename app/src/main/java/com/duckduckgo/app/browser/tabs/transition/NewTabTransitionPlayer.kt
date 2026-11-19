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

package com.duckduckgo.app.browser.tabs.transition

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.RoundedCorner
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Plays a new tab's transition: [cover] shows a picture of the page being covered, and the live tab container grows in
 * over it. The cover must sit behind the container.
 */
class NewTabTransitionPlayer(
    private val cover: ImageView,
    private val state: NewTabTransitionState,
) {

    private var active: Active? = null
    private var animator: TimeAnimator? = null
    private val dim = Color.BLACK.toDrawable()
    private val handler = Handler(Looper.getMainLooper())
    private val coverTimeout = Runnable { cleanUp() }

    init {
        cover.scaleType = ImageView.ScaleType.MATRIX
    }

    /**
     * Shows [picture] in place of [container] until [play] runs, giving up after [COVER_TIMEOUT_MS]. [onEnd] runs when the
     * cover ends: replaced, cancelled or timed out.
     */
    fun cover(
        container: View,
        picture: Bitmap,
        onEnd: () -> Unit,
    ) {
        cleanUp(publishIdle = false)
        if (!matchesWindow(picture)) {
            state.setRunning(false)
            onEnd()
            return
        }
        active = Active(container, onEnd, onResized = { cleanUp() })
        showCover(picture)
        container.alpha = 0f
        state.setRunning(true)
        handler.postDelayed(coverTimeout, COVER_TIMEOUT_MS)
    }

    /** Grows [container] in over [picture]; cleans up any running transition or cover first. [onEnd] runs after end or cancel. */
    fun play(
        container: View,
        picture: Bitmap,
        motion: NewTabMotion,
        onEnd: () -> Unit = {},
    ) {
        // A transition replacing another, we don't want to publish idle in between.
        cleanUp(publishIdle = false)
        // With phone animations off (including Battery Saver) there's nothing to animate. A zero scale would also end the clock
        // inside its own start.
        if (!matchesWindow(picture) || !ValueAnimator.areAnimatorsEnabled()) {
            state.setRunning(false)
            onEnd()
            return
        }
        active = Active(container, onEnd, onResized = { cleanUp() })
        showCover(picture)
        dim.setBounds(0, 0, picture.width, picture.height)
        cover.overlay.add(dim)

        val screenRadius = container.screenCornerRadius()
        val shadow = SHADOW_DP.toPx().toFloat()
        var cornerRadius = screenRadius / motion.startScale
        container.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(
                view: View,
                outline: Outline,
            ) = outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
        }
        container.clipToOutline = true
        container.setBackgroundColor(container.context.getColorFromAttr(CommonR.attr.daxColorBackground))

        val interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        val applyFrame = { fraction: Float ->
            val progress = interpolator.getInterpolation(fraction)
            val incoming = motion.incomingFrame(progress)
            container.scaleX = incoming.scale
            container.scaleY = incoming.scale
            container.translationX = incoming.translationXFraction * container.width
            container.translationY = incoming.translationYFraction * container.height
            container.alpha = incoming.alpha
            container.translationZ = incoming.shadowFraction * shadow
            cornerRadius = screenRadius * incoming.cornerRadiusFactor
            container.invalidateOutline()

            val outgoing = motion.outgoingFrame(progress, timeFraction = fraction)
            cover.scaleX = outgoing.scale
            cover.scaleY = outgoing.scale
            cover.translationX = outgoing.translationXFraction * cover.width
            dim.alpha = (outgoing.dimAlpha * 255).toInt()
        }
        applyFrame(0f)
        state.setRunning(true)

        val scaledDurationMs = motion.durationMs * animatorDurationScale()
        var elapsedMs = 0f
        // Store the clock before starting it: start() can run the first tick right away, and if that tick ends the transition,
        // the cleanup needs to find this clock to stop it.
        val clock = TimeAnimator().also { animator = it }
        clock.apply {
            setTimeListener { _, _, deltaMs ->
                // At most one frame per tick: a freeze pauses the animation instead of making it jump ahead.
                elapsedMs += deltaMs.coerceAtMost(MAX_FRAME_STEP_MS).toFloat()
                val fraction = (elapsedMs / scaledDurationMs).coerceAtMost(1f)
                applyFrame(fraction)
                if (fraction == 1f) cleanUp()
            }
            start()
        }
    }

    /** Ends any transition or cover immediately and restores the container. */
    fun cancel() = cleanUp()

    private fun cleanUp(publishIdle: Boolean = true) {
        handler.removeCallbacks(coverTimeout)
        animator?.let {
            animator = null
            it.removeAllListeners()
            it.removeAllUpdateListeners()
            it.cancel()
        }
        val active = active ?: return
        this.active = null
        active.restore()
        cover.overlay.remove(dim)
        cover.visibility = View.GONE
        cover.setImageDrawable(null)
        cover.scaleX = 1f
        cover.scaleY = 1f
        cover.translationX = 0f
        if (publishIdle) state.setRunning(false)
        active.onEnd()
    }

    private fun showCover(picture: Bitmap) {
        // The picture is in window coordinates, and the cover fills its parent: shift the picture so it lines up with
        // what was on screen, wherever the parent sits in the window.
        val parent = cover.parent as View
        val parentInWindow = IntArray(2).also { parent.getLocationInWindow(it) }
        cover.imageMatrix = Matrix().apply {
            setTranslate(-(parentInWindow[0] + parent.paddingLeft).toFloat(), -(parentInWindow[1] + parent.paddingTop).toFloat())
        }
        cover.setImageBitmap(picture)
        cover.visibility = View.VISIBLE
    }

    // A picture taken before a rotation no longer lines up with the window.
    private fun matchesWindow(picture: Bitmap): Boolean {
        val window = cover.rootView
        return picture.width == window.width && picture.height == window.height
    }

    private fun animatorDurationScale(): Float =
        if (Build.VERSION.SDK_INT >= 33) {
            ValueAnimator.getDurationScale()
        } else {
            Settings.Global.getFloat(cover.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }

    private fun View.screenCornerRadius(): Float {
        if (Build.VERSION.SDK_INT >= 31) {
            val radius = rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
            if (radius > 0) return radius.toFloat()
        }
        return DEFAULT_SCREEN_CORNER_RADIUS_DP.toPx().toFloat()
    }

    private class Active(
        val container: View,
        val onEnd: () -> Unit,
        onResized: () -> Unit,
    ) {
        private val background: Drawable? = container.background
        private val outlineProvider: ViewOutlineProvider? = container.outlineProvider
        private val clipToOutline = container.clipToOutline

        // A rotation or window resize mid-transition leaves the picture out of line with the window, so it ends at once.
        private val resizeListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) onResized()
        }

        init {
            container.addOnLayoutChangeListener(resizeListener)
        }

        fun restore() {
            container.removeOnLayoutChangeListener(resizeListener)
            container.scaleX = 1f
            container.scaleY = 1f
            container.translationX = 0f
            container.translationY = 0f
            container.translationZ = 0f
            container.alpha = 1f
            container.outlineProvider = outlineProvider
            container.clipToOutline = clipToOutline
            container.background = background
        }
    }

    private companion object {
        const val COVER_TIMEOUT_MS = 1_000L
        const val MAX_FRAME_STEP_MS = 20L
        const val SHADOW_DP = 24
        const val DEFAULT_SCREEN_CORNER_RADIUS_DP = 16
    }
}
