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
import android.util.AttributeSet
import android.view.MotionEvent
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
    private var lastClampedTopY: Boolean = true // when created we are always at the top
    private var contentAllowsSwipeToRefresh: Boolean = true
    private var enableSwipeRefreshCallback: ((Boolean) -> Unit)? = null
    private var hasGestureFinished = true
    private var canSwipeToRefresh = true

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
        var returnValue = false

        val event = MotionEvent.obtain(ev)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
        }
        val eventY = event.y.toInt()
        event.offsetLocation(0f, nestedOffsetY.toFloat())

        when (action) {
            MotionEvent.ACTION_UP -> {
                hasGestureFinished = true
                returnValue = super.onTouchEvent(event)
                stopNestedScroll()
            }
            MotionEvent.ACTION_MOVE -> {
                var deltaY = lastY - eventY

                lastClampedTopY = deltaY <= 0

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

                if (canSwipeToRefresh && scrollY == 0 && lastClampedTopY && nestedOffsetY == 0) {
                    // we are on a new gesture, have reached the top, are clamped vertically and nestedScrollY is done too -> enable swipeRefresh (by default always disabled)
                    enableSwipeRefresh(true)
                }

                lastDeltaY = deltaY
            }

            MotionEvent.ACTION_DOWN -> {
                hasGestureFinished = false
                // disable swipeRefresh until we can be sure it should be enabled
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
        // taking into account lastDeltaY since we are only interested whether we clamped at the top
        lastClampedTopY = clampedY && lastDeltaY <= 0

        if (!lastClampedTopY) {
            canSwipeToRefresh = false // disable because user scrolled down so we need a new gesture
        }

        if (lastClampedTopY && hasGestureFinished) {
            canSwipeToRefresh = true // only enable if at the top and gestured finished
        }

        enableSwipeRefresh(canSwipeToRefresh && clampedY && scrollY == 0 && (lastDeltaY <= 0 || nestedOffsetY == 0))
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
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
        enableSwipeRefreshCallback?.invoke(enable && contentAllowsSwipeToRefresh)
    }

    private fun setContentAllowsSwipeToRefresh(allowed: Boolean) {
        contentAllowsSwipeToRefresh = allowed
        if (!allowed) {
            enableSwipeRefresh(false)
        }
    }

    companion object {

        /*
         * Taken from EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
         * We can't use that value directly as it was only added on Oreo, but we can apply the value anyway.
         */
        private const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000
    }
}
