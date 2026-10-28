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

package com.duckduckgo.app.onboarding.ui.page.welcome

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.common.ui.view.getColorFromAttr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w480dp-h1040dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingComparisonAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private var drawingView: View? = null
    private val ready = mutableStateOf<Boolean?>(null)
    private val skipped = mutableStateOf(false)
    private val generation = mutableStateOf(0)
    private val dark = mutableStateOf(false)
    private val chart = mutableStateOf<ComparisonChartConfig>(ComparisonChartConfig.Ai)
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun titleDecodesHtmlAndKeepsFinalPhraseOnOneLineIncludingDynamicChanges() {
        val state = OnboardingContentState(
            "test",
            ContentConfig.Welcome(
                TextConfig.Literal("Choose &amp; keep your default browser?"),
                TextConfig.Literal("Body"),
                null,
            ),
            ContentValueStore(),
            {},
            true,
        )
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    DaxOnboardingDialog("title", state.resolvedTitle(LocalContext.current), Modifier.width(260.dp), animate = false)
                }
            }
        }
        var expected = "Choose & keep your default\u00a0browser?"
        var layout = textLayout(expected)
        assertTrue(layout.lineCount > 1)
        assertEquals(layout.getLineForOffset(expected.indexOf("default")), layout.getLineForOffset(expected.indexOf("browser")))
        compose.runOnIdle { state.title = TextConfig.Literal("<b>Imported</b> &amp; saved your passwords") }
        expected = "Imported & saved your\u00a0passwords"
        layout = textLayout(expected)
        assertEquals(expected, layout.layoutInput.text.text)
        assertEquals(layout.getLineForOffset(expected.indexOf("your")), layout.getLineForOffset(expected.indexOf("passwords")))
    }

    @Test
    fun everyChartUsesReferenceTextAndDividerColorsAndGrowsWithLargeTypeInBothThemes() {
        ready.value = false
        showChart(fontScale = 1.8f, width = 320)
        val charts = listOf(
            ComparisonChartConfig.Browser(false),
            ComparisonChartConfig.Browser(true),
            ComparisonChartConfig.Ai,
            ComparisonChartConfig.SegmentedSearchPath,
            ComparisonChartConfig.SegmentedAiPath,
            ComparisonChartConfig.SegmentedNoAiPath,
            ComparisonChartConfig.SegmentedBlockAdsPath,
        )
        listOf(false, true).forEach { night ->
            charts.forEach { config ->
                compose.runOnIdle {
                    dark.value = night
                    chart.value = config
                    generation.value++
                }
                val context =
                    ContextThemeWrapper(
                        app,
                        if (night) CommonR.style.Theme_DuckDuckGo_Dark_Onboarding else CommonR.style.Theme_DuckDuckGo_Light_Onboarding,
                    )
                val reference = LayoutInflater.from(context).inflate(R.layout.include_brand_design_comparison_chart_row, null)
                val referenceText = reference.findViewById<TextView>(R.id.rowText)
                config.rows.forEachIndexed { index, row ->
                    assertEquals(referenceText.currentTextColor, textLayout(context.getString(row.textRes)).layoutInput.style.color.toArgb())
                    val rowBounds = compose.onNodeWithTag("comparison-row-$index").fetchSemanticsNode().boundsInRoot
                    val divider = compose.onNodeWithTag("comparison-divider-$index")
                    val bounds = divider.fetchSemanticsNode().boundsInRoot
                    assertEquals(rowBounds.height - 16f, bounds.height, .1f)
                    val pixels = capture(bounds).toPixelMap()
                    val expected = Color(context.getColorFromAttr(CommonR.attr.daxColorContainer))
                    // The divider is composited over the chart row, so compare against the same reference color composite.
                    val background = if (index % 2 == 0) {
                        Color(
                            context.getColorFromAttr(CommonR.attr.onboardingAccentAltGlowPrimary),
                        )
                    } else {
                        Color.Transparent
                    }
                    val referencePixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    Canvas(referencePixel).apply {
                        drawColor(android.graphics.Color.WHITE)
                        drawColor(background.toArgb())
                        drawColor(expected.toArgb())
                    }
                    assertEquals(referencePixel.getPixel(0, 0), pixels[pixels.width / 2, pixels.height / 2].toArgb())
                    referencePixel.recycle()
                }
                config.headerLeftLabelRes?.let {
                    assertEquals(referenceText.currentTextColor, textLayout(context.getString(it)).layoutInput.style.color.toArgb())
                }
                assertTrue(config.rows.indices.any { compose.onNodeWithTag("comparison-row-$it").fetchSemanticsNode().boundsInRoot.height > 56f })
            }
        }
    }

    @Test
    fun checkStaggerOvershootAndNaturalEndpointMatchSnapAndReplacement() {
        compose.mainClock.autoAdvance = false
        showChart()
        advance(16)
        assertEquals(0, checkPixels(0))
        compose.runOnIdle { ready.value = true }
        advance(96)
        assertTrue(
            "first check at ${compose.mainClock.currentTime}: ${compose.onNodeWithTag("comparison-check-0").fetchSemanticsNode().boundsInRoot}",
            checkPixels(0) > 0,
        )
        assertEquals(0, checkPixels(1))
        advance(160)
        val overshoot = checkPixels(0)
        advance(1000)
        val settled = pixels()
        chart.value.rows.indices.forEach { assertTrue("settled check $it is painted", checkPixels(it) > 200) }
        assertTrue(overshoot > checkPixels(0))
        compose.runOnIdle { skipped.value = true }
        advance(32)
        assertTrue(settled.contentEquals(pixels()))
        compose.runOnIdle {
            generation.value++
            ready.value = false
            skipped.value = false
        }
        advance(32)
        assertTrue(settled.contentEquals(pixels()))
        compose.runOnIdle {
            generation.value++
            ready.value = true
        }
        advance(96)
        compose.runOnIdle {
            generation.value++
            ready.value = null
        }
        advance(1500)
        chart.value.rows.indices.forEach { assertEquals(0, checkPixels(it)) }
    }

    @Test
    fun skipBeforeAndAfterReadinessShowsAllFinalChecks() {
        compose.mainClock.autoAdvance = false
        skipped.value = true
        ready.value = false
        showChart()
        advance(32)
        val settled = pixels()
        chart.value.rows.indices.forEach { assertTrue("settled check $it is painted", checkPixels(it) > 200) }
        compose.runOnIdle {
            generation.value++
            skipped.value = false
            ready.value = true
        }
        advance(250)
        compose.runOnIdle { skipped.value = true }
        advance(32)
        assertTrue(settled.contentEquals(pixels()))
    }

    private fun advance(milliseconds: Long) {
        repeat(((milliseconds + 15) / 16).toInt()) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun showChart(fontScale: Float = 1f, width: Int = 400) {
        compose.setContent {
            drawingView = LocalView.current
            val context =
                ContextThemeWrapper(
                    LocalContext.current,
                    if (dark.value) CommonR.style.Theme_DuckDuckGo_Dark_Onboarding else CommonR.style.Theme_DuckDuckGo_Light_Onboarding,
                )
            CompositionLocalProvider(LocalContext provides context, LocalDensity provides Density(1f, fontScale)) {
                DuckDuckGoTheme(isDarkTheme = dark.value) {
                    OnboardingTheme {
                        Box(Modifier.width(width.dp).background(Color.White).testTag("chart")) {
                            key(generation.value) { ComparisonChartContent(chart.value, ready.value, skipped.value) }
                        }
                    }
                }
            }
        }
    }

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun capture(bounds: Rect): ImageBitmap = compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(-bounds.left, -bounds.top)
        requireNotNull(drawingView).draw(canvas)
        bitmap.asImageBitmap()
    }

    private fun pixels(): IntArray {
        val image = capture(compose.onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot).toPixelMap()
        return IntArray(image.width * image.height) { image[it % image.width, it / image.width].toArgb() }
    }

    private fun checkPixels(index: Int): Int {
        val image = capture(compose.onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot).toPixelMap()
        val bounds = compose.onNodeWithTag("comparison-row-$index").fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot
        var count = 0
        for (y in (bounds.top - root.top).toInt() until (bounds.bottom - root.top).toInt()) {
            for (x in image.width - 44 until image.width - 4) {
                val color = image[x, y]
                if (color.green - color.red > .01f && color.green - color.blue > .01f) count++
            }
        }
        return count
    }
}
