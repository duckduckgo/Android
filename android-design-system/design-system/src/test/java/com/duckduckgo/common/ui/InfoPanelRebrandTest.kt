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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.InfoPanel
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class InfoPanelRebrandTest {

    @Test
    fun whenAreaIsOffThenStandardPanelsKeepTheirBackgroundsIconsAndSpacing() {
        forEachTheme(false) { context ->
            assertPanel(
                context,
                R.drawable.info_panel_tooltip_background,
                R.drawable.ic_info_panel_info,
                R.attr.daxInfoPanelTooltipBackgroundColor,
                4,
            )
            assertPanel(
                context,
                R.drawable.info_panel_alert_background,
                R.drawable.ic_exclamation_yellow_16,
                R.attr.daxInfoPanelAlertBackgroundColor,
                4,
            )
        }
    }

    @Test
    fun whenAreaIsOnThenStandardPanelsUseSecondarySurfaceAndRebrandIconsWithoutTopMargin() {
        forEachTheme(true) { context ->
            assertPanel(context, R.drawable.info_panel_tooltip_background, R.drawable.ic_info_recolorable_16, R.attr.daxColorSurfaceSecondary, 0)
            assertPanel(
                context,
                R.drawable.info_panel_alert_background,
                R.drawable.ic_exclamation_recolorable_invert_16,
                R.attr.daxColorSurfaceSecondary,
                0,
            )
            val protections = InfoPanel(context).apply { setImageResource(R.drawable.ic_shield_check_recolorable_16) }
            assertEquals(
                R.drawable.ic_shield_check_recolorable_16,
                shadowOf(protections.findViewById<ImageView>(R.id.infoPanelImage).drawable).createdFromResId,
            )
        }
    }

    @Test
    fun whenAreaIsOnThenExplicitStandardIconsAreMappedAndCustomIconsAndBackgroundsArePreserved() {
        forEachTheme(true) { context ->
            val attributes = Robolectric.buildAttributeSet()
                .addAttribute(R.attr.panelBackground, "@drawable/chip_background")
                .addAttribute(R.attr.panelDrawable, "@drawable/ic_info_panel_link")
                .build()
            val panel = InfoPanel(context, attributes)
            assertEquals(R.drawable.chip_background, shadowOf(panel.background).createdFromResId)
            val image = panel.findViewById<ImageView>(R.id.infoPanelImage)
            assertEquals(R.drawable.ic_info_panel_link, shadowOf(image.drawable).createdFromResId)
            panel.setImageResource(R.drawable.ic_info_panel_info)
            assertEquals(R.drawable.ic_info_recolorable_16, shadowOf(image.drawable).createdFromResId)
            panel.setImageResource(R.drawable.ic_exclamation_yellow_16)
            assertEquals(R.drawable.ic_exclamation_recolorable_invert_16, shadowOf(image.drawable).createdFromResId)
        }
    }

    @Test
    fun whenAlertHasNoExplicitIconThenRebrandUsesAlertIconAndOffKeepsDefaultInfoIcon() {
        listOf(false, true).forEach { enabled ->
            forEachTheme(enabled) { context ->
                val attributes = Robolectric.buildAttributeSet()
                    .addAttribute(R.attr.panelBackground, "@drawable/info_panel_alert_background")
                    .build()
                val panel = InfoPanel(context, attributes)
                val expected = if (enabled) R.drawable.ic_exclamation_recolorable_invert_16 else R.drawable.ic_info_panel_info
                assertEquals(expected, shadowOf(panel.findViewById<ImageView>(R.id.infoPanelImage).drawable).createdFromResId)
            }
        }
    }

    @Test
    fun whenOnlyInfoPanelsAreaIsOnThenBackgroundStillUsesRadiusAttribute() {
        forEachTheme(true) { context ->
            val panel = InfoPanel(context)
            assertEquals(context.resources.getDimension(R.dimen.smallShapeCornerRadius), (panel.background as GradientDrawable).cornerRadius, 0f)
            context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
            assertEquals(
                context.resources.getDimension(R.dimen.rebrandContainerRadius),
                (InfoPanel(context).background as GradientDrawable).cornerRadius,
                0f,
            )
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenIconsAreInflatedThenBlueUsesThemeAccentAndOtherBackgroundsKeepFigmaFills() {
        forEachTheme(true) { context ->
            listOf(
                R.drawable.ic_info_recolorable_16 to context.getColorFromAttr(R.attr.daxColorRebrandAccentBlue),
                R.drawable.ic_exclamation_recolorable_invert_16 to context.getColor(R.color.pollen50),
                R.drawable.ic_shield_check_recolorable_16 to context.getColor(R.color.rb_green40),
            ).forEach { (icon, expectedColor) ->
                val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                val drawable = checkNotNull(AppCompatResources.getDrawable(context, icon))
                drawable.setBounds(0, 0, 160, 160)
                drawable.draw(Canvas(bitmap))
                assertEquals(expectedColor, bitmap.getPixel(35, 80))
            }
        }
    }

    private fun assertPanel(context: ContextThemeWrapper, background: Int, icon: Int, color: Int, topMarginDp: Int) {
        val attributes = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.panelBackground, "@drawable/" + context.resources.getResourceEntryName(background))
            .addAttribute(
                R.attr.panelDrawable,
                if (background == R.drawable.info_panel_alert_background) "@drawable/ic_exclamation_yellow_16" else "@drawable/ic_info_panel_info",
            )
            .build()
        val panel = InfoPanel(context, attributes)
        assertEquals(context.getColorFromAttr(color), (panel.background as GradientDrawable).color!!.defaultColor)
        val image = panel.findViewById<ImageView>(R.id.infoPanelImage)
        assertEquals(icon, shadowOf(image.drawable).createdFromResId)
        assertEquals((topMarginDp * context.resources.displayMetrics.density).toInt(), (image.layoutParams as MarginLayoutParams).topMargin)
        assertEquals(context.resources.getDimensionPixelSize(R.dimen.infoPanelIconSize), image.layoutParams.width)
    }

    private fun forEachTheme(enabled: Boolean, assertion: (ContextThemeWrapper) -> Unit) {
        listOf(R.style.Theme_DuckDuckGo_Light, R.style.Theme_DuckDuckGo_Dark).forEach { theme ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme)
            if (enabled) context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_InfoPanels, true)
            assertion(context)
        }
    }
}
