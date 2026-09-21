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

package com.duckduckgo.duckchat.impl.nativeinput.footer

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputModeWidget
import com.google.android.material.card.MaterialCardView

/**
 * Docks a [NativeInputFooterView] under an input card. The footer is positioned against the
 * card's visible surface (compat shadow padding excluded), tucked under its bottom edge by more than the
 * card's corner radius, and drawn first so the card and its shadow sit on top of it.
 */
open class NativeInputFooterDockLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var inputCard: MaterialCardView? = null
    private val footerOverlap = resources.getDimensionPixelSize(R.dimen.nativeInputFooterOverlap)

    // The card's elevation keeps changing (hide fade, morph animation); the footer follows it each frame
    // so their side shadows stay continuous.
    private val elevationSync = ViewTreeObserver.OnPreDrawListener {
        syncFooterElevation()
        true
    }

    init {
        isChildrenDrawingOrderEnabled = true
        clipChildren = false
        clipToPadding = false
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        inputCard = findInputCard()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(elevationSync)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(elevationSync)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (alignFooterToInputCard()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        syncFooterElevation()
    }

    internal fun onFooterVisibilityChanged() {
        syncFooterElevation()
    }

    override fun getChildDrawingOrder(
        childCount: Int,
        drawingPosition: Int,
    ): Int = footerFirstChildDrawingOrder(childCount, drawingPosition)

    /**
     * Sets the footer margins so it spans exactly the card's visible width and overlaps its visible
     * bottom edge by [footerOverlap]. Reads the first measure pass, so siblings beside the card (fire
     * icon, inline nav buttons) and compat shadow padding are all accounted for.
     */
    private fun alignFooterToInputCard(): Boolean {
        val card = inputCard ?: return false
        val footer = footerHost()?.takeIf { it.visibility != GONE } ?: return false
        val params = footer.layoutParams as? MarginLayoutParams ?: return false

        val start = card.offsetWithin(this, Edge.START) + card.paddingStart
        val end = card.offsetWithin(this, Edge.END) + card.paddingEnd
        val top = -(card.offsetWithin(this, Edge.BOTTOM) + card.paddingBottom + footerOverlap)
        if (params.marginStart == start && params.marginEnd == end && params.topMargin == top) return false

        params.marginStart = start
        params.marginEnd = end
        params.topMargin = top
        // Start/end margins only reach leftMargin/rightMargin through resolution, which the footer's own
        // measure pass already ran before these values were set.
        params.resolveLayoutDirection(layoutDirection)
        return true
    }

    private enum class Edge { START, END, BOTTOM }

    private fun View.offsetWithin(
        ancestor: ViewGroup,
        edge: Edge,
    ): Int {
        var offset = 0
        var child: View = this
        while (true) {
            val parent = child.parent as? ViewGroup ?: return offset
            offset += child.margin(edge)
            if (parent === ancestor) return offset
            offset += parent.padding(edge) + parent.siblingsExtent(child, edge)
            child = parent
        }
    }

    private fun View.margin(edge: Edge): Int {
        val params = layoutParams as? MarginLayoutParams ?: return 0
        return when (edge) {
            Edge.START -> params.marginStart
            Edge.END -> params.marginEnd
            Edge.BOTTOM -> params.bottomMargin
        }
    }

    private fun View.padding(edge: Edge): Int = when (edge) {
        Edge.START -> paddingStart
        Edge.END -> paddingEnd
        Edge.BOTTOM -> paddingBottom
    }

    private fun ViewGroup.siblingsExtent(
        child: View,
        edge: Edge,
    ): Int {
        if (this !is LinearLayout) return 0
        val horizontal = orientation == HORIZONTAL
        if (horizontal && edge == Edge.BOTTOM || !horizontal && edge != Edge.BOTTOM) return 0
        val childIndex = indexOfChild(child)
        val siblings = if (edge == Edge.START) 0 until childIndex else childIndex + 1 until childCount
        return siblings
            .map(::getChildAt)
            .filter { it.visibility != GONE }
            .sumOf { sibling ->
                val params = sibling.layoutParams as? MarginLayoutParams
                if (horizontal) {
                    sibling.measuredWidth + (params?.marginStart ?: 0) + (params?.marginEnd ?: 0)
                } else {
                    sibling.measuredHeight + (params?.topMargin ?: 0) + (params?.bottomMargin ?: 0)
                }
            }
    }

    private fun syncFooterElevation() {
        val card = inputCard ?: return
        footerCard()?.let { footerCard ->
            if (footerCard.cardElevation != card.cardElevation) footerCard.cardElevation = card.cardElevation
        }
    }

    private fun findInputCard(): MaterialCardView? {
        fun ViewGroup.findCard(): MaterialCardView? {
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                if (child is MaterialCardView && child.childCount == 1 && child.getChildAt(0) is NativeInputModeWidget) {
                    return child
                }
                if (child is ViewGroup) {
                    child.findCard()?.let { return it }
                }
            }
            return null
        }
        return findCard()
    }

    private fun footerHost(): NativeInputFooterView? =
        (0 until childCount)
            .map(::getChildAt)
            .filterIsInstance<NativeInputFooterView>()
            .firstOrNull()

    private fun footerCard(): MaterialCardView? =
        footerHost()
            ?.takeIf { it.isVisible }
            ?.getChildAt(0) as? MaterialCardView
}

internal fun footerFirstChildDrawingOrder(
    childCount: Int,
    drawingPosition: Int,
): Int = when {
    childCount < 2 -> drawingPosition
    drawingPosition == 0 -> childCount - 1
    else -> drawingPosition - 1
}
