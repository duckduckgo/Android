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

package com.duckduckgo.common.ui.view

import android.graphics.Rect
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.duckduckgo.common.ui.isRebrandAreaEnabled
import com.duckduckgo.mobile.android.R
import com.google.android.material.card.MaterialCardView

fun MaterialCardView.applyRmfCardStyle(close: ImageView) {
    if (!context.isRebrandAreaEnabled(R.attr.daxRebrandRmfCards)) return
    setCardBackgroundColor(context.getColorFromAttr(R.attr.daxColorSurfaceSecondary))
    val content = getChildAt(0) as ViewGroup
    val padding = resources.getDimensionPixelSize(R.dimen.keyline_5)
    content.setPadding(padding, padding, padding, padding)
    content.clipToPadding = false
    close.applyRmfCloseButtonStyle()
}

fun ImageView.applyRmfCloseButtonStyle() {
    if (!context.isRebrandAreaEnabled(R.attr.daxRebrandRmfCards)) return
    val container = parent as ViewGroup
    val inset = resources.getDimensionPixelSize(R.dimen.keyline_4)
    setImageResource(R.drawable.ic_close_24_small)
    scaleType = ImageView.ScaleType.CENTER
    setPadding(0, 0, 0, 0)
    setBackgroundResource(R.drawable.rmf_message_close_background)
    contentDescription = context.getString(R.string.closeButtonContentDescription)
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    updateLayoutParams<ConstraintLayout.LayoutParams> {
        width = resources.getDimensionPixelSize(R.dimen.rmfMessageCloseButtonSize)
        height = width
        topMargin = inset - container.paddingTop
        marginEnd = inset - container.paddingEnd
        startToStart = ConstraintLayout.LayoutParams.UNSET
        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    }
    container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val target = Rect()
        getHitRect(target)
        val expansion = (resources.getDimensionPixelSize(R.dimen.keyline_7) - width) / 2
        target.inset(-expansion, -expansion)
        container.touchDelegate = TouchDelegate(target, this)
    }
}
