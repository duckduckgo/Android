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

import android.content.Context
import android.support.v4.view.NestedScrollingChildHelper
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView

/**
 * NestedWebView which allows the WebView to hide the toolbar when placed in a CoordinatorLayout
 *
 * Based on https://github.com/takahirom/webview-in-coordinatorlayout
 */
class NestedWebView : WebView {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var lastY: Int = 0
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private var nestedOffsetY: Int = 0

    private val mChildHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)

    init {
        isNestedScrollingEnabled = true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        var returnValue = false

        val event = MotionEvent.obtain(ev)
        val action = ev.action
        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
        }
        val eventY = event.y.toInt()
        event.offsetLocation(0f, nestedOffsetY.toFloat())
        when (action) {
            MotionEvent.ACTION_MOVE -> {
                var deltaY = lastY - eventY
                // NestedPreScroll
                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset)) {
                    deltaY -= scrollConsumed[1]
                    lastY = eventY - scrollOffset[1]
                    event.offsetLocation(0f, (-scrollOffset[1]).toFloat())
                    nestedOffsetY += scrollOffset[1]
                }
                returnValue = super.onTouchEvent(event)

                // NestedScroll
                if (dispatchNestedScroll(0, scrollOffset[1], 0, deltaY, scrollOffset)) {
                    event.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                    lastY -= scrollOffset[1]
                }
            }
            MotionEvent.ACTION_DOWN -> {
                returnValue = super.onTouchEvent(event)
                lastY = eventY
                // start NestedScroll
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                returnValue = super.onTouchEvent(event)
                // end NestedScroll
                stopNestedScroll()
            }
        }
        return returnValue
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        mChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = mChildHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = mChildHelper.startNestedScroll(axes)

    override fun stopNestedScroll() {
        mChildHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean = mChildHelper.hasNestedScrollingParent()

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
                                      offsetInWindow: IntArray?): Boolean =
            mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean =
            mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
            mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
            mChildHelper.dispatchNestedPreFling(velocityX, velocityY)

}