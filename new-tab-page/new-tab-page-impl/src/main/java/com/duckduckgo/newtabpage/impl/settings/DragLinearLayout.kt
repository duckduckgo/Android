/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.newtabpage.impl.settings

import android.animation.*
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.core.view.MotionEventCompat
import kotlin.math.abs
import kotlin.math.roundToInt
import logcat.LogPriority.ERROR
import logcat.logcat

class DragLinearLayout @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) :
    LinearLayout(context, attrs) {

    companion object {
        private val TAG = DragLinearLayout::class.java.simpleName
        private const val NOMINAL_SWITCH_DURATION: Long = 150
        private const val MIN_SWITCH_DURATION = NOMINAL_SWITCH_DURATION
        private const val MAX_SWITCH_DURATION = NOMINAL_SWITCH_DURATION * 2
        private const val NOMINAL_DISTANCE = 20f
        private const val INVALID_POINTER_ID = -1
        private fun getBitmapFromView(view: View): Bitmap {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            return bitmap
        }
    }

    private val mNominalDistanceScaled: Float
    private val mDragItem: DragItem
    private val mSlop: Int
    private var mDownY = -1
    private var mDownX = -1
    private var mActivePointerId = INVALID_POINTER_ID
    private var mLayoutTransition: LayoutTransition? = null
    private val mDraggableChildren: SparseArray<DraggableChild> = SparseArray()
    private var mIsLongClickDraggable = false
    private var mClickToDragListener: ILongClickToDragListener? = null
    private var mIsEnterLongClick = false
    private val mLongClickDragListener = LongClickDragListener()
    private var swapListener: OnViewSwapListener? = null

    init {
        mDragItem = DragItem()
        val vc = ViewConfiguration.get(this.context)
        mSlop = vc.scaledTouchSlop
        val resources = resources
        mNominalDistanceScaled = (NOMINAL_DISTANCE * resources.displayMetrics.density + 0.5f).roundToInt().toFloat()
    }

    fun addDragView(
        child: View?,
        dragHandle: View?,
    ) {
        addView(child)
        setViewDraggable(child, dragHandle)
    }

    fun setViewDraggable(
        child: View?,
        dragHandle: View?,
    ) {
        require(!(null == child || null == dragHandle)) { "Draggable children and their drag handles must not be null." }
        if (this === child!!.parent) {
            dragHandle!!.setOnTouchListener(DragHandleOnTouchListener(child!!))
            dragHandle!!.setOnLongClickListener(mLongClickDragListener)
            mDraggableChildren.put(indexOfChild(child), DraggableChild())
        } else {
            logcat(tag = TAG, priority = ERROR) { "$child is not a child, cannot make draggable" }
        }
    }

    override fun removeAllViews() {
        val count = childCount
        for (i in 0 until count) {
            getChildAt(i).setOnLongClickListener(null)
            getChildAt(i).setOnTouchListener(null)
        }
        super.removeAllViews()
        mDraggableChildren.clear()
    }

    private fun getTranslateAnimationDuration(distance: Float): Long {
        return Math.min(
            MAX_SWITCH_DURATION,
            Math.max(
                MIN_SWITCH_DURATION,
                (NOMINAL_SWITCH_DURATION * Math.abs(distance) / mNominalDistanceScaled).toLong(),
            ),
        )
    }

    fun startDetectingDrag(child: View) {
        if (mDragItem.mDetecting) return
        val position = indexOfChild(child)
        if (position >= 0) {
            mDraggableChildren[position].endExistingAnimation()
            mDragItem.startDetectingOnPossibleDrag(child, position)
        }
    }

    private fun startDrag() {
        swapListener?.onStartDrag()

        mLayoutTransition = layoutTransition
        if (mLayoutTransition != null) {
            layoutTransition = null
        }
        mDragItem.onDragStart()
        requestDisallowInterceptTouchEvent(true)
    }

    private fun onDragStop() {
        if (orientation == VERTICAL) {
            mDragItem.mSettleAnimation = ValueAnimator.ofFloat(
                mDragItem.mTotalDragOffset.toFloat(),
                mDragItem.mTotalDragOffset - mDragItem.mTargetTopOffset.toFloat(),
            )
                .setDuration(getTranslateAnimationDuration(mDragItem.mTargetTopOffset.toFloat()))
        } else {
            mDragItem.mSettleAnimation = ValueAnimator.ofFloat(
                mDragItem.mTotalDragOffset.toFloat(),
                mDragItem.mTotalDragOffset - mDragItem.mTargetLeftOffset.toFloat(),
            )
                .setDuration(getTranslateAnimationDuration(mDragItem.mTargetLeftOffset.toFloat()))
        }
        mDragItem.mSettleAnimation?.addUpdateListener(
            AnimatorUpdateListener { animation ->
                if (!mDragItem.mDetecting) return@AnimatorUpdateListener
                mDragItem.setTotalOffset((animation.animatedValue as Float).toInt())
                invalidate()
            },
        )
        mDragItem.mSettleAnimation?.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    mDragItem.onDragStop()
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!mDragItem.mDetecting) {
                        return
                    }

                    swapListener?.onEndDrag()

                    mDragItem.mSettleAnimation = null
                    mDragItem.stopDetecting()
                    if (mLayoutTransition != null && layoutTransition == null) {
                        layoutTransition = mLayoutTransition
                    }
                }
            },
        )
        mDragItem.mSettleAnimation?.start()
    }

    private fun onDrag(offset: Int) {
        if (orientation == VERTICAL) {
            mDragItem.setTotalOffset(offset)
            invalidate()
            val currentTop = mDragItem.mStartTop + mDragItem.mTotalDragOffset
            val belowPosition = nextDraggablePosition(mDragItem.mPosition)
            val abovePosition = previousDraggablePosition(mDragItem.mPosition)
            val belowView = getChildAt(belowPosition)
            val aboveView = getChildAt(abovePosition)
            val isBelow = belowView != null &&
                currentTop + mDragItem.mHeight > belowView.top + belowView.height / 2
            val isAbove = aboveView != null &&
                currentTop < aboveView.top + aboveView.height / 2
            if (isBelow || isAbove) {
                val switchView = if (isBelow) belowView else aboveView
                val originalPosition = mDragItem.mPosition
                val switchPosition = if (isBelow) belowPosition else abovePosition
                mDraggableChildren[switchPosition].cancelExistingAnimation()
                val switchViewStartY = switchView!!.y

                swapListener?.onSwap(mDragItem.mView, mDragItem.mPosition, switchView, switchPosition)

                if (isBelow) {
                    removeViewAt(originalPosition)
                    removeViewAt(switchPosition - 1)
                    addView(belowView, originalPosition)
                    addView(mDragItem.mView, switchPosition)
                } else {
                    removeViewAt(switchPosition)
                    removeViewAt(originalPosition - 1)
                    addView(mDragItem.mView, switchPosition)
                    addView(aboveView, originalPosition)
                }
                mDragItem.mPosition = switchPosition
                val switchViewObserver = switchView.viewTreeObserver
                switchViewObserver.addOnPreDrawListener(
                    object :
                        ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            switchViewObserver.removeOnPreDrawListener(this)
                            val switchAnimator = ObjectAnimator.ofFloat(
                                switchView,
                                "y",
                                switchViewStartY,
                                switchView.top.toFloat(),
                            )
                                .setDuration(getTranslateAnimationDuration(switchView.top - switchViewStartY))
                            switchAnimator.addListener(
                                object : AnimatorListenerAdapter() {
                                    override fun onAnimationStart(animation: Animator) {
                                        mDraggableChildren[originalPosition].mValueAnimator = switchAnimator
                                    }

                                    override fun onAnimationEnd(animation: Animator) {
                                        mDraggableChildren[originalPosition].mValueAnimator = null
                                    }
                                },
                            )
                            switchAnimator.start()
                            return true
                        }
                    },
                )
                val observer = mDragItem.mView!!.viewTreeObserver
                observer.addOnPreDrawListener(
                    object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            observer.removeOnPreDrawListener(this)
                            mDragItem.updateTargetLocation()
                            if (mDragItem.settling()) {
                                logcat(tag = TAG) { "Updating settle animation" }
                                mDragItem.mSettleAnimation!!.removeAllListeners()
                                mDragItem.mSettleAnimation!!.cancel()
                                onDragStop()
                            }
                            return true
                        }
                    },
                )
            }
        } else {
            mDragItem.setTotalOffset(offset)
            invalidate()
            val currentLeft = mDragItem.mStartLeft + mDragItem.mTotalDragOffset
            val nextPosition = nextDraggablePosition(mDragItem.mPosition)
            val prePosition = previousDraggablePosition(mDragItem.mPosition)
            val nextView = getChildAt(nextPosition)
            val preView = getChildAt(prePosition)
            val isToNext = nextView != null &&
                currentLeft + mDragItem.mWidth > nextView.left + nextView.width / 2
            val isToPre = preView != null &&
                currentLeft < preView.left + preView.width / 2
            if (isToNext || isToPre) {
                val switchView = if (isToNext) nextView else preView
                val originalPosition = mDragItem.mPosition
                val switchPosition = if (isToNext) nextPosition else prePosition
                mDraggableChildren[switchPosition].cancelExistingAnimation()
                val switchViewStartX = switchView!!.x

                swapListener?.onSwap(mDragItem.mView, mDragItem.mPosition, switchView, switchPosition)

                if (isToNext) {
                    removeViewAt(originalPosition)
                    removeViewAt(switchPosition - 1)
                    addView(nextView, originalPosition)
                    addView(mDragItem.mView, switchPosition)
                } else {
                    removeViewAt(switchPosition)
                    removeViewAt(originalPosition - 1)
                    addView(mDragItem.mView, switchPosition)
                    addView(preView, originalPosition)
                }
                mDragItem.mPosition = switchPosition
                val switchViewObserver = switchView.viewTreeObserver
                switchViewObserver.addOnPreDrawListener(
                    object :
                        ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            switchViewObserver.removeOnPreDrawListener(this)
                            val switchAnimator = ObjectAnimator.ofFloat(
                                switchView,
                                "x",
                                switchViewStartX,
                                switchView.left.toFloat(),
                            )
                                .setDuration(getTranslateAnimationDuration(switchView.left - switchViewStartX))
                            switchAnimator.addListener(
                                object : AnimatorListenerAdapter() {
                                    override fun onAnimationStart(animation: Animator) {
                                        mDraggableChildren[originalPosition].mValueAnimator = switchAnimator
                                    }

                                    override fun onAnimationEnd(animation: Animator) {
                                        mDraggableChildren[originalPosition].mValueAnimator = null
                                    }
                                },
                            )
                            switchAnimator.start()
                            return true
                        }
                    },
                )
                val observer = mDragItem.mView!!.viewTreeObserver
                observer.addOnPreDrawListener(
                    object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            observer.removeOnPreDrawListener(this)
                            mDragItem.updateTargetLocation()
                            if (mDragItem.settling()) {
                                mDragItem.mSettleAnimation!!.removeAllListeners()
                                mDragItem.mSettleAnimation!!.cancel()
                                onDragStop()
                            }
                            return true
                        }
                    },
                )
            }
        }
    }

    private fun previousDraggablePosition(position: Int): Int {
        val startIndex = mDraggableChildren.indexOfKey(position)
        return if (startIndex < 1 || startIndex > mDraggableChildren.size()) {
            -1
        } else {
            mDraggableChildren.keyAt(
                startIndex - 1,
            )
        }
    }

    private fun nextDraggablePosition(position: Int): Int {
        val startIndex = mDraggableChildren.indexOfKey(position)
        return if (startIndex < -1 || startIndex > mDraggableChildren.size() - 2) {
            -1
        } else {
            mDraggableChildren.keyAt(
                startIndex + 1,
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (mDragItem.mDetecting && (mDragItem.mDragging || mDragItem.settling())) {
            canvas.save()
            if (orientation == VERTICAL) {
                canvas.translate(0f, mDragItem.mTotalDragOffset.toFloat())
            } else {
                canvas.translate(mDragItem.mTotalDragOffset.toFloat(), 0f)
            }
            mDragItem.mBitmapDrawable!!.draw(canvas)
            canvas.restore()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!mIsLongClickDraggable) {
            return super.onInterceptTouchEvent(event)
        }
        when (MotionEventCompat.getActionMasked(event)) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                if (mDragItem.mDetecting) return false
                mDownY = MotionEventCompat.getY(event, 0).toInt()
                mDownX = MotionEventCompat.getX(event, 0).toInt()
                mActivePointerId = MotionEventCompat.getPointerId(event, 0)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!mIsLongClickDraggable) {
                    return super.onInterceptTouchEvent(event)
                }

                if (!mDragItem.mDetecting) return false
                if (INVALID_POINTER_ID == mActivePointerId) {
                    return false
                }

                val pointerIndex = event.findPointerIndex(mActivePointerId)
                val y = MotionEventCompat.getY(event, pointerIndex)
                val x = MotionEventCompat.getX(event, pointerIndex)
                val dy = y - mDownY
                val dx = x - mDownX
                if (orientation == VERTICAL) {
                    if (abs(dy) > mSlop) {
                        startDrag()
                        return true
                    }
                } else {
                    if (abs(dx) > mSlop) {
                        startDrag()
                        return true
                    }
                }
                return false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                run {
                    val pointerIndex = MotionEventCompat.getActionIndex(event)
                    val pointerId = MotionEventCompat.getPointerId(event, pointerIndex)
                    if (pointerId != mActivePointerId) {
                        return false
                    }
                }
                run {
                    parent.requestDisallowInterceptTouchEvent(false)
                    onTouchEnd()
                    if (mDragItem.mDetecting) mDragItem.stopDetecting()
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                onTouchEnd()
                if (mDragItem.mDetecting) mDragItem.stopDetecting()
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mIsLongClickDraggable) {
            return super.onTouchEvent(event)
        }
        when (MotionEventCompat.getActionMasked(event)) {
            MotionEvent.ACTION_DOWN -> {
                if (!mDragItem.mDetecting || mDragItem.settling()) return false
                startDrag()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!mIsEnterLongClick) {
                    return super.onTouchEvent(event)
                }
                if (!mDragItem.mDragging) {
                    return false
                }

                if (INVALID_POINTER_ID == mActivePointerId) {
                    return false
                }

                val pointerIndex = event.findPointerIndex(mActivePointerId)
                val lastEventY = MotionEventCompat.getY(event, pointerIndex)
                    .toInt()
                val lastEventX = MotionEventCompat.getX(event, pointerIndex)
                    .toInt()
                if (orientation == VERTICAL) {
                    val deltaY = lastEventY - mDownY
                    onDrag(deltaY)
                } else {
                    val deltaX = lastEventX - mDownX
                    onDrag(deltaX)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                run {
                    val pointerIndex = MotionEventCompat.getActionIndex(event)
                    val pointerId = MotionEventCompat.getPointerId(event, pointerIndex)
                    if (pointerId != mActivePointerId) {
                        return false
                    }
                }
                run {
                    onTouchEnd()
                    if (mDragItem.mDragging) {
                        onDragStop()
                    } else if (mDragItem.mDetecting) {
                        mDragItem.stopDetecting()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                onTouchEnd()
                if (mDragItem.mDragging) {
                    onDragStop()
                } else if (mDragItem.mDetecting) {
                    mDragItem.stopDetecting()
                }
                return true
            }
        }

        return false
    }

    private fun onTouchEnd() {
        mDownY = -1
        mDownX = -1
        mIsEnterLongClick = false
        mActivePointerId = INVALID_POINTER_ID
    }

    private fun getDragDrawable(view: View): BitmapDrawable {
        val top = view.top
        val left = view.left
        val bitmap = getBitmapFromView(view)
        val drawable = BitmapDrawable(resources, bitmap)
        drawable.bounds = Rect(left, top, left + view.width, top + view.height)
        return drawable
    }

    fun setClickToDragListener(clickToDragListener: ILongClickToDragListener?) {
        mClickToDragListener = clickToDragListener
    }

    fun setLongClickDrag(longClickDrag: Boolean) {
        if (mIsLongClickDraggable != longClickDrag) {
            mIsLongClickDraggable = longClickDrag
        }
    }

    fun setViewSwapListener(swapListener: OnViewSwapListener) {
        this.swapListener = swapListener
    }

    private inner class DragItem internal constructor() {
        var mView: View? = null
        private var mStartVisibility = 0
        var mBitmapDrawable: BitmapDrawable? = null
        var mPosition = 0
        var mStartTop = 0
        var mHeight = 0
        var mTotalDragOffset = 0
        var mTargetTopOffset = 0
        var mStartLeft = 0
        var mWidth = 0
        var mTargetLeftOffset = 0
        var mSettleAnimation: ValueAnimator? = null
        var mDetecting = false
        var mDragging = false
        fun startDetectingOnPossibleDrag(
            view: View,
            position: Int,
        ) {
            mView = view
            mStartVisibility = view.visibility
            mBitmapDrawable = getDragDrawable(view)
            mPosition = position
            mStartTop = view.top
            mHeight = view.height
            mStartLeft = view.left
            mWidth = view.width
            mTotalDragOffset = 0
            mTargetTopOffset = 0
            mTargetLeftOffset = 0
            mSettleAnimation = null
            mDetecting = true
        }

        fun onDragStart() {
            mView!!.visibility = INVISIBLE
            mDragging = true
        }

        fun setTotalOffset(offset: Int) {
            mTotalDragOffset = offset
            updateTargetLocation()
        }

        fun updateTargetLocation() {
            if (orientation == VERTICAL) {
                updateTargetTop()
            } else {
                updateTargetLeft()
            }
        }

        private fun updateTargetLeft() {
            mTargetLeftOffset = mStartLeft - mView!!.left + mTotalDragOffset
        }

        private fun updateTargetTop() {
            mTargetTopOffset = mStartTop - mView!!.top + mTotalDragOffset
        }

        fun onDragStop() {
            mDragging = false
        }

        fun settling(): Boolean {
            return null != mSettleAnimation
        }

        fun stopDetecting() {
            mDetecting = false
            if (null != mView) mView!!.visibility = mStartVisibility
            mView = null
            mStartVisibility = -1
            mBitmapDrawable = null
            mPosition = -1
            mStartTop = -1
            mHeight = -1
            mStartLeft = -1
            mWidth = -1
            mTotalDragOffset = 0
            mTargetTopOffset = 0
            mTargetLeftOffset = 0
            if (null != mSettleAnimation) mSettleAnimation!!.end()
            mSettleAnimation = null
        }

        init {
            stopDetecting()
        }
    }

    private inner class DraggableChild {
        var mValueAnimator: ValueAnimator? = null
        fun endExistingAnimation() {
            mValueAnimator?.end()
        }

        fun cancelExistingAnimation() {
            mValueAnimator?.cancel()
        }
    }

    private inner class DragHandleOnTouchListener internal constructor(private val view: View) :
        OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(
            v: View,
            event: MotionEvent,
        ): Boolean {
            if (MotionEvent.ACTION_DOWN == MotionEventCompat.getActionMasked(event)) {
                startDetectingDrag(view)
            }
            return false
        }
    }

    inner class LongClickDragListener : OnLongClickListener {
        override fun onLongClick(v: View): Boolean {
            if (!mIsLongClickDraggable) {
                return false
            }
            mIsEnterLongClick = true
            if (mClickToDragListener != null) {
                mClickToDragListener!!.onLongClickToDrag(v)
            }
            startDetectingDrag(v)
            return true
        }
    }

    interface ILongClickToDragListener {
        fun onLongClickToDrag(dragableView: View?)
    }

    interface OnViewSwapListener {
        fun onStartDrag() {}
        fun onEndDrag() {}

        /**
         * Invoked right before the two items are swapped due to a drag event.
         * After the swap, the firstView will be in the secondPosition, and vice versa.
         *
         *
         * No guarantee is made as to which of the two has a lesser/greater position.
         */
        fun onSwap(
            firstView: View?,
            firstPosition: Int,
            secondView: View?,
            secondPosition: Int,
        ) {
        }
    }
}
