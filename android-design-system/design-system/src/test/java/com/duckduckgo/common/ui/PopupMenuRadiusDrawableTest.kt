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

package com.duckduckgo.common.ui

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class PopupMenuRadiusDrawableTest {

    @Test
    fun whenRadiusOverlayIsAbsentThenPopupSurfaceAndCustomRipplesUse8DpInLightAndDarkThemes() {
        assertPopupRadii(
            overlayEnabled = false,
            expectedDimen = R.dimen.smallShapeCornerRadius,
        )
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenPopupSurfaceAndCustomRipplesUse16DpInLightAndDarkThemes() {
        assertPopupRadii(
            overlayEnabled = true,
            expectedDimen = R.dimen.largeShapeCornerRadius,
        )
    }

    private fun assertPopupRadii(
        overlayEnabled: Boolean,
        expectedDimen: Int,
    ) {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeResId ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
            if (overlayEnabled) {
                context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
            }
            val expectedRadius = context.resources.getDimension(expectedDimen)

            val popupBackground = checkNotNull(AppCompatResources.getDrawable(context, R.drawable.popup_menu_bg)) as GradientDrawable
            assertEquals(expectedRadius, popupBackground.getCornerRadius(), 0f)

            val singleItemMask = rippleMask(context, R.drawable.ripple_rectangle_rounded)
            assertEquals(expectedRadius, singleItemMask.getCornerRadius(), 0f)

            val topMask = rippleMask(context, R.drawable.ripple_top_rounded)
            assertArrayEquals(
                floatArrayOf(expectedRadius, expectedRadius, expectedRadius, expectedRadius, 0f, 0f, 0f, 0f),
                checkNotNull(topMask.cornerRadii),
                0f,
            )

            val bottomMask = rippleMask(context, R.drawable.ripple_bottom_rounded)
            assertArrayEquals(
                floatArrayOf(0f, 0f, 0f, 0f, expectedRadius, expectedRadius, expectedRadius, expectedRadius),
                checkNotNull(bottomMask.cornerRadii),
                0f,
            )

            assertPopupStyleUsesSharedBackground(
                context,
                R.style.Widget_DuckDuckGo_PopupMenu,
                android.R.attr.popupBackground,
                expectedRadius,
            )
            assertPopupStyleUsesSharedBackground(
                context,
                R.style.Widget_DuckDuckGo_PopupMenu,
                com.google.android.material.R.attr.popupMenuBackground,
                expectedRadius,
            )
            assertPopupStyleUsesSharedBackground(
                context,
                R.style.Widget_DuckDuckGo_PopUpOverflowMenu,
                android.R.attr.popupBackground,
                expectedRadius,
            )

            val popupContent = LinearLayout(context).apply {
                setBackgroundResource(R.drawable.popup_menu_bg)
            }
            PopupMenu(
                layoutInflater = LayoutInflater.from(context),
                resourceId = R.layout.view_popup_menu_item,
                view = popupContent,
            )
            assertEquals(overlayEnabled, popupContent.clipToOutline)
        }
    }

    private fun rippleMask(
        context: ContextThemeWrapper,
        drawableRes: Int,
    ): GradientDrawable {
        val ripple = checkNotNull(AppCompatResources.getDrawable(context, drawableRes)) as RippleDrawable
        return checkNotNull(ripple.findDrawableByLayerId(android.R.id.mask)) as GradientDrawable
    }

    private fun assertPopupStyleUsesSharedBackground(
        baseContext: ContextThemeWrapper,
        popupStyle: Int,
        backgroundAttr: Int,
        expectedRadius: Float,
    ) {
        val popupContext = ContextThemeWrapper(baseContext, popupStyle)
        val values = popupContext.obtainStyledAttributes(intArrayOf(backgroundAttr))
        try {
            assertEquals(R.drawable.popup_menu_bg, values.getResourceId(0, 0))
            val drawable = checkNotNull(AppCompatResources.getDrawable(popupContext, R.drawable.popup_menu_bg)) as GradientDrawable
            assertEquals(expectedRadius, drawable.getCornerRadius(), 0f)
        } finally {
            values.recycle()
        }
    }
}
