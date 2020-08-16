/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat

/**
 * WebView subclass which allows the WebView to
 *   - hide the toolbar when placed in a CoordinatorLayout
 *   - add the flag so that users' typing isn't used for personalisation
 *
 * Originally based on https://github.com/takahirom/webview-in-coordinatorlayout for scrolling behaviour
 */
class DuckDuckGoWebView : WebView, NestedScrollingChild {
    private var swipeRefreshHandler: Handler = Handler()
    private var enableSwipeRefreshRunnable: Runnable = Runnable {
        enableSwipeRefresh(true)
    }
    private var lastClampedTopYTimestamp: Long? = null
    private var lastClampedTopY: Boolean = false
    private var contentAllowsSwipeToRefresh: Boolean = true
    private var enableSwipeRefreshCallback: ((Boolean) -> Unit)? = null

    private var lastY: Int = 0
    private var lastDeltaY: Int = 0
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private var nestedOffsetY: Int = 0
    private var nestedScrollHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        isNestedScrollingEnabled = true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val inputConnection = super.onCreateInputConnection(outAttrs) ?: return null

        addNoPersonalisedFlag(outAttrs)

        return inputConnection
    }

    private fun addNoPersonalisedFlag(outAttrs: EditorInfo) {
        outAttrs.imeOptions = outAttrs.imeOptions or IME_FLAG_NO_PERSONALIZED_LEARNING
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val returnValue: Boolean

        val event = MotionEvent.obtain(ev)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
        }
        val eventY = event.y.toInt()
        event.offsetLocation(0f, nestedOffsetY.toFloat())

        when (action) {
            MotionEvent.ACTION_MOVE -> {
                var deltaY = lastY - eventY

                if (deltaY > 0) {
                    lastClampedTopY = false
                }

                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset)) {
                    deltaY -= scrollConsumed[1]
                    lastY = eventY - scrollOffset[1]
                    event.offsetLocation(0f, (-scrollOffset[1]).toFloat())
                    nestedOffsetY += scrollOffset[1]
                }

                returnValue = super.onTouchEvent(event)

                if (dispatchNestedScroll(0, scrollOffset[1], 0, deltaY, scrollOffset)) {
                    event.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                    lastY -= scrollOffset[1]
                }

                if (scrollY == 0 && lastClampedTopY && nestedOffsetY == 0 && isOverScrollGlowEffectReceded()) {
                    // we have reached the top, are clamped vertically and nestedScrollY is done too -> enable swipeRefresh (by default always disabled on ACTION_DOWN)
                    enableSwipeRefresh(true)
                }

                lastDeltaY = deltaY
            }

            MotionEvent.ACTION_DOWN -> {
                // disable swipeRefresh until we can be sure it should be enabled - this is required to avoid https://github.com/duckduckgo/Android/pull/750#pullrequestreview-383768114
                enableSwipeRefresh(false)

                returnValue = super.onTouchEvent(event)
                lastY = eventY
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
            }

            else -> {
                returnValue = super.onTouchEvent(event)
                stopNestedScroll()
            }
        }

        return returnValue
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        nestedScrollHelper.isNestedScrollingEnabled = enabled
    }

    override fun stopNestedScroll() {
        nestedScrollHelper.stopNestedScroll()
    }

    override fun isNestedScrollingEnabled(): Boolean = nestedScrollHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = nestedScrollHelper.startNestedScroll(axes)

    override fun hasNestedScrollingParent(): Boolean = nestedScrollHelper.hasNestedScrollingParent()

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?): Boolean =
        nestedScrollHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean =
        nestedScrollHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        nestedScrollHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        nestedScrollHelper.dispatchNestedPreFling(velocityX, velocityY)

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        lastClampedTopY = scrollY == 0 && clampedY

        // if we reach this swipeRefresh must be disabled
        if (lastClampedTopY) {
            // schedule enable after over scroll effect has receded
            scheduleEnableSwipeRefresh()
        }

        lastClampedTopYTimestamp = if (lastClampedTopY) {
            System.currentTimeMillis()
        } else {
            null
        }

        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }

    private fun scheduleEnableSwipeRefresh() {
        swipeRefreshHandler.removeCallbacks(enableSwipeRefreshRunnable)
        swipeRefreshHandler.postDelayed(enableSwipeRefreshRunnable, OVER_SCROLL_EDGE_EFFECT_RECEDE_TIME)
    }

    private fun isOverScrollGlowEffectReceded(): Boolean {
        return lastClampedTopYTimestamp != null && System.currentTimeMillis() - OVER_SCROLL_EDGE_EFFECT_RECEDE_TIME >= lastClampedTopYTimestamp!!
    }

    fun setEnableSwipeRefreshCallback(callback: (Boolean) -> Unit) {
        enableSwipeRefreshCallback = callback
    }

    /**
     * Allows us to determine whether to (de)activate Swipe to Refresh behavior for the current page content, e.g. if page implements a swipe behavior of its
     * own already (see twitter.com).
     */
    fun detectOverscrollBehavior() {
        evaluateJavascript("(function() { return getComputedStyle(document.querySelector('body')).overscrollBehaviorY; })();") { behavior ->
            setContentAllowsSwipeToRefresh(behavior.replace("\"", "") == "auto")
        }
    }

    private fun enableSwipeRefresh(enable: Boolean) {
        overScrollMode = if (!enable) {
            swipeRefreshHandler.removeCallbacks(enableSwipeRefreshRunnable)
            View.OVER_SCROLL_ALWAYS
        } else {
            View.OVER_SCROLL_NEVER
        }

        enableSwipeRefreshCallback?.invoke(enable && contentAllowsSwipeToRefresh)
    }

    private fun setContentAllowsSwipeToRefresh(allowed: Boolean) {
        contentAllowsSwipeToRefresh = allowed
        if (!allowed) {
            enableSwipeRefresh(false)
        }
    }

    override fun destroy() {
        swipeRefreshHandler.removeCallbacks(enableSwipeRefreshRunnable)
        super.destroy()
    }

    companion object {

        /*
         * Taken from EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
         * We can't use that value directly as it was only added on Oreo, but we can apply the value anyway.
         */
        private const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000

        /*
        * Taken from EdgeEffect.RECEDE_TIME
         */
        private const val OVER_SCROLL_EDGE_EFFECT_RECEDE_TIME = 600L
    }
}
