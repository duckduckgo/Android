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
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.ViewGroup.MarginLayoutParams
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import com.duckduckgo.mobile.android.R as CommonR

/**
 * The card every host wraps [NativeInputModeWidget] in. It creates the widget as its only child, applies the
 * Fire Tab card overlay to itself, and owns the card's look for each [Surface]; hosts only place it.
 */
class NativeInputLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle,
) : MaterialCardView(withInputCardTheme(context), attrs, defStyleAttr) {

    enum class Surface { OMNIBAR_TOP, OMNIBAR_BOTTOM, CONTEXTUAL, EDIT }

    val widget: NativeInputModeWidget = NativeInputModeWidget(this.context).apply { id = R.id.inputModeWidget }

    var surface: Surface = Surface.OMNIBAR_TOP
        set(value) {
            field = value
            applySurfaceStyle()
        }

    init {
        addView(widget, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val styled = this.context.obtainStyledAttributes(attrs, R.styleable.NativeInputLayout)
        surface = Surface.entries[styled.getInt(R.styleable.NativeInputLayout_nativeInputSurface, Surface.OMNIBAR_TOP.ordinal)]
        styled.recycle()
    }

    private fun applySurfaceStyle() {
        val keyline1 = resources.getDimensionPixelSize(CommonR.dimen.keyline_1)
        val keyline2 = resources.getDimensionPixelSize(CommonR.dimen.keyline_2)
        val elevation = when (surface) {
            Surface.OMNIBAR_TOP -> 6f
            Surface.OMNIBAR_BOTTOM -> 3f
            Surface.CONTEXTUAL -> 4f
            Surface.EDIT -> 2f
        }.toPx()
        // Compat padding is sized from maxCardElevation, which only follows cardElevation at construction.
        maxCardElevation = elevation
        cardElevation = elevation
        useCompatPadding = surface == Surface.OMNIBAR_TOP
        val background = if (surface == Surface.OMNIBAR_TOP) CommonR.attr.daxColorSurface else CommonR.attr.daxColorWindow
        setCardBackgroundColor(context.getColorFromAttr(background))
        when (surface) {
            Surface.OMNIBAR_TOP, Surface.OMNIBAR_BOTTOM -> radius = 24f.toPx()
            // The sheet's card floats with margins on all sides, so all four corners are rounded.
            Surface.CONTEXTUAL -> shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(resources.getDimension(CommonR.dimen.extraLargeShapeCornerRadius))
                .build()
            Surface.EDIT -> Unit
        }
        when (surface) {
            Surface.OMNIBAR_TOP -> widget.setPaddingRelative(keyline1, 4f.toPx().toInt(), keyline1, 4f.toPx().toInt())
            Surface.OMNIBAR_BOTTOM -> widget.setPaddingRelative(keyline1, keyline2, keyline1, keyline2)
            Surface.CONTEXTUAL, Surface.EDIT -> widget.setPaddingRelative(keyline2, 4f.toPx().toInt(), keyline2, 4f.toPx().toInt())
        }
    }

    /**
     * Shapes the omnibar card from the widget's state. The contextual and edit cards keep their surface shape:
     * the per-tab store they share can briefly emit a top BROWSER search-only state that would otherwise turn
     * them into the omnibar pill.
     */
    internal fun applyOmnibarShape(
        state: NativeInputState,
        rebrandAddressBar: Boolean,
    ) {
        if (surface != Surface.OMNIBAR_TOP && surface != Surface.OMNIBAR_BOTTOM) return
        val lp = layoutParams as? MarginLayoutParams ?: return
        // Only the top browser search-only omnibar takes the wide-omnibar shape; isBottom is part of this
        // condition (not an early return) so a bottom Duck.ai frame still reaches the reset below.
        val isBrowserSearchOnly = state.inputContext == NativeInputState.InputContext.BROWSER && !state.toggleVisible
        if (state.isBottom) {
            val radius = when {
                !isBrowserSearchOnly -> CommonR.dimen.extraLargeShapeCornerRadius
                rebrandAddressBar -> CommonR.dimen.rebrandInputRadius
                else -> CommonR.dimen.largeShapeCornerRadius
            }
            this.radius = resources.getDimension(radius)
        }
        if (isBrowserSearchOnly && !state.isBottom) {
            val targetTopMargin = resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginTop)
            val targetHorizontalMargin = resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginHorizontal)
            if (rebrandAddressBar) {
                shapeAppearanceModel = shapeAppearanceModel.withCornerSize(ShapeAppearanceModel.PILL)
            } else {
                radius = resources.getDimension(CommonR.dimen.largeShapeCornerRadius)
            }
            lp.topMargin = targetTopMargin - paddingTop
            lp.marginStart = targetHorizontalMargin - paddingLeft
            lp.marginEnd = targetHorizontalMargin - paddingRight
        } else if (isBrowserSearchOnly && state.isBottom) {
            // Bottom search-only omnibar: 16dp leading (paddingStart keyline_2 + this keyline_2) and 16dp
            // trailing (paddingEnd keyline_1 + this) so the field is centred when full-width.
            lp.marginStart = resources.getDimensionPixelSize(CommonR.dimen.keyline_2)
            lp.marginEnd = resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginHorizontal) -
                resources.getDimensionPixelSize(CommonR.dimen.keyline_1)
        } else {
            // Reset the start margin so the omnibar value doesn't leak onto the shared Duck.ai card.
            // The bottom card keeps keyline_1 (Duck.ai needs 8dp with the wrapper's paddingEnd).
            lp.marginStart = 0
            lp.marginEnd = if (state.isBottom) resources.getDimensionPixelSize(CommonR.dimen.keyline_1) else 0
        }
        layoutParams = lp
    }
}

/**
 * Same wrapping LayoutInflater applies for android:theme on a tag: in a Fire Tab the overlay remaps
 * daxColorSurface/daxColorWindow, and the widget inherits it because it is created with this card's context.
 */
private fun withInputCardTheme(context: Context): Context {
    val overlay = TypedValue()
    if (!context.theme.resolveAttribute(CommonR.attr.daxInputModeCardThemeOverlay, overlay, true) || overlay.resourceId == 0) {
        return context
    }
    return ContextThemeWrapper(context, overlay.resourceId)
}
