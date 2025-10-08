/*
 * Copyright (c) 2019 DuckDuckGo
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

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Canvas
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.cardview.widget.CardView
import androidx.core.animation.doOnEnd
import androidx.core.math.MathUtils
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.Mode
import kotlin.math.abs

class TabTouchHelper(
    private val numberGridColumns: Int,
    private val onTabSwiped: (Int) -> Unit,
    private val onTabMoved: (Int, Int) -> Unit,
    private val onTabDraggingStarted: () -> Unit,
    private val onTabDraggingFinished: () -> Unit,
) : ItemTouchHelper.SimpleCallback(
    /* dragDirs = */
    ItemTouchHelper.START or ItemTouchHelper.END or ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    /* swipeDirs = */
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
) {
    var mode: Mode = Mode.Normal

    override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
        onTabSwiped(viewHolder.bindingAdapterPosition)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val alpha = 1 - (abs(dX) / (recyclerView.width / numberGridColumns))
            viewHolder.itemView.alpha = MathUtils.clamp(alpha, 0f, 1f)
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        onTabMoved(from, to)
        return true
    }

    /*
     * Triggered by drag & drop events
     */
    override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ACTION_STATE_DRAG) {
            onTabDraggingStarted()

            // Change alpha and scale on drag
            (viewHolder?.itemView as? CardView)?.also {
                AnimatorSet().apply {
                    this.duration = ANIM_DURATION
                    this.interpolator = AccelerateDecelerateInterpolator()

                    playTogether(
                        getAlphaAnimator(it, ALPHA_DRAG_MIN),
                        getScaleXAnimator(it, SCALE_DRAG),
                        getScaleYAnimator(it, SCALE_DRAG),
                    )
                }.start()
            }
        }
    }

    /**
     * Triggered by drag end
     */
    override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        // Clear alpha, scale and elevation after drag/swipe
        (viewHolder.itemView as? CardView)?.also {
            AnimatorSet().apply {
                this.duration = ANIM_DURATION
                this.interpolator = AccelerateDecelerateInterpolator()

                playTogether(
                    getAlphaAnimator(it, ALPHA_DRAG_MAX),
                    getScaleXAnimator(it, SCALE_NORMAL),
                    getScaleYAnimator(it, SCALE_NORMAL),
                )

                doOnEnd { _ ->
                    onTabDraggingFinished()
                }
            }.start()
        }
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: ViewHolder,
    ): Int {
        if (viewHolder.isTabAnimatedTabViewHolder() || mode is Mode.Selection) {
            return 0
        }
        return super.getMovementFlags(recyclerView, viewHolder)
    }

    fun onLayoutTypeChanged(layoutType: LayoutType) {
        when (layoutType) {
            LayoutType.GRID -> setDefaultDragDirs(ItemTouchHelper.START or ItemTouchHelper.END or ItemTouchHelper.UP or ItemTouchHelper.DOWN)
            LayoutType.LIST -> setDefaultDragDirs(ItemTouchHelper.UP or ItemTouchHelper.DOWN)
        }
    }

    private fun getAlphaAnimator(view: View, alphaTo: Float): Animator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, alphaTo)
    }

    private fun getScaleXAnimator(view: View, scaleTo: Float): Animator {
        return ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, scaleTo)
    }

    private fun getScaleYAnimator(view: View, scaleTo: Float): Animator {
        return ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, scaleTo)
    }

    private fun ViewHolder?.isTabAnimatedTabViewHolder(): Boolean =
        this is TabSwitcherAdapter.TabSwitcherViewHolder.TrackerAnimationInfoPanelViewHolder

    companion object {
        private const val ANIM_DURATION = 100L

        private const val ALPHA_DRAG_MIN = 0.7f
        private const val ALPHA_DRAG_MAX = 1f

        private const val SCALE_DRAG = 0.8f
        private const val SCALE_NORMAL = 1f
    }
}
