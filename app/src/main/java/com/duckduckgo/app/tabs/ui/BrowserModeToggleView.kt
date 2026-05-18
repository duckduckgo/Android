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

package com.duckduckgo.app.tabs.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewBrowserModeToggleBinding
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.ui.view.toPx
import kotlin.math.abs

/**
 * Segmented pill toggle that lets the user switch between [BrowserMode.FIRE] (left)
 * and [BrowserMode.REGULAR] (right). The white "raised" selection indicator slides
 * between segments — either on tap, or by direct horizontal drag (snaps to nearest
 * side on release).
 *
 * Programmatic state updates via [setMode] / [setRegularTabCount] never fire the
 * change listener — only user interactions do.
 */
class BrowserModeToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewBrowserModeToggleBinding =
        ViewBrowserModeToggleBinding.inflate(LayoutInflater.from(context), this)

    private val segmentWidthPx: Int =
        resources.getDimensionPixelSize(R.dimen.browserModeToggleSegmentWidth)

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    private var listener: ((BrowserMode) -> Unit)? = null
    private var currentMode: BrowserMode? = null

    private var dragging = false
    private var initialTouchX = 0f
    private var initialIndicatorX = 0f

    init {
        setBackgroundResource(R.drawable.background_browser_mode_toggle)

        val pad = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_0)
        setPadding(pad, pad, pad, pad)
        clipChildren = false
        clipToPadding = false
        elevation = OUTER_ELEVATION_DP.toPx(context).toFloat()

        binding.fireSegment.setOnClickListener { dispatchUserSelection(BrowserMode.FIRE) }
        binding.regularSegment.setOnClickListener { dispatchUserSelection(BrowserMode.REGULAR) }
    }

    fun setMode(mode: BrowserMode) {
        if (mode == currentMode) return
        val previous = currentMode
        currentMode = mode
        animateIndicatorTo(mode, animated = previous != null)
    }

    fun setRegularTabCount(count: Int) {
        binding.regularTabCount.text = count.toString()
    }

    fun setOnModeChangedListener(listener: (BrowserMode) -> Unit) {
        this.listener = listener
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.x
                initialIndicatorX = binding.selectionIndicator.translationX
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(ev.x - initialTouchX) > touchSlop) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val candidate = initialIndicatorX + (ev.x - initialTouchX)
                binding.selectionIndicator.translationX =
                    candidate.coerceIn(0f, segmentWidthPx.toFloat())
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val finalX = binding.selectionIndicator.translationX
                val snappedMode =
                    if (finalX < segmentWidthPx / 2f) BrowserMode.FIRE else BrowserMode.REGULAR
                dragging = false

                if (snappedMode != currentMode) {
                    // Let the ViewModel publish the change; setMode arrives via the flow
                    // and finishes the animation. Don't pre-animate here — keeps a single
                    // source of truth and avoids a snap-and-jump if the switch is vetoed.
                    listener?.invoke(snappedMode)
                } else {
                    // No mode change — snap the indicator back to the current segment.
                    animateIndicatorTo(snappedMode, animated = true)
                }
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun animateIndicatorTo(mode: BrowserMode, animated: Boolean) {
        val targetX = if (mode == BrowserMode.REGULAR) segmentWidthPx.toFloat() else 0f
        if (animated) {
            binding.selectionIndicator.animate()
                .translationX(targetX)
                .setDuration(SLIDE_DURATION_MS)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            binding.selectionIndicator.translationX = targetX
        }
    }

    private fun dispatchUserSelection(mode: BrowserMode) {
        if (mode == currentMode) return
        listener?.invoke(mode)
    }

    private companion object {
        const val OUTER_ELEVATION_DP = 4
        const val SLIDE_DURATION_MS = 220L
    }
}
