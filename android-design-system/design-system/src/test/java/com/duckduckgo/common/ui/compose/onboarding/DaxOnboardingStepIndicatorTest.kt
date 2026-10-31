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

package com.duckduckgo.common.ui.compose.onboarding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.LocalOnboardingColors
import com.duckduckgo.common.ui.compose.theme.OnboardingColors
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaxOnboardingStepIndicatorTest {
    @get:Rule
    val compose = createComposeRule()

    private val step = mutableStateOf(DaxOnboardingDialogStep(2, (1..5).map { "$it of 5" }.toImmutableList()))
    private val animatedStep = mutableStateOf<DaxOnboardingDialogStep?>(progress(1), neverEqualPolicy())
    private val animate = mutableStateOf(true)
    private var usesAnimation = false
    private lateinit var drawingView: View
    private lateinit var colors: OnboardingColors

    @Test
    fun pillIsASingleAccessibleNodeReadingTheCurrentLabel() {
        showIndicator()
        pill().assertTextEquals("2 of 5").onChildren().assertCountEquals(0)
        compose.onAllNodes(
            hasAnyAncestor(hasTestTag("daxOnboardingStepIndicator")) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun pillWidthIsPinnedToTheWidestLabel() {
        step.value = model(1, "1", "22222222")
        showIndicator()
        val width = bounds().width
        update(model(2, "1", "22222222"))
        assertEquals(width, bounds().width)
        update(model(1, "1", "1"))
        assertTrue(width > bounds().width)
    }

    @Test
    fun pillHeightHasNoBodyLineHeight() {
        showIndicator()
        assertTrue("Height: ${bounds().height}", bounds().height in 72f..84f)
    }

    @Test
    fun fontScaleTwoKeepsPinnedWidthAndFullLabel() {
        step.value = model(1, "1", "22222222")
        showIndicator(fontScale = 2f)
        val width = bounds().width
        update(model(2, "1", "22222222"))
        assertEquals(width, bounds().width)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(
            hasText("22222222") and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(1, layout.lineCount)
        assertFalse(layout.isLineEllipsized(0))
        assertTrue(layout.getLineRight(0) <= layout.size.width)
    }

    @Test
    fun dotsMatchTheViewGeometryAndColours() {
        step.value = model(2, "1", "2", "3")
        showIndicator()
        val spans = dotSpans()
        assertEquals(3, spans.size)
        listOf(18, 36, 18).forEachIndexed { i, expected -> assertEquals(expected.toFloat(), spans[i].count().toFloat(), 2f) }
        assertEquals(12f, (spans[1].first - spans[0].last - 1).toFloat(), 2f)
        assertEquals(12f, (spans[2].first - spans[1].last - 1).toFloat(), 2f)
        val pixels = capturePixels()
        spans.forEachIndexed { i, span ->
            assertColor(
                if (i < 2) colors.accentPrimary else colors.accentAltPrimary,
                pixels[(span.first + span.last) / 2, bounds().center.y.toInt()],
            )
        }
    }

    @Test
    fun pillPaintsFillAndStroke() {
        showIndicator()
        val bounds = bounds()
        val pixels = capturePixels()
        assertColor(colors.accentAltPrimary, pixels[bounds.center.x.toInt(), bounds.top.toInt() + 1])
        assertColor(colors.surfaceTertiary, pixels[bounds.left.toInt() + 15, bounds.center.y.toInt()])
    }

    @Test
    fun plusOneAnimatesWithFastOutSlowInOverThreeHundredMillis() {
        showIndicator(animated = true)
        val start = change(progress(2))
        pill().assertTextEquals("2 of 5")
        assertSpan(1, 18f)
        val elapsed = sample(start, 160)
        assertSpan(1, 18f + 18f * FastOutSlowInEasing.transform(elapsed / 300f))
        assertTrue(abs(dotSpans()[1].count() - (18f + 18f * elapsed / 300f)) > 3f)
        val later = sample(start, 240)
        assertSpan(1, 18f + 18f * FastOutSlowInEasing.transform(later / 300f))
        sample(start, 316)
        assertSpan(1, 36f)
        assertSpan(0, 18f)
    }

    @Test
    fun jumpAnimatesFromThePreviousStepLikeTheView() {
        showIndicator(animated = true)
        val start = change(progress(3))
        assertSpan(1, 36f)
        assertSpan(2, 18f)
        sample(start, 316)
        assertSpan(2, 36f)
    }

    @Test
    fun totalChangeAnimatesFromCurrentMinusOneWithTheNewLabels() {
        showIndicator(animated = true)
        change(progress(2, 6))
        assertEquals(6, dotSpans().size)
        assertSpan(0, 36f)
        pill().assertTextEquals("2 of 6")
    }

    @Test
    fun firstStepSnaps() {
        animatedStep.value = progress(2)
        showIndicator(animated = true)
        val start = change(progress(1))
        assertSpan(0, 36f)
        val spans = dotSpans()
        sample(start, 316)
        assertEquals(spans, dotSpans())
    }

    @Test
    fun firstCompositionAndEqualValuesSnap() {
        animatedStep.value = progress(3)
        showIndicator(animated = true)
        val start = compose.mainClock.currentTime
        assertSpan(2, 36f)
        val spans = dotSpans()
        sample(start, 150)
        assertEquals(spans, dotSpans())
        sample(start, 316)
        assertEquals(spans, dotSpans())
        val equalStart = change(progress(3))
        sample(equalStart, 150)
        assertEquals(spans, dotSpans())
    }

    @Test
    fun animateFalseSnaps() {
        animate.value = false
        showIndicator(animated = true)
        change(progress(2))
        assertSpan(1, 36f)
    }

    @Test
    fun exitFadesWithAccelerateDecelerateThenRemoves() {
        animatedStep.value = progress(5)
        showIndicator(animated = true)
        val start = change(null)
        val elapsed = sample(start, 150)
        val alpha = 1f - ((cos((elapsed / 300f + 1) * PI) / 2.0) + 0.5).toFloat()
        val actual = fillPixel()
        assertTrue(actual.green > 0f && actual.green < 1f)
        assertTrue(distance(colors.surfaceTertiary.copy(alpha = alpha).compositeOver(Color.Magenta), actual) <= 0.03f)
        sample(start, 316)
        pill().assertDoesNotExist()
    }

    @Test
    fun exitWithoutAnimationRemovesImmediately() {
        animatedStep.value = progress(5)
        animate.value = false
        showIndicator(animated = true)
        change(null)
        pill().assertDoesNotExist()
    }

    @Test
    fun newStepMidAnimationSnapsThenAppliesTheTable() {
        showIndicator(animated = true)
        sample(change(progress(2)), 100)
        val start = change(progress(3))
        assertSpan(1, 36f)
        assertSpan(2, 18f)
        sample(start, 316)
        assertSpan(2, 36f)
    }

    @Test
    fun nonNullMidExitCancelsTheFadeAndSnaps() {
        animatedStep.value = progress(5)
        showIndicator(animated = true)
        sample(change(null), 100)
        change(progress(2))
        assertColor(colors.surfaceTertiary, fillPixel())
        assertSpan(1, 36f)
    }

    @Test
    fun animateTurningFalseMidRunSnaps() {
        showIndicator(animated = true)
        sample(change(progress(2)), 100)
        compose.runOnIdle { animate.value = false }
        settleChange()
        assertSpan(1, 36f)
    }

    @Test
    fun animateTurningFalseMidExitRemoves() {
        showIndicator(animated = true)
        sample(change(null), 100)
        compose.runOnIdle { animate.value = false }
        settleChange()
        pill().assertDoesNotExist()
    }

    @Test
    fun backwardsMoveAnimatesFromCurrentMinusOne() {
        animatedStep.value = progress(5)
        showIndicator(animated = true)
        val start = change(progress(3))
        assertSpan(1, 36f)
        assertSpan(2, 18f)
        sample(start, 316)
        assertSpan(2, 36f)
    }

    private fun progress(current: Int, total: Int = 5) =
        DaxOnboardingDialogStep(current, (1..total).map { "$it of $total" }.toImmutableList())

    private fun change(value: DaxOnboardingDialogStep?): Long {
        compose.runOnIdle { animatedStep.value = value }
        settleChange()
        return compose.mainClock.currentTime
    }

    private fun settleChange() {
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun sample(start: Long, target: Long): Float {
        val before = compose.mainClock.currentTime
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val frame = compose.mainClock.currentTime - before
        while (compose.mainClock.currentTime - start + frame / 2 < target) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        return (compose.mainClock.currentTime - start).toFloat()
    }

    private fun assertSpan(index: Int, expected: Float) = assertEquals(expected, dotSpans()[index].count().toFloat(), 1.5f)

    private fun fillPixel(): Color {
        val bounds = bounds()
        return capturePixels()[bounds.left.toInt() + 5, bounds.center.y.toInt()]
    }

    private fun model(current: Int, vararg labels: String) = DaxOnboardingDialogStep(current, labels.toList().toImmutableList())

    private fun showIndicator(fontScale: Float = 1f, animated: Boolean = false) {
        usesAnimation = animated
        if (animated) compose.mainClock.autoAdvance = false
        compose.setContent {
            DuckDuckGoTheme(isDarkTheme = false) {
                OnboardingTheme {
                    colors = LocalOnboardingColors.current
                    drawingView = LocalView.current
                    CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                        Box(Modifier.fillMaxSize().background(Color.Magenta).padding(40.dp)) {
                            if (animated) {
                                AnimatedDaxOnboardingStepIndicator(animatedStep.value, animate.value)
                            } else {
                                DaxOnboardingStepIndicator(step.value)
                            }
                        }
                    }
                }
            }
        }
        if (animated) settleChange()
    }

    private fun update(value: DaxOnboardingDialogStep) {
        compose.runOnIdle { step.value = value }
        compose.waitForIdle()
    }

    private fun pill() = compose.onNodeWithTag("daxOnboardingStepIndicator")
    private fun bounds() = pill().fetchSemanticsNode().boundsInRoot

    private fun capturePixels() = compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(drawingView.width, drawingView.height, Bitmap.Config.ARGB_8888)
        drawingView.draw(Canvas(bitmap))
        bitmap.asImageBitmap().toPixelMap()
    }

    private fun dotSpans(): List<IntRange> {
        val bounds = bounds()
        val pixels = capturePixels()
        val start = bounds.left.toInt() + 24
        val total = if (usesAnimation) animatedStep.value?.labels?.size ?: 5 else step.value.labels.size
        val end = start + 36 + (total - 1) * 30
        val ink = (start until end).filter { x -> distance(pixels[x, bounds.center.y.toInt()], colors.surfaceTertiary) > 0.02f }
        return ink.fold(mutableListOf()) { spans, x ->
            if (spans.isNotEmpty() && spans.last().last == x - 1) {
                spans[spans.lastIndex] = spans.last().first..x
            } else {
                spans.add(x..x)
            }
            spans
        }
    }

    private fun distance(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
    private fun assertColor(expected: Color, actual: Color) = assertTrue("Expected $expected, got $actual", distance(expected, actual) <= 0.02f)
}
