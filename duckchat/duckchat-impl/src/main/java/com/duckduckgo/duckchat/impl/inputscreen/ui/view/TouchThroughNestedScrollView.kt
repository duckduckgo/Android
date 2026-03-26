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

package com.duckduckgo.duckchat.impl.inputscreen.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.widget.NestedScrollView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class TouchThroughNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : NestedScrollView(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var viewPager: View? = null
    private var pendingDownEvent: MotionEvent? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isHorizontalSwipe = false
    private var isClick = false
    private var hasForwardedDown = false

    fun setViewPager(viewPager: View) {
        this.viewPager = viewPager
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleInterceptDown(ev)
            MotionEvent.ACTION_MOVE -> handleInterceptMove(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleInterceptUpOrCancel(ev)
            else -> false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isTouchOverContent(ev)) {
            return handleTouchOutsideContent(ev)
        }
        if (isHorizontalSwipe) {
            return handleHorizontalSwipe(ev)
        }
        if (isClick && ev.actionMasked == MotionEvent.ACTION_DOWN) {
            if (dispatchToChild(ev)) return true
        }
        return super.onTouchEvent(ev)
    }

    private fun handleInterceptDown(ev: MotionEvent): Boolean {
        resetTouchState(ev)
        pendingDownEvent = MotionEvent.obtain(ev)

        if (!isTouchOverContent(ev)) {
            viewPager?.let {
                setViewPagerInputEnabled(true)
                forwardTouchEvent(ev, it)
                hasForwardedDown = true
            }
            recyclePendingDownEvent()
            isHorizontalSwipe = true
            return true
        }

        isClick = true
        return false
    }

    private fun handleInterceptMove(ev: MotionEvent): Boolean {
        val dx = abs(ev.x - initialTouchX)
        val dy = abs(ev.y - initialTouchY)

        if (!isTouchOverContent(ev)) {
            return startHorizontalSwipe()
        }

        return when {
            dx > touchSlop && dx > dy && !isHorizontalSwipe -> beginHorizontalSwipe(ev)
            isHorizontalSwipe -> continueHorizontalSwipe(ev)
            dy > touchSlop && dy > dx -> beginVerticalSwipe(ev)
            else -> click()
        }
    }

    private fun startHorizontalSwipe(): Boolean {
        setViewPagerInputEnabled(true)
        isHorizontalSwipe = true
        recyclePendingDownEvent()
        return true
    }

    private fun beginHorizontalSwipe(ev: MotionEvent): Boolean {
        isHorizontalSwipe = true
        isClick = false
        setViewPagerInputEnabled(true)
        viewPager?.let { pager ->
            pendingDownEvent?.let { down ->
                forwardTouchEvent(down, pager)
                down.recycle()
                pendingDownEvent = null
            }
            forwardTouchEvent(ev, pager)
        }
        return true
    }

    private fun continueHorizontalSwipe(ev: MotionEvent): Boolean {
        viewPager?.let { forwardTouchEvent(ev, it) }
        return true
    }

    private fun beginVerticalSwipe(ev: MotionEvent): Boolean {
        recyclePendingDownEvent()
        return super.onInterceptTouchEvent(ev)
    }

    private fun click(): Boolean {
        isClick = true
        return false
    }

    private fun isTouchOverContent(ev: MotionEvent): Boolean {
        val child = getChildAt(0) ?: return false
        return ev.y + scrollY <= child.height
    }

    private fun handleTouchOutsideContent(ev: MotionEvent): Boolean {
        setViewPagerInputEnabled(true)
        viewPager?.let { pager ->
            if (ev.actionMasked != MotionEvent.ACTION_DOWN || !hasForwardedDown) {
                forwardTouchEvent(ev, pager)
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    hasForwardedDown = true
                }
            }
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            setViewPagerInputEnabled(false)
            isHorizontalSwipe = false
            hasForwardedDown = false
        }
        return true
    }

    private fun handleHorizontalSwipe(ev: MotionEvent): Boolean {
        viewPager?.let { forwardTouchEvent(ev, it) }
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            setViewPagerInputEnabled(false)
            isHorizontalSwipe = false
            isClick = false
        }
        return true
    }

    private fun handleInterceptUpOrCancel(ev: MotionEvent): Boolean {
        if (isHorizontalSwipe) {
            viewPager?.let { forwardTouchEvent(ev, it) }
        }
        resetSwipeState()
        return false
    }

    private fun dispatchToChild(ev: MotionEvent): Boolean {
        val child = getChildAt(0) ?: return false
        return MotionEvent.obtain(ev).run {
            offsetLocation(0f, -scrollY.toFloat())
            val handled = child.dispatchTouchEvent(this)
            recycle()
            handled
        }
    }

    private fun resetTouchState(ev: MotionEvent) {
        initialTouchX = ev.x
        initialTouchY = ev.y
        isHorizontalSwipe = false
        isClick = false
        hasForwardedDown = false
    }

    private fun resetSwipeState() {
        setViewPagerInputEnabled(false)
        isHorizontalSwipe = false
        isClick = false
        hasForwardedDown = false
        recyclePendingDownEvent()
    }

    private fun forwardTouchEvent(ev: MotionEvent, target: View) {
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        MotionEvent.obtain(ev).apply {
            setLocation(ev.rawX - location[0], ev.rawY - location[1])
            target.dispatchTouchEvent(this)
            recycle()
        }
    }

    private fun setViewPagerInputEnabled(enabled: Boolean) {
        (viewPager as? ViewPager2)?.apply {
            isUserInputEnabled = enabled
            isClickable = enabled
        }
    }

    private fun recyclePendingDownEvent() {
        pendingDownEvent?.recycle()
        pendingDownEvent = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        recyclePendingDownEvent()
    }
}
