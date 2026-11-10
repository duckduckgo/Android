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

package com.duckduckgo.common.ui.view.dialog

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.doOnAttach
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.duckduckgo.common.ui.isRebrandAreaEnabled
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.button.DaxButtonGhost
import com.duckduckgo.common.ui.view.button.DaxButtonSecondary
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R
import com.google.android.material.R as MaterialR

fun ViewGroup.applyRebrandBottomSheetSpacing(
    header: View,
    title: View,
    messages: List<View> = emptyList(),
    buttons: View? = null,
    secondary: List<DaxButton> = emptyList(),
    close: View? = null,
    content: ViewGroup = this,
    sections: List<ViewGroup> = emptyList(),
    edgeToEdgeHeader: Boolean = false,
    fullHeight: Boolean = false,
) {
    if (!context.isRebrandAreaEnabled(R.attr.daxRebrandBottomSheets)) return
    val minSide = resources.getDimensionPixelSize(R.dimen.keyline_6)
    val tabletSide = resources.getDimensionPixelSize(R.dimen.rebrandSheetTabletSide)
    val closeTarget = resources.getDimensionPixelSize(R.dimen.keyline_7)
    val closeInset = resources.getDimensionPixelSize(R.dimen.keyline_2)
    val titleGap = resources.getDimensionPixelSize(R.dimen.keyline_5)
    val messageGap = resources.getDimensionPixelSize(R.dimen.keyline_3)
    val headerTopPadding = 30.toPx(context)
    // The 6dp button inset brings this view-edge spacing to a visible 24dp gap.
    val buttonInsetAdjustedSpacing = 18.toPx(context)
    val surface = ColorStateList.valueOf(context.getColorFromAttr(R.attr.daxColorSurfaceTertiary))
    doOnAttach { view ->
        // OnShow listeners can replace the sheet drawable to restore rounded corners.
        view.post {
            generateSequence(view.parent as? View) { it.parent as? View }
                .firstOrNull { it.id == com.google.android.material.R.id.design_bottom_sheet }
                ?.backgroundTintList = surface
        }
    }
    (sequenceOf(this, content) + generateSequence(parent as? View) { it.parent as? View })
        .firstOrNull { it.background != null }
        ?.backgroundTintList = surface
    if (content !== this) {
        setPadding(0)
        content.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = 0
            bottomMargin = 0
            marginStart = 0
            marginEnd = 0
            leftMargin = 0
            rightMargin = 0
            if (this is ConstraintLayout.LayoutParams) {
                width = 0
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                matchConstraintMaxWidth = 0
                verticalBias = 0f
            }
        }
    }
    val topPadding = when {
        fullHeight -> (close!!.parent as View).layoutParams.height + (title.parent as View).paddingTop
        close != null && !edgeToEdgeHeader && close.parent !is LinearLayout -> closeTarget
        else -> headerTopPadding
    }
    content.setPadding(minSide, topPadding, minSide, buttonInsetAdjustedSpacing)
    addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right - left == oldRight - oldLeft) return@addOnLayoutChangeListener
        post {
            generateSequence(parent as? View) { it.parent as? View }
                .firstOrNull { it.id == MaterialR.id.design_bottom_sheet }
                ?.let { sheet ->
                    val insets = ViewCompat.getRootWindowInsets(sheet)
                        ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                        ?: return@let
                    val location = IntArray(2)
                    sheet.getLocationInWindow(location)
                    val rightGap = sheet.rootView.width - location[0] - sheet.width
                    // Reserve only the system-bar region that overlaps this sheet.
                    sheet.updatePadding(
                        left = (insets.left - location[0]).coerceAtLeast(0),
                        right = (insets.right - rightGap).coerceAtLeast(0),
                    )
                }
            val side = if (resources.configuration.smallestScreenWidthDp >= 600) tabletSide else minSide
            content.updatePadding(left = side, right = side)
            if (edgeToEdgeHeader) {
                header.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginStart = -side
                    marginEnd = -side
                    leftMargin = -side
                    rightMargin = -side
                }
            }
            close?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginEnd = closeInset - (close.parent as View).paddingEnd
                rightMargin = marginEnd
            }
        }
    }
    content.clipToPadding = false
    sections.forEach { it.setPadding(0) }
    (listOf(content) + sections).forEach { section ->
        section.children.filter {
            it !== close && (it.layoutParams.width == 0 || it.layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT)
        }.forEach { child ->
            child.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = 0
                marginEnd = 0
                leftMargin = 0
                rightMargin = 0
                bottomMargin = 0
                if (this is ConstraintLayout.LayoutParams) {
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    startToEnd = ConstraintLayout.LayoutParams.UNSET
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    endToStart = ConstraintLayout.LayoutParams.UNSET
                    matchConstraintPercentWidth = 1f
                    matchConstraintMaxWidth = 0
                    if (close != null && topToBottom == close.id) {
                        topMargin = 0
                        topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        topToBottom = ConstraintLayout.LayoutParams.UNSET
                    }
                }
            }
            if (child is DaxTextView) child.setPadding(0)
        }
    }
    header.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = 0
        bottomMargin = 0
        if (edgeToEdgeHeader) {
            marginStart = -content.paddingStart
            marginEnd = -content.paddingEnd
            leftMargin = marginStart
            rightMargin = marginEnd
        }
    }
    title.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = titleGap }
    messages.forEach { it.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = messageGap } }
    buttons?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        if (this is ConstraintLayout.LayoutParams && topToTop == ConstraintLayout.LayoutParams.UNSET &&
            topToBottom == ConstraintLayout.LayoutParams.UNSET && bottomToBottom != ConstraintLayout.LayoutParams.UNSET
        ) {
            buttons.updatePadding(top = buttonInsetAdjustedSpacing)
            topMargin = 0
        } else {
            topMargin = buttonInsetAdjustedSpacing
        }
    }
    secondary.forEach { button ->
        val followsButton = (button.parent as ViewGroup).children.takeWhile { it !== button }.any { it is DaxButton && it.visibility != View.GONE }
        button.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = if (followsButton) -4.toPx(context) else 0 }
        if (button is DaxButtonGhost) {
            val style = DaxButtonSecondary(context, null)
            button.backgroundTintList = style.backgroundTintList
            button.setTextColor(style.textColors)
            button.strokeColor = style.strokeColor
            button.strokeWidth = style.strokeWidth
            button.rippleColor = style.rippleColor
            button.iconTint = style.iconTint
            button.shapeAppearanceModel = style.shapeAppearanceModel
        }
    }
    close?.let { button ->
        button.setPadding(closeInset)
        button.minimumHeight = closeTarget
        button.minimumWidth = closeTarget
        (button as? ImageView)?.scaleType = ImageView.ScaleType.CENTER
        val parent = button.parent as ViewGroup
        if (parent is FrameLayout && parent.parent is LinearLayout) {
            // Import states overlap the reserved close row; keep its toolbar above scrolling content so the close button receives taps.
            parent.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = -height }
            parent.translationZ = 1.toPx(context).toFloat()
        }
        button.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = closeInset - parent.paddingTop
            marginEnd = closeInset - parent.paddingEnd
            marginStart = 0
            rightMargin = marginEnd
            leftMargin = marginStart
            bottomMargin = 0
            if (this is ConstraintLayout.LayoutParams) {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                topToBottom = ConstraintLayout.LayoutParams.UNSET
            }
            // shortcut: painted artwork uses 0dp gap, use -8dp when background-free assets replace it.
            if (parent is LinearLayout) {
                bottomMargin = if (edgeToEdgeHeader) 0 else -closeInset
            }
        }
    }
}
