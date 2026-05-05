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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout.LayoutParams
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import com.duckduckgo.duckchat.impl.R

class SearchInNewTabButtonView(context: Context) : AppCompatImageView(context) {

    private var widget: NativeInputWidget? = null

    init {
        val size = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.toolbarIcon)
        layoutParams = LayoutParams(size, size, Gravity.TOP)
        setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.selectable_item_rounded_corner_background)
        scaleType = ScaleType.CENTER_INSIDE
        setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_find_search_24)
        contentDescription = context.getString(R.string.duckAiInputScreenSearchInNewTabContentDescription)
        setOnClickListener { onClick() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        widget = findNativeInputWidget()
        viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusListener)
        updateVisibility()
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusListener)
        widget = null
        super.onDetachedFromWindow()
    }

    private fun onClick() {
        widget?.submitAsSearch()
    }

    private fun updateVisibility() {
        isVisible = widget?.hasInputFocus() == true
    }

    private val globalFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
        updateVisibility()
    }

    private fun findNativeInputWidget(): NativeInputWidget? {
        var node: View? = this
        while (node != null) {
            if (node is NativeInputWidget) return node
            node = node.parent as? View
        }
        return null
    }
}
