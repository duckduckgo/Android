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

package com.duckduckgo.app.browser.pdf

import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.R as CommonR

class PdfDownloadTooltipPopup(
    context: Context,
    private val wavePosition: BubbleTooltipDrawable.WavePosition,
) {

    private val contentView: View = LayoutInflater.from(context)
        .inflate(R.layout.view_pdf_download_tooltip, null, false)

    private val rightInsetPx: Int = context.resources.getDimensionPixelSize(CommonR.dimen.keyline_1)

    private val popupWindow: PopupWindow = PopupWindow(
        contentView,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        isFocusable = false
        isOutsideTouchable = true
        setBackgroundDrawable(
            BubbleTooltipDrawable(
                backgroundColor = context.getColorFromAttr(CommonR.attr.daxColorSurface),
                wavePosition = wavePosition,
            ),
        )
        elevation = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            ELEVATION_DP,
            context.resources.displayMetrics,
        )
        contentView.setOnClickListener { dismiss() }
    }

    fun show(anchor: View) {
        if (popupWindow.isShowing) return

        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        contentView.measure(unspec, unspec)
        val drawablePadding = Rect()
        popupWindow.background?.getPadding(drawablePadding)
        val popupWidth = contentView.measuredWidth + drawablePadding.left + drawablePadding.right
        val popupHeight = contentView.measuredHeight + drawablePadding.top + drawablePadding.bottom

        // popupWindow.width must be set before showAsDropDown — Gravity.END's positioning math
        // breaks with WRAP_CONTENT and pushes the popup off-screen.
        popupWindow.width = popupWidth

        val yoff = if (wavePosition == BubbleTooltipDrawable.WavePosition.BOTTOM) {
            -(anchor.height + popupHeight)
        } else {
            0
        }
        popupWindow.showAsDropDown(anchor, -rightInsetPx, yoff, Gravity.END)
    }

    fun dismiss() {
        if (popupWindow.isShowing) popupWindow.dismiss()
    }

    private companion object {
        const val ELEVATION_DP = 4f
    }
}
