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

package com.duckduckgo.mobile.android.ui.menu

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow

open class PopupMenu(
    layoutInflater: LayoutInflater,
    resourceId: Int,
    view: View = inflate(layoutInflater, resourceId),
    width: Int = WRAP_CONTENT,
    height: Int = WRAP_CONTENT
) : PopupWindow(view, width, height, true) {

    init {
        elevation = ELEVATION
        animationStyle = android.R.style.Animation_Dialog
    }

    fun onMenuItemClicked(
        menuView: View,
        onClick: () -> Unit
    ) {
        menuView.setOnClickListener {
            onClick()
            dismiss()
        }
    }

    fun show(
        rootView: View,
        anchorView: View
    ) {
        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)
        val x = MARGIN
        val y = anchorLocation[1] + MARGIN
        showAtLocation(rootView, Gravity.TOP or Gravity.END, x, y)
    }

    fun show(
        rootView: View,
        anchorView: View,
        onDismiss: () -> Unit
    ) {
        show(rootView, anchorView)
        setOnDismissListener(onDismiss)
    }

    fun showAnchoredToView(
        rootView: View,
        anchorView: View
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

        fun inflate(
            layoutInflater: LayoutInflater,
            resourceId: Int
        ): View {
            return layoutInflater.inflate(resourceId, null)
        }
    }
}
