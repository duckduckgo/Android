/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.common.ui.menu

import android.app.Activity
import android.content.Context
import android.os.Build.VERSION
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import com.duckduckgo.common.ui.view.PopupMenuItemView
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toDp
import com.duckduckgo.mobile.android.R

open class PopupMenu(
    layoutInflater: LayoutInflater,
    resourceId: Int,
    view: View = inflate(layoutInflater, resourceId),
    width: Int = getPopupWidth(view.context),
    height: Int = WRAP_CONTENT,
) : PopupWindow(view, width, height, true) {

    init {
        elevation = ELEVATION
        animationStyle = android.R.style.Animation_Dialog
        applyRoundedRippleCorners()
    }

    private fun applyRoundedRippleCorners() {
        (contentView as? ViewGroup)?.let { content ->
            for (i in 0 until content.childCount) {
                val childLabel = (content.getChildAt(i) as? PopupMenuItemView)
                    ?.findViewById<DaxTextView>(R.id.label)

                when {
                    content.childCount == 1 -> R.drawable.ripple_rectangle_rounded
                    i == 0 -> R.drawable.ripple_top_rounded
                    i == content.childCount - 1 -> R.drawable.ripple_bottom_rounded
                    else -> null
                }?.let { backgroundResource ->
                    childLabel?.setBackgroundResource(backgroundResource)
                }
            }
        }
    }

    fun onMenuItemClicked(
        menuView: View,
        onClick: () -> Unit,
    ) {
        menuView.setOnClickListener {
            onClick()
            dismiss()
        }
    }

    fun onMenuItemLongClicked(
        menuView: View,
        onClick: () -> Unit,
    ) {
        menuView.setOnLongClickListener {
            onClick()
            dismiss()
            true
        }
    }

    fun show(
        rootView: View,
        anchorView: View,
    ) {
        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)
        val x = MARGIN
        val y = anchorLocation[1] + MARGIN
        showAtLocation(rootView, Gravity.TOP or Gravity.END, x, y)
    }

    /**
     * Shows an overflow menu and computes the gravity values based on the position of the [anchorView] on the screen.
     *
     * If the anchor is in the top half of the screen, the overflow menu will appear below it and expand towards the bottom.
     * If it's in the bottom half of the screen, the overflow menu will appear above it and expand towards the top.
     *
     * If the anchor is more to the start of the screen, the overflow menu will also expand from the start of the screen.
     * If it's more towards the end of the screen, so will the overflow menu.
     */
    fun showAnchoredView(
        activity: Activity,
        rootView: View,
        anchorView: View,
    ) {
        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)
        val x = MARGIN
        val screenWidth = if (VERSION.SDK_INT >= 30) {
            activity.windowManager.currentWindowMetrics.bounds.width()
        } else {
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.heightPixels
        }
        val screenHeight = if (VERSION.SDK_INT >= 30) {
            activity.windowManager.currentWindowMetrics.bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.heightPixels
        }
        val screenVerticalMidpoint = screenHeight / 2
        val screenHorizontalMidpoint = screenWidth / 2

        val anchorWidth = anchorView.width
        val anchorHorizontalMidpoint = anchorLocation[0] + (anchorWidth / 2)
        val anchorMoreToStartOfScreen: Boolean = anchorHorizontalMidpoint < screenHorizontalMidpoint
        val anchorMoreToTopOfScreen: Boolean = anchorLocation[1] < screenVerticalMidpoint

        val horizontalGravity = if (anchorMoreToStartOfScreen) Gravity.START else Gravity.END
        val verticalGravity = if (anchorMoreToTopOfScreen) Gravity.TOP else Gravity.BOTTOM

        val gravity = verticalGravity or horizontalGravity

        val y = if (anchorMoreToTopOfScreen) {
            anchorLocation[1] + anchorView.height + 4.toDp()
        } else {
            screenHeight - anchorLocation[1] + 4.toDp()
        }

        showAtLocation(rootView, gravity, x, y)
    }

    fun show(
        rootView: View,
        anchorView: View,
        onDismiss: () -> Unit,
    ) {
        show(rootView, anchorView)
        setOnDismissListener(onDismiss)
    }

    fun showAnchoredToView(
        rootView: View,
        anchorView: View,
    ) {
        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)
        val x = anchorLocation[0] + MARGIN
        val y = anchorLocation[1] + MARGIN
        showAtLocation(rootView, Gravity.NO_GRAVITY, x, y)
    }

    companion object {

        private const val MARGIN = 16
        private const val ELEVATION = 6f

        const val POPUP_DEFAULT_ELEVATION_DP = 4f

        fun inflate(
            layoutInflater: LayoutInflater,
            resourceId: Int,
        ): View {
            return layoutInflater.inflate(resourceId, null)
        }

        fun getPopupWidth(context: Context): Int = context.resources.getDimensionPixelSize(R.dimen.popupMenuWidth)
    }
}
