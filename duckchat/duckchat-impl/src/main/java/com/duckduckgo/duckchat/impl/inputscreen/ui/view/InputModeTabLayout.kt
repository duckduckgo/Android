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
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.tabs.TabLayout
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(scope = ViewScope::class)
class InputModeTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.tabStyle,
) : TabLayout(context, attrs, defStyleAttr) {

    @Inject
    lateinit var appTheme: AppTheme

    init {
        setSelectedTabIndicator(
            buildShadowedTabIndicator(
                context = context,
                heightPx = resources.getDimensionPixelSize(R.dimen.inputModeTabIndicatorHeight),
                containerHeightPx = resources.getDimensionPixelSize(R.dimen.inputModeSwitchHeight),
                horizontalInsetPx = resources.getDimensionPixelSize(R.dimen.inputModeTabIndicatorHorizontalInset),
                elevationPx = resources.getDimensionPixelSize(R.dimen.inputModeTabIndicatorElevation),
            ),
        )
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }
    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        val searchTabView = getTabAt(0)!!.view
        val searchTabIcon = searchTabView.findViewById<ImageView>(R.id.tab_icon)
        // We're changing the selector to ensure that the right icon is used even if user manually changes the theme,
        // without relying on the system theme. For these cases, using "drawable-night" directory is not sufficient.
        if (appTheme.isLightModeEnabled()) {
            searchTabIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_search_tab_selector))
        } else {
            searchTabIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_search_tab_selector_dark))
        }
    }

    private fun buildShadowedTabIndicator(
        context: Context,
        heightPx: Int,
        containerHeightPx: Int,
        horizontalInsetPx: Int,
        elevationPx: Int,
    ): InsetDrawable {
        val cornerRadius = heightPx / 2f
        val pill = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
            .build()

        // Computing a pill height that will be perfectly centered in the container vertically, with equal top and bottom insets,
        // and matching horizontal insets, if possible.
        val shadowPillHeightCandidates = listOf(heightPx, heightPx - 1, heightPx + 1).filter { candidate ->
            // only pick values that will result in even vertical insets
            (containerHeightPx - candidate) % 2 == 0
        }
        val shadowPillHeight = shadowPillHeightCandidates
            .firstOrNull { candidate ->
                // prefer values that will result in equal horizontal and vertical insets
                val verticalInsetsPx = (containerHeightPx - candidate) / 2
                verticalInsetsPx == horizontalInsetPx
            }
            ?: shadowPillHeightCandidates.firstOrNull()
            ?: heightPx

        val shadowedPill = object : MaterialShapeDrawable(pill) {
            override fun getIntrinsicHeight(): Int = shadowPillHeight
        }.apply {
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
            initializeElevationOverlay(context)
            val shadowColor = MaterialColors.getColor(
                context,
                com.duckduckgo.mobile.android.R.attr.daxColorInputModeIndicatorShadow,
                Color.BLACK,
            )
            setShadowColor(shadowColor)
            elevation = elevationPx.toFloat()
        }

        return InsetDrawable(shadowedPill, horizontalInsetPx, 0, horizontalInsetPx, 0)
    }
}
