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

package com.duckduckgo.app.browser.progressbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.isVisible
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr

class PageLoadProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val config = ProgressBarConfig()

    private val timeProvider = object : TimeProvider {
        override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    }

    private val engine = ProgressPhaseEngine(config, timeProvider)

    private val barColor = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue)

    private val shimmerRenderer = ShimmerRenderer(
        config = config,
        density = resources.displayMetrics.density,
        shimmerColor = lighten(barColor, shimmerLightenFraction(config)),
    )

    private val progressPaint = Paint().apply {
        color = barColor
        style = Paint.Style.FILL
    }

    private val barHeightPx = 1.5f * resources.displayMetrics.density

    private fun shimmerLightenFraction(config: ProgressBarConfig): Float {
        val isDark = (context as? DuckDuckGoActivity)?.isDarkThemeEnabled() ?: false
        return if (isDark) config.shimmerLightenFractionDark else config.shimmerLightenFractionLight
    }

    private var lastFrameTimeNanos = 0L
    private var _isStarted = false
    private var isDismissing = false
    private var lastReportedProgress = 0f

    /**
     * True from [start] until the completion fade-out finishes.
     * Prevents stale progress updates from restarting the animation.
     */
    val isStarted: Boolean get() = _isStarted

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val dtSeconds = if (lastFrameTimeNanos == 0L) {
                0.016f
            } else {
                ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
            }
            lastFrameTimeNanos = frameTimeNanos

            val state = engine.tick(dtSeconds)

            invalidate()

            if (state.phase == Phase.DONE) {
                onDone()
            } else if (state.shouldInvalidate) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun start() {
        val wasVisible = isDismissing || isVisible
        if (isDismissing) {
            animate().cancel()
            isDismissing = false
        }
        engine.reset()
        engine.start()
        _isStarted = true

        lastReportedProgress = 0f
        shimmerRenderer.start(SystemClock.elapsedRealtime())
        if (wasVisible) {
            // Bar still on screen from previous load — continue seamlessly
            alpha = 1f
        } else {
            alpha = 0f
            animate().alpha(1f).setDuration(config.fadeInDuration).start()
        }
        visibility = VISIBLE
        lastFrameTimeNanos = 0L
        // Ensure we don't have multiple frame callbacks queued if start() is called multiple times in quick succession
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun onProgressUpdate(progress: Float) {
        if (progress < lastReportedProgress) {
            // Progress went backward — new navigation, restart fresh
            start()
        }
        lastReportedProgress = progress
        engine.onProgressUpdate(progress)
    }

    fun triggerCompletion() {
        engine.triggerCompletion()
    }

    fun reset() {
        _isStarted = false
        isDismissing = false

        animate().cancel()
        engine.reset()
        shimmerRenderer.stop()
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        lastFrameTimeNanos = 0L
        visibility = INVISIBLE
        alpha = 1f
    }

    private fun onDone() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        isDismissing = true
        animate()
            .alpha(0f)
            .setDuration(config.fadeOutDuration)
            .withEndAction {
                if (isDismissing) {
                    _isStarted = false
                    isDismissing = false
                    engine.reset()
                    shimmerRenderer.stop()
                    visibility = INVISIBLE
                    alpha = 1f
                    lastFrameTimeNanos = 0L
                }
            }
            .start()
    }

    override fun onDraw(canvas: Canvas) {
        val state = engine.frameState
        if (state.displayProgress <= 0f) return

        val progressWidth = (state.displayProgress / 100f) * width
        val cornerRadius = barHeightPx / 2f
        val top = height - barHeightPx
        // Draw round rect starting off-screen left so left corners are clipped by the view bounds
        canvas.drawRoundRect(-cornerRadius, top, progressWidth, height.toFloat(), cornerRadius, cornerRadius, progressPaint)

        if (shimmerRenderer.isActive) {
            shimmerRenderer.draw(canvas, progressWidth, top, height.toFloat(), SystemClock.elapsedRealtime())
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    companion object {
        /** Blend [color] toward white by [fraction] (0 = original, 1 = white). */
        private fun lighten(color: Int, fraction: Float): Int = Color.rgb(
            (color.red + (255 - color.red) * fraction).toInt(),
            (color.green + (255 - color.green) * fraction).toInt(),
            (color.blue + (255 - color.blue) * fraction).toInt(),
        )
    }
}
