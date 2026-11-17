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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.InfoPanel
import com.duckduckgo.common.ui.view.alignToFirstLine
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun whenAreaIsOnThenStandardPanelsUseSecondarySurfaceAndRebrandIconsCentredOnFirstLine() {
        forEachTheme(true) { context ->
            assertPanel(context, R.drawable.info_panel_tooltip_background, R.drawable.info_recolorable_16, R.attr.daxColorSurfaceSecondary, null)
            assertPanel(
                context,
                R.drawable.info_panel_alert_background,
                R.drawable.exclamation_recolorable_invert_16,
                R.attr.daxColorSurfaceSecondary,
                null,
            )
            val protections = InfoPanel(context).apply { setImageResource(R.drawable.shield_check_recolorable_16) }
            assertEquals(
                R.drawable.shield_check_recolorable_16,
                shadowOf(protections.findViewById<ImageView>(R.id.infoPanelImage).drawable).createdFromResId,
            )
        }
    }

    @Test
    fun whenTextWrapsOrFontScaleChangesThenIconStaysCentredOnTheFirstLine() {
        val application = RuntimeEnvironment.getApplication()
        listOf(R.style.Theme_DuckDuckGo_Light, R.style.Theme_DuckDuckGo_Dark).forEach { theme ->
            listOf(0.85f, 1f, 2f).forEach { fontScale ->
                val configuration = Configuration(application.resources.configuration).apply { this.fontScale = fontScale }
                val context = ContextThemeWrapper(application.createConfigurationContext(configuration), theme)
                context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_InfoPanels, true)
                listOf("Info", "First line of information\nSecond line of information").forEach { body ->
                    val panel = InfoPanel(context).apply { setText(body) }
                    val width = (240 * context.resources.displayMetrics.density).toInt()
                    panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
                    panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
                    val image = panel.findViewById<ImageView>(R.id.infoPanelImage)
                    val text = panel.findViewById<TextView>(R.id.infoPanelText)
                    val metrics = text.paint.fontMetricsInt
                    val firstLineCentre = text.top + text.baseline + (metrics.ascent + metrics.descent) / 2f
                    assertEquals(firstLineCentre, image.top + image.height / 2f, 1f)
                    if ('\n' in body) {
                        assertTrue(text.lineCount > 1)
                        assertTrue(text.top + text.height / 2f > image.top + image.height / 2f + 1f)
                    }
                }
            }
        }
    }

    @Test
    fun whenAligningTheTrackerTileFrameThenTheClampPreservesItsMeasuredFirstLinePosition() {
        val application = RuntimeEnvironment.getApplication()
        listOf(R.style.Theme_DuckDuckGo_Light, R.style.Theme_DuckDuckGo_Dark).forEach { theme ->
            listOf(0.85f, 1f, 2f).forEach { fontScale ->
                val configuration = Configuration(application.resources.configuration).apply { this.fontScale = fontScale }
                val context = ContextThemeWrapper(application.createConfigurationContext(configuration), theme)
                val text = DaxTextView(context).apply {
                    setTypography(DaxTextView.Typography.Body2)
                    setText("First line of information\nSecond line of information")
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.keyline_4)
                    }
                }
                val frameSize = context.resources.getDimensionPixelSize(R.dimen.rebrandTabSwitcherInfoPanelAnimationSize)
                val icon = View(context).apply { layoutParams = FrameLayout.LayoutParams(frameSize, frameSize) }
                val tile = FrameLayout(context).apply {
                    addView(text)
                    addView(icon)
                }
                val metrics = text.paint.fontMetricsInt
                val firstBaseline = text.paddingTop - if (text.includeFontPadding) metrics.top else metrics.ascent
                val originalTopMargin = (text.layoutParams as MarginLayoutParams).topMargin + firstBaseline +
                    (metrics.ascent + metrics.descent) / 2 - frameSize / 2
                (icon.layoutParams as MarginLayoutParams).topMargin = originalTopMargin
                val width = (240 * context.resources.displayMetrics.density).toInt()
                tile.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
                tile.layout(0, 0, tile.measuredWidth, tile.measuredHeight)
                val originalCentre = icon.top + icon.height / 2f

                text.alignToFirstLine(icon)
                tile.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
                tile.layout(0, 0, tile.measuredWidth, tile.measuredHeight)

                assertEquals(originalTopMargin, (icon.layoutParams as MarginLayoutParams).topMargin)
                assertEquals(originalCentre, icon.top + icon.height / 2f, 0f)
                assertEquals(text.top + text.baseline + (metrics.ascent + metrics.descent) / 2f, icon.top + icon.height / 2f, 1f)
                assertTrue(text.lineCount > 1)
            }
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
            assertEquals(R.drawable.info_recolorable_16, shadowOf(image.drawable).createdFromResId)
            panel.setImageResource(R.drawable.ic_exclamation_yellow_16)
            assertEquals(R.drawable.exclamation_recolorable_invert_16, shadowOf(image.drawable).createdFromResId)
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
                val expected = if (enabled) R.drawable.exclamation_recolorable_invert_16 else R.drawable.ic_info_panel_info
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
                R.drawable.info_recolorable_16 to context.getColorFromAttr(R.attr.daxColorRebrandAccentBlue),
                R.drawable.exclamation_recolorable_invert_16 to context.getColor(R.color.pollen50),
                R.drawable.shield_check_recolorable_16 to context.getColor(R.color.rb_green40),
            ).forEach { (icon, expectedColor) ->
                val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                val drawable = checkNotNull(AppCompatResources.getDrawable(context, icon))
                drawable.setBounds(0, 0, 160, 160)
                drawable.draw(Canvas(bitmap))
                assertEquals(expectedColor, bitmap.getPixel(35, 80))
            }
        }
    }

    /** A null [topMarginDp] means the rebrand layout: icon centred on the first text line, 12dp from the text. */
    private fun assertPanel(context: ContextThemeWrapper, background: Int, icon: Int, color: Int, topMarginDp: Int?) {
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
        val imageParams = image.layoutParams as MarginLayoutParams
        if (topMarginDp != null) {
            assertEquals((topMarginDp * context.resources.displayMetrics.density).toInt(), imageParams.topMargin)
        } else {
            val text = panel.findViewById<TextView>(R.id.infoPanelText)
            assertEquals(12 * context.resources.displayMetrics.density, (text.layoutParams as MarginLayoutParams).marginStart.toFloat(), 0.5f)
        }
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
