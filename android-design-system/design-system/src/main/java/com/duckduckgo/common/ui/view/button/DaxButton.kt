/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.common.ui.view.button

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import com.duckduckgo.common.ui.view.button.Size.Small
import com.duckduckgo.mobile.android.R
import com.google.android.material.button.MaterialButton

open class DaxButton @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
) : MaterialButton(
    ctx,
    attrs,
    defStyleAttr,
) {

    init {
        val typedArray =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.DaxButton,
                0,
                0,
            )

        val buttonSize = if (typedArray.hasValue(R.styleable.DaxButton_daxButtonSize)) {
            Size.from(typedArray.getInt(R.styleable.DaxButton_daxButtonSize, 0))
        } else {
            Small
        }

        typedArray.recycle()

        val sidePadding = resolveThemeDimensionPx(
            if (buttonSize == Small) R.attr.daxButtonSmallSidePadding else R.attr.daxButtonLargeSidePadding,
            if (buttonSize == Small) R.dimen.buttonSmallSidePadding else R.dimen.buttonLargeSidePadding,
        )

        val topPadding = resolveThemeDimensionPx(
            if (buttonSize == Small) R.attr.daxButtonSmallTopPadding else R.attr.daxButtonLargeTopPadding,
            if (buttonSize == Small) R.dimen.buttonSmallTopPadding else R.dimen.buttonLargeTopPadding,
        )

        val insetValue = TypedValue()
        val insetAttr = if (buttonSize == Small) R.attr.daxButtonSmallVerticalInset else R.attr.daxButtonLargeVerticalInset
        val verticalInset = if (context.theme.resolveAttribute(insetAttr, insetValue, true)) {
            TypedValue.complexToDimensionPixelSize(insetValue.data, resources.displayMetrics)
        } else {
            null
        }
        if (verticalInset != null) {
            insetTop = verticalInset
            insetBottom = verticalInset
        }

        val resolvedHeight = resolveThemeDimensionPx(
            if (buttonSize == Small) R.attr.daxButtonSmallHeight else R.attr.daxButtonLargeHeight,
            Size.dimension(buttonSize),
        )
        minHeight = if (buttonSize == Size.Large && verticalInset != null) {
            resolvedHeight + verticalInset * 2
        } else {
            resolvedHeight
        }

        setPadding(sidePadding, topPadding, sidePadding, topPadding)

        if (buttonSize == Size.Large && !hasLayoutTextAppearance(attrs)) {
            val largeAppearance = TypedValue()
            if (context.theme.resolveAttribute(R.attr.textAppearanceButtonLarge, largeAppearance, true)) {
                setTextAppearance(largeAppearance.resourceId)
            }
        }
    }

    private fun resolveThemeDimensionPx(
        attr: Int,
        fallbackDimen: Int,
    ): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true) && value.type == TypedValue.TYPE_DIMENSION) {
            TypedValue.complexToDimensionPixelSize(value.data, resources.displayMetrics)
        } else {
            resources.getDimensionPixelSize(fallbackDimen)
        }
    }

    private fun hasLayoutTextAppearance(attrs: AttributeSet?): Boolean {
        if (attrs == null) return false
        for (i in 0 until attrs.attributeCount) {
            if (attrs.getAttributeName(i) == "textAppearance") return true
        }
        return false
    }
}

enum class Size {
    Small,
    Large,
    ;

    companion object {
        fun from(size: Int): Size {
            // same order as attrs-button.xml
            return when (size) {
                0 -> Small
                1 -> Large
                else -> Large
            }
        }

        fun dimension(size: Size): Int {
            return when (size) {
                Small -> R.dimen.buttonSmallHeight
                Large -> R.dimen.buttonLargeHeight
                else -> R.dimen.buttonSmallHeight
            }
        }
    }
}

enum class ButtonType {
    PRIMARY,
    GHOST,
    SECONDARY,
    DESTRUCTIVE,
    DESTRUCTIVE_SECONDARY,
    GHOST_DESTRUCTIVE,
    GHOST_ALT,
    BRAND,
    ;

    fun getView(context: Context): DaxButton {
        return when (this) {
            PRIMARY -> DaxButtonPrimary(context, null)
            GHOST -> DaxButtonGhost(context, null)
            SECONDARY -> DaxButtonSecondary(context, null)
            DESTRUCTIVE -> DaxButtonDestructive(context, null)
            DESTRUCTIVE_SECONDARY -> DaxButtonDestructiveSecondary(context, null)
            GHOST_DESTRUCTIVE -> DaxButtonGhostDestructive(context, null)
            GHOST_ALT -> DaxButtonGhostAlt(context, null)
            BRAND -> DaxButtonBrand(context, null)
        }
    }
}
