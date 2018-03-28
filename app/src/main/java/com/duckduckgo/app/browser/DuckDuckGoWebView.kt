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
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.support.v4.view.NestedScrollingChild
import android.support.v4.view.NestedScrollingChildHelper
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView

/**
 * WebView subclass which allows the WebView to
 *   - hide the toolbar when placed in a CoordinatorLayout
 *   - add the flag so that users' typing isn't used for personalisation
 *
 * Originally based on https://github.com/takahirom/webview-in-coordinatorlayout for scrolling behaviour
 */
class DuckDuckGoWebView : WebView, NestedScrollingChild {
    private var lastY: Int = 0
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private var nestedOffsetY: Int = 0
    private var nestedScrollHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    {
        isNestedScrollingEnabled = true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val inputConnection = super.onCreateInputConnection(outAttrs) ?: return null

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addNoPersonalisedFlag(outAttrs)
        }

        return inputConnection
    }


    @TargetApi(Build.VERSION_CODES.O)
    private fun addNoPersonalisedFlag(outAttrs: EditorInfo) {
        outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
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
            MotionEvent.ACTION_MOVE -> {
                var deltaY = lastY - eventY

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
            }

            MotionEvent.ACTION_DOWN -> {
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
}
