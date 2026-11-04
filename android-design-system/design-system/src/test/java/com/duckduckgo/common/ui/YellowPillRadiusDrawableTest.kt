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

import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.DaxYellowPill
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class YellowPillRadiusDrawableTest {

    @Test
    fun whenRadiusOverlayIsAbsentThenYellowPillUses2DpInLightAndDarkThemes() {
        assertPillCornerRadius(
            overlayEnabled = false,
            expectedDimen = R.dimen.daxYellowPillCornerRadius,
        )
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenYellowPillUsesPillRadiusInLightAndDarkThemes() {
        assertPillCornerRadius(
            overlayEnabled = true,
            expectedDimen = R.dimen.pillShapeCornerRadius,
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun badgesOverlayControlsPillAppearanceInLightAndDarkThemes() {
        listOf(R.style.Theme_DuckDuckGo_Light, R.style.Theme_DuckDuckGo_Dark).forEach { themeResId ->
            listOf(false, true).forEach { enabled ->
                val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
                if (enabled) {
                    context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Badges, true)
                }
                val pill = DaxYellowPill(context).apply { text = "Beta" }
                val background = pill.background as GradientDrawable
                val horizontalPadding = context.resources.getDimensionPixelSize(
                    if (enabled) R.dimen.keyline_2 else R.dimen.yellowPillPaddingHorizontal,
                )

                assertEquals(context.getColor(if (enabled) R.color.pollen30 else R.color.yellow50), background.color!!.defaultColor)
                assertEquals(context.getColor(if (enabled) R.color.pollen90 else R.color.black), pill.currentTextColor)
                assertEquals(horizontalPadding, pill.paddingStart)
                assertEquals(horizontalPadding, pill.paddingEnd)
                assertEquals(0, pill.paddingTop)
                assertEquals(0, pill.paddingBottom)
                assertEquals(if (enabled) 0.08f else 0f, pill.letterSpacing, 0f)
                assertEquals(if (enabled) 600 else 700, pill.typeface.weight)
                assertEquals(context.resources.getDimension(R.dimen.rebrandPillTextSize), pill.textSize, 0.01f)
                assertEquals("BETA", pill.transformationMethod.getTransformation(pill.text, pill).toString())
                pill.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
                if (enabled) {
                    assertEquals(context.resources.getDimensionPixelSize(R.dimen.rebrandPillTextSize), pill.lineHeight)
                    assertEquals(false, pill.includeFontPadding)
                }
                assertEquals(
                    context.resources.getDimensionPixelSize(if (enabled) R.dimen.rebrandPillMinHeight else R.dimen.yellowPillHeight),
                    pill.measuredHeight,
                )
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenBadgesOverlayIsAppliedAndFontScaleIsLargeThenPillGrowsWithText() {
        val configuration = Configuration(RuntimeEnvironment.getApplication().resources.configuration).apply { fontScale = 2f }
        val context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication().createConfigurationContext(configuration),
            R.style.Theme_DuckDuckGo_Light,
        )
        context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Badges, true)
        val pill = DaxYellowPill(context).apply { text = "Beta" }

        pill.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)

        assertTrue(pill.measuredHeight > context.resources.getDimensionPixelSize(R.dimen.rebrandPillMinHeight))
    }

    private fun assertPillCornerRadius(
        overlayEnabled: Boolean,
        expectedDimen: Int,
    ) {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeResId ->
            listOf(false, true).forEach { badgesEnabled ->
                val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
                if (overlayEnabled) {
                    context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
                }
                if (badgesEnabled) {
                    context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Badges, true)
                }
                val expectedRadius = context.resources.getDimension(expectedDimen)
                val drawable = DaxYellowPill(context).background as GradientDrawable

                assertEquals(expectedRadius, drawable.getCornerRadius(), 0f)
            }
        }
    }
}
