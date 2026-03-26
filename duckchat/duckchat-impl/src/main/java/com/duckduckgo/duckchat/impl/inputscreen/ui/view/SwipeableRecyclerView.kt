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
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class SwipeableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    private var viewPager: ViewPager2? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var isHorizontalSwipe = false
    private var hasSentDownEvent = false

    fun setViewPager(viewPager: ViewPager2?) {
        this.viewPager = viewPager
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> resetSwipeState(e)
            MotionEvent.ACTION_MOVE -> if (detectHorizontalSwipe(e)) return true
        }
        return super.onInterceptTouchEvent(e)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pager = viewPager ?: return super.onTouchEvent(e)

        if (e.action == MotionEvent.ACTION_MOVE && !isHorizontalSwipe) {
            detectHorizontalSwipe(e)
        }

        if (!isHorizontalSwipe) return super.onTouchEvent(e)

        dispatchDownEvent(e, pager)
        pager.dispatchTouchEvent(e)
        resetOnGestureEnd(e)

        return true
    }

    private fun dispatchDownEvent(e: MotionEvent, pager: ViewPager2) {
        if (!hasSentDownEvent) {
            dispatchSyntheticDownEvent(e, pager)
            hasSentDownEvent = true
        }
    }

    private fun resetOnGestureEnd(e: MotionEvent) {
        if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
            isHorizontalSwipe = false
            hasSentDownEvent = false
        }
    }

    private fun resetSwipeState(e: MotionEvent) {
        startX = e.x
        startY = e.y
        isHorizontalSwipe = false
        hasSentDownEvent = false
    }

    private fun detectHorizontalSwipe(e: MotionEvent): Boolean {
        val dx = abs(e.x - startX)
        val dy = abs(e.y - startY)

        if (dx > touchSlop && dx > dy && !isHorizontalSwipe) {
            isHorizontalSwipe = true
            viewPager?.isUserInputEnabled = true
            return true
        }
        return false
    }

    private fun dispatchSyntheticDownEvent(e: MotionEvent, pager: ViewPager2) {
        MotionEvent.obtain(e.downTime, e.eventTime, MotionEvent.ACTION_DOWN, e.x, e.y, e.metaState).also {
            pager.dispatchTouchEvent(it)
            it.recycle()
        }
    }
}
