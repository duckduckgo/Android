/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.inputscreen.ui.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.util.AttributeSet
import android.view.ViewOutlineProvider
import com.duckduckgo.duckchat.impl.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.tabs.TabLayout

class InputModeTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.tabStyle,
) : TabLayout(context, attrs, defStyleAttr) {

    init {
        val indicatorHeightPx = resources.getDimensionPixelSize(R.dimen.inputModeTabIndicatorHeight)
        setSelectedTabIndicator(
            makeShadowedTabIndicator(context, indicatorHeightPx, resources.getDimensionPixelSize(R.dimen.inputModeTabIndicatorHorizontalPadding)),
        )
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }

    private fun makeShadowedTabIndicator(
        context: Context,
        heightPx: Int,
        horizontalPaddingPx: Int,
    ): InsetDrawable {
        val cornerRadius = heightPx / 2f
        val shape = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
            .build()

        val pill = object : MaterialShapeDrawable(shape) {
            override fun getIntrinsicHeight(): Int = heightPx
        }.apply {
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
            initializeElevationOverlay(context)
            val shadowColor = MaterialColors.getColor(
                context,
                com.duckduckgo.mobile.android.R.attr.daxColorInputModeIndicatorShadow,
                Color.BLACK,
            )
            setShadowColor(shadowColor)
            elevation = resources.getDimensionPixelSize(R.dimen.inputModeTabIndicatorElevation).toFloat()
        }

        return object : InsetDrawable(pill, horizontalPaddingPx, 0, horizontalPaddingPx, 0) {
            override fun getIntrinsicHeight(): Int = heightPx
        }
    }
}
