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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.LocalOnboardingColors
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaxOnboardingDialogRenderingTest {

    private var durationScale = 1f
    private val motionScale = object : MotionDurationScale {
        override val scaleFactor: Float
            get() = durationScale
    }

    @get:Rule
    val compose = createComposeRule(effectContext = motionScale)

    private val step = mutableStateOf<DaxOnboardingDialogStep?>(null)
    private var topSpace = 0.dp
    private val state = DaxOnboardingDialogState()
    private val contentKey = mutableStateOf("first")
    private val animate = mutableStateOf(false)
    private val contentHeight = mutableStateOf(48.dp)
    private val contentColor = mutableStateOf(Color.Transparent)
    private var contentCentre = Offset.Unspecified
    private val title = mutableStateOf("")
    private val tail = mutableStateOf(DaxOnboardingDialogTail.None)
    private val tailExtended = mutableStateOf(true)
    private var surface = Color.Unspecified
    private var onFinished: (String) -> Unit = {}
    private var dismiss: DaxOnboardingDialogDismiss? = null
    private var shadowOverride: Color? = null
    private var drawingView: View? = null
    private var unboundedParent = false

    @Before
    fun pauseAnimationClock() {
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun settledGrowAndShrinkAnimateThePaintedCardWithItsLayoutBounds() {
        showDialog()
        val small = cardBounds()
        compose.runOnIdle { animate.value = true }
        frames()
        compose.runOnIdle { contentHeight.value = 160.dp }
        frames()
        advance(120)
        val growing = cardBounds()
        assertPaintMatchesLayout()
        advance(1_000)
        val large = cardBounds()
        assertTrue(growing.height > small.height + 1f)
        assertTrue(growing.height < large.height - 1f)
        assertPaintMatchesLayout()

        compose.runOnIdle { contentHeight.value = 48.dp }
        frames()
        advance(120)
        val shrinking = cardBounds()
        assertTrue(shrinking.height > small.height + 1f)
        assertTrue(shrinking.height < large.height - 1f)
        assertPaintMatchesLayout()
        advance(1_000)
        assertEquals(small.height, cardBounds().height, 1f)
        assertPaintMatchesLayout()
    }

    @Test
    fun settledAndMorphResizesAccelerateAndDecelerateLikeTheViewDialog() {
        showDialog()
        val small = cardBounds().height
        compose.runOnIdle { animate.value = true }
        frames()
        compose.runOnIdle { contentHeight.value = 160.dp }
        frames()
        advance(100)
        val large = small + 112f
        val settledProgress = (cardBounds().height - small) / (large - small)
        assertTrue("A settled resize eases in like the View dialog, was $settledProgress", settledProgress < 0.3f)
        advance(1_000)

        compose.runOnIdle {
            contentKey.value = "second"
            contentHeight.value = 48.dp
        }
        frames()
        advance(100)
        val morphProgress = (large - cardBounds().height) / (large - small)
        assertTrue("A replacement morph starts without another entrance delay", morphProgress > 0f)
        assertTrue("A replacement morph eases in like the View dialog, was $morphProgress", morphProgress < 0.3f)
    }

    @Test
    fun firstMountAndSameKeyIntroChangesSnapBoundsAndDisabledAnimationSnapsSettledChanges() {
        animate.value = true
        contentHeight.value = 160.dp
        showDialog()
        val large = cardBounds().height
        advance(700)
        assertTrue(state.isAnimating)
        assertPaintMatchesLayout()

        compose.runOnIdle { contentHeight.value = 48.dp }
        frames()
        val small = cardBounds().height
        assertTrue(large > small + 50f)
        assertPaintMatchesLayout()

        compose.runOnIdle { state.skip() }
        frames()
        compose.runOnIdle {
            animate.value = false
            contentHeight.value = 160.dp
        }
        frames()
        assertEquals(large, cardBounds().height, 1f)
        assertPaintMatchesLayout()
    }

    @Test
    fun replacingContentMorphsPaintedBoundsBeforeRevealingTheNewTitle() {
        title.value = "Short title"
        val finished = mutableListOf<String>()
        onFinished = { finished.add(it) }
        showDialog()
        val small = cardBounds().height
        compose.runOnIdle {
            animate.value = true
            contentKey.value = "second"
            title.value = "Next title"
            contentHeight.value = 160.dp
        }
        frames()
        assertEquals(small, cardBounds().height, 1f)
        advance(350)
        val growing = cardBounds().height
        assertPaintMatchesLayout()
        compose.onNodeWithText(title.value).assertDoesNotExist()
        compose.onNodeWithTag("bottom-marker").assertDoesNotExist()
        assertEquals(listOf("first"), finished)
        advance(1_000)
        val large = cardBounds().height
        assertTrue(growing > small + 1f)
        assertTrue(growing < large - 1f)
        assertPaintMatchesLayout()

        compose.runOnIdle {
            contentKey.value = "third"
            contentHeight.value = 48.dp
        }
        frames()
        assertEquals(large, cardBounds().height, 1f)
        advance(350)
        val shrinking = cardBounds().height
        assertTrue(shrinking > small + 1f)
        assertTrue(shrinking < large - 1f)
        assertPaintMatchesLayout()
        advance(1_000)
        assertEquals(small, cardBounds().height, 1f)
        assertEquals(listOf("first", "second", "third"), finished)
        assertPaintMatchesLayout()
    }

    @Test
    fun skippingAReplacementMorphAndImmediateReplacementSnapToTheirTargetBounds() {
        showDialog()
        val small = cardBounds().height
        compose.runOnIdle {
            animate.value = true
            contentKey.value = "second"
            contentHeight.value = 160.dp
        }
        frames()
        advance(350)
        val morphing = cardBounds().height
        compose.runOnIdle { state.skip() }
        frames()
        val large = cardBounds().height
        assertFalse(state.isAnimating)
        assertTrue(large > morphing + 1f)
        assertPaintMatchesLayout()

        compose.runOnIdle {
            animate.value = false
            contentKey.value = "third"
            contentHeight.value = 48.dp
        }
        frames()
        assertFalse(state.isAnimating)
        assertEquals(small, cardBounds().height, 1f)
        assertPaintMatchesLayout()
    }

    @Test
    fun reducedMotionReplacementSnapsBoundsWithoutAResizingIntro() {
        showDialog()
        val small = cardBounds().height
        compose.runOnIdle {
            durationScale = 0f
            animate.value = true
            contentKey.value = "second"
            contentHeight.value = 160.dp
        }
        frames()

        assertFalse(state.isAnimating)
        assertTrue(cardBounds().height > small + 50f)
        assertPaintMatchesLayout()
    }

    @Test
    fun scrollingTallContentShrinksFromItsVisibleViewportToTheShortCard() {
        showDialog()
        val shortHeight = cardBounds().height
        compose.runOnIdle { contentHeight.value = 1_000.dp }
        frames()
        val viewportHeight = cardBounds().height
        assertTrue(viewportHeight > shortHeight + 100f)
        assertPaintMatchesLayout()

        compose.runOnIdle {
            animate.value = true
            contentHeight.value = 48.dp
        }
        frames()
        advance(120)
        val shrinkingHeight = cardBounds().height
        assertTrue(shrinkingHeight > shortHeight + 1f)
        assertTrue(shrinkingHeight < viewportHeight - 1f)
        assertPaintMatchesLayout()
        advance(1_000)
        assertEquals(shortHeight, cardBounds().height, 1f)
        assertPaintMatchesLayout()
    }

    @Test
    fun tailChangeFromSkippedCompletionStillAnimatesAfterTheIntro() {
        animate.value = true
        tail.value = DaxOnboardingDialogTail.BottomStart
        onFinished = { tailExtended.value = false }
        showDialog()
        compose.runOnIdle { state.skip() }
        frames()
        val initiallyExtended = paintedBottom()

        advance(80)
        val retracting = paintedBottom()
        advance(1_000)
        val retracted = paintedBottom()

        assertFalse(state.isAnimating)
        assertTrue("The settled tail must move after a skip callback", initiallyExtended > retracting + 1)
        assertTrue("The tail must pass through an intermediate depth", retracting > retracted + 1)
    }

    @Test
    fun shellAndTailRemainPaintedWhenTheContentKeyChanges() {
        tail.value = DaxOnboardingDialogTail.BottomStart
        showDialog()
        val before = paintedBottom()
        compose.runOnIdle {
            animate.value = true
            contentKey.value = "second"
        }
        frames()

        assertTrue(state.isAnimating)
        assertEquals(before, paintedBottom())
        assertPaintMatchesLayout(tailBand = true)
    }

    @Test
    fun contentFadeBlendsMidwayAndSnapsOpaqueOnSkip() {
        animate.value = true
        contentColor.value = Color.Blue
        showDialog()
        // Content starts a few frames after 600ms (phase hand-offs each take a frame) and fades for 200ms.
        advance(700)

        assertTrue(state.isAnimating)
        val mid = contentCentrePixel()
        assertFalse("Mid-fade content must not be fully opaque", matchesColor(mid, Color.Blue))
        assertFalse("Mid-fade content must not be fully transparent", matchesSurface(mid))

        compose.runOnIdle { state.skip() }
        frames()
        assertTrue("Skipped content must be fully opaque", matchesColor(contentCentrePixel(), Color.Blue))
    }

    @Test
    fun tailChangesDuringContentFadeSettleToTheLatestTail() {
        val finished = mutableListOf<String>()
        animate.value = true
        tail.value = DaxOnboardingDialogTail.BottomStart
        tailExtended.value = false
        onFinished = { finished.add(it) }
        showDialog()
        // Content starts a few frames after 600ms (phase hand-offs each take a frame) and fades for 200ms.
        advance(700)
        assertTrue(state.isAnimating)

        compose.runOnIdle {
            tail.value = DaxOnboardingDialogTail.BottomEnd
            tailExtended.value = true
        }
        var waited = 0
        while (state.isAnimating) {
            check(++waited < 60) { "The intro never completed" }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        // One more frame recomposes the snapped shape before the settled depth effect can animate it.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()

        assertEquals(listOf("first"), finished)
        assertTailFullyExtendedAtTheEnd("on the completion frame")

        advance(1_000)
        assertEquals(listOf("first"), finished)
        assertTailFullyExtendedAtTheEnd("once settled")
    }

    @Test
    fun tailIsFullyExtendedBeforeTheContentFadesIn() {
        animate.value = true
        tail.value = DaxOnboardingDialogTail.BottomEnd
        showDialog()
        // Content starts a few frames after 600ms (phase hand-offs each take a frame) and fades for 200ms.
        advance(700)

        assertTrue(state.isAnimating)
        assertTailFullyExtendedAtTheEnd("while the content fades in")
    }

    @Test
    fun newContentSnapsItsTailDepthWhenItsIntroStarts() {
        tail.value = DaxOnboardingDialogTail.BottomEnd
        tailExtended.value = false
        showDialog()
        compose.runOnIdle {
            animate.value = true
            contentKey.value = "second"
            tailExtended.value = true
        }
        frames()

        assertTrue(state.isAnimating)
        assertTailFullyExtendedAtTheEnd("as the new content's intro starts")
    }

    @Test
    fun cardFadesInWithItsSurfaceLikeTheViewDialog() {
        animate.value = true
        showDialog()
        // EntranceDelay (200ms) then a 400ms container fade; 400ms lands mid-fade.
        advance(400)

        assertTrue(state.isAnimating)
        val mid = cardCentrePixel()
        assertFalse("Mid-fade card surface must not be fully opaque", matchesSurface(mid))
        assertFalse("Mid-fade card surface must not be fully transparent", matchesColor(mid, Color.Magenta))

        compose.runOnIdle { state.skip() }
        frames()
        assertTrue("Skipped card must be fully opaque", matchesSurface(cardCentrePixel()))
    }

    @Test
    fun cardShadowFadesWithTheCardInsteadOfBeingClipped() {
        animate.value = true
        shadowOverride = Color.Black
        showDialog()
        // About a third of the way into the container fade, so the shadow sits well between clear and full.
        advance(300)
        assertTrue(state.isAnimating)
        val midPixels = capturePixels()

        compose.runOnIdle { state.skip() }
        frames()
        val settledPixels = capturePixels()
        val bounds = cardBounds()
        val x = bounds.center.x.toInt()
        val below = (bounds.bottom.toInt() + 1 until minOf(settledPixels.height, bounds.bottom.toInt() + 24))
        val y = below.maxBy { distance(settledPixels[x, it], Color.Magenta) }

        val settledDepth = distance(settledPixels[x, y], Color.Magenta)
        val midDepth = distance(midPixels[x, y], Color.Magenta)
        assertTrue("The settled shadow must paint below the card", settledDepth > 0.1f)
        assertTrue("The mid-fade shadow must not be clipped away", midDepth > settledDepth * 0.1f)
        assertTrue("The mid-fade shadow must be fading, not fully drawn", midDepth < settledDepth * 0.9f)
    }

    @Test
    fun dismissCircleFadesInsteadOfShowingOpaqueDuringEntry() {
        animate.value = true
        dismiss = DaxOnboardingDialogDismiss("Dismiss") {}
        showDialog()
        advance(400)
        assertTrue(state.isAnimating)
        // The allowance covers the card's 3% shadow, which can reach this pixel.
        assertTrue("The dismiss circle must be invisible during Entry", dismissInk() < 0.05f)

        compose.runOnIdle { state.skip() }
        frames()
        assertTrue("The settled dismiss circle must paint the surface", dismissInk() > 0.9f)
    }

    @Test
    fun dismissKeepsItsPartialFadeWhenTheContentKeyChangesMidFade() {
        animate.value = true
        dismiss = DaxOnboardingDialogDismiss("Dismiss") {}
        showDialog()
        // Content starts a few frames after 600ms and fades for 200ms.
        advance(700)
        assertTrue(state.isAnimating)
        val partial = dismissInk()
        assertTrue("The dismiss must be part-way through its first fade", partial > 0.1f && partial < 0.9f)

        compose.runOnIdle { contentKey.value = "second" }
        frames()
        assertTrue(state.isAnimating)
        // The old fade can run one more frame before the key change cancels it, hence the upper allowance.
        val held = dismissInk()
        assertTrue("The dismiss must keep its partial fade, not reset", held > partial - 0.05f)
        assertTrue("The dismiss must keep its partial fade, not jump to full", held < partial + 0.25f && held < 0.9f)

        // The new intro is in its 200ms Delay: nothing may move the dismiss yet.
        advance(100)
        assertTrue(state.isAnimating)
        assertEquals("The dismiss must hold its value until the next Content phase", held, dismissInk(), 0.02f)

        advance(2_000)
        assertFalse(state.isAnimating)
        assertTrue("The second intro must finish the dismiss fade", dismissInk() > 0.9f)
    }

    @Test
    fun dismissStaysFullyVisibleWhenTheContentKeyChangesAfterASkip() {
        animate.value = true
        dismiss = DaxOnboardingDialogDismiss("Dismiss") {}
        showDialog()
        advance(300)
        compose.runOnIdle { state.skip() }
        frames()
        assertTrue("A skip must snap the dismiss fully visible", dismissInk() > 0.9f)

        compose.runOnIdle { contentKey.value = "second" }
        frames()
        assertTrue(state.isAnimating)
        assertTrue("The dismiss must stay fully visible while new content introduces itself", dismissInk() > 0.9f)
    }

    @Test
    fun unboundedReplacementStartsTypingAfterTheDelayWithoutAnInvisibleBoundsTween() {
        unboundedParent = true
        showDialog()
        compose.runOnIdle {
            animate.value = true
            contentKey.value = "second"
            title.value = "New title"
            contentHeight.value = 160.dp
        }
        frames()
        advance(300)

        compose.onNodeWithText(title.value).assertExists()
        assertTrue(state.isAnimating)
    }

    @Test
    fun stepIndicatorIsCentredOnThePaintedCardTop() {
        topSpace = 40.dp
        showDialog()
        val root = cardBounds()
        val pixels = capturePixels()
        val paintedTop = (root.top.toInt() + 10 downTo root.top.toInt()).first { y ->
            !matchesSurface(pixels[root.center.x.toInt(), y])
        }
        assertEquals(root.top + 4f, paintedTop.toFloat(), 1f)
        compose.runOnIdle { step.value = progress(2) }
        frames()
        val pill = pillBounds()
        assertEquals(paintedTop.toFloat(), pill.center.y, 1f)
        assertEquals(38f, root.right - 4f - pill.right, 1f)
        assertTrue(pill.height > 0f)
    }

    @Test
    fun stepIndicatorDoesNotMoveTheCard() {
        topSpace = 40.dp
        showDialog()
        val before = cardBounds()
        compose.runOnIdle { step.value = progress(2) }
        frames()
        assertEquals(before, cardBounds())
        compose.runOnIdle { step.value = null }
        frames()
        assertEquals(before, cardBounds())
    }

    @Test
    fun stepIndicatorPaintsAboveTheDialogBounds() {
        topSpace = 40.dp
        step.value = progress(2)
        showDialog()
        val x = pillBounds().left.toInt() + 5
        val y = cardBounds().top.toInt() - 2
        assertTrue(matchesSurface(capturePixels()[x, y]))
        compose.runOnIdle { step.value = null }
        frames()
        assertTrue(matchesColor(capturePixels()[x, y], Color.Magenta))
    }

    @Test
    fun stepIndicatorFadesInWithTheCardShell() {
        topSpace = 40.dp
        animate.value = true
        step.value = progress(2)
        showDialog()
        advance(350)
        val pill = pillBounds()
        val x = pill.left.toInt() + 5
        val y = pill.center.y.toInt()
        val mid = capturePixels()[x, y]
        assertTrue(mid.green > Color.Magenta.green && mid.green < surface.green)
        advance(1_000)
        assertTrue(matchesSurface(capturePixels()[x, y]))
    }

    @Test
    @Config(sdk = [28], qualifiers = "xxhdpi")
    fun sameKeyAnimateChangesReachTheIndicator() {
        topSpace = 40.dp
        step.value = progress(1)
        showDialog()
        compose.runOnIdle { animate.value = true }
        frames()
        compose.runOnIdle { step.value = progress(2) }
        frames(count = 2)
        val start = compose.mainClock.currentTime
        advance(100)
        val elapsed = (compose.mainClock.currentTime - start).toFloat()
        assertEquals(18f + 18f * FastOutSlowInEasing.transform(elapsed / 300f), secondDotSpan(), 1.5f)
        compose.runOnIdle { animate.value = false }
        frames()
        assertEquals(36f, secondDotSpan(), 1.5f)
    }

    @Test
    @Config(sdk = [28], qualifiers = "xxhdpi")
    fun sameKeyDisablingAnimationSnapsTheIndicatorAfterAnAnimatedIntro() {
        topSpace = 40.dp
        animate.value = true
        step.value = progress(1)
        showDialog()
        advance(2_000)
        compose.runOnIdle { step.value = progress(2) }
        frames()
        advance(100)
        assertTrue(secondDotSpan() < 34f)
        compose.runOnIdle { animate.value = false }
        frames()
        assertEquals(36f, secondDotSpan(), 1.5f)
    }

    private fun progress(current: Int) = DaxOnboardingDialogStep(current, (1..5).map { "$it of 5" }.toImmutableList())

    private fun pillBounds() = compose.onNodeWithTag("daxOnboardingStepIndicator").fetchSemanticsNode().boundsInRoot

    private fun secondDotSpan(): Float {
        val pill = pillBounds()
        val pixels = capturePixels()
        val start = pill.left.toInt() + 24
        val ink = (start until start + 156).filter { x -> !matchesSurface(pixels[x, pill.center.y.toInt()]) }
        val spans = ink.fold(mutableListOf<IntRange>()) { spans, x ->
            if (spans.isNotEmpty() && spans.last().last == x - 1) {
                spans[spans.lastIndex] = spans.last().first..x
            } else {
                spans.add(x..x)
            }
            spans
        }
        return spans[1].count().toFloat()
    }

    private fun showDialog() {
        compose.setContent {
            DuckDuckGoTheme(isDarkTheme = false) {
                OnboardingTheme {
                    val onboardingColors = LocalOnboardingColors.current
                    CompositionLocalProvider(
                        LocalOnboardingColors provides (shadowOverride?.let { onboardingColors.copy(bubbleShadow = it) } ?: onboardingColors),
                    ) {
                        surface = DaxOnboardingDialogDefaults.SurfaceColor
                        drawingView = LocalView.current
                        Box(
                            Modifier.fillMaxSize().background(Color.Magenta).testTag("canvas")
                                .then(if (unboundedParent) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                        ) {
                            Box(Modifier.padding(top = topSpace)) {
                                DaxOnboardingDialog(
                                    contentKey = contentKey.value,
                                    title = title.value,
                                    modifier = Modifier.width(280.dp).testTag("dialog"),
                                    state = state,
                                    animate = animate.value,
                                    tail = tail.value,
                                    tailExtended = tailExtended.value,
                                    onIntroFinished = onFinished,
                                    dismiss = dismiss,
                                    step = step.value,
                                ) {
                                    Box(
                                        Modifier
                                            .width(48.dp)
                                            .height(contentHeight.value)
                                            .background(contentColor.value)
                                            .onGloballyPositioned { contentCentre = it.boundsInRoot().center },
                                    ) {
                                        Box(Modifier.align(Alignment.BottomCenter).size(8.dp).testTag("bottom-marker"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        frames()
    }

    private fun assertPaintMatchesLayout(tailBand: Boolean = false) {
        val bounds = cardBounds()
        val pixels = capturePixels()
        val x = bounds.center.x.toInt()
        val bottom = (0 until pixels.height).lastOrNull { matchesSurface(pixels[x, it]) }
        assertTrue("The card must paint its surface", bottom != null)
        val expectedBottom = bounds.bottom - if (tailBand) 33f else 0f
        assertEquals("Painted card edge must follow its measured edge", expectedBottom, bottom!!.toFloat(), 4f)
    }

    private fun cardBounds(): Rect = compose.onNodeWithTag("dialog").fetchSemanticsNode().boundsInRoot

    private fun paintedBottom(): Int {
        val pixels = capturePixels()
        return (0 until pixels.height).last { y ->
            (0 until pixels.width).any { x -> matchesSurface(pixels[x, y]) }
        }
    }

    private fun assertTailFullyExtendedAtTheEnd(moment: String) {
        val bounds = cardBounds()
        val pixels = capturePixels()
        val bodyBottom = (0 until pixels.height).last { matchesSurface(pixels[bounds.center.x.toInt(), it]) }
        val tipRow = paintedBottom()
        val band = DaxOnboardingDialogDefaults.TailBandHeight.value
        assertTrue("The tail must be fully extended $moment", tipRow - bodyBottom > band * 0.75f)
        val tipColumns = (0 until pixels.width).filter { matchesSurface(pixels[it, tipRow]) }
        assertTrue("The tail tip must sit at the end edge $moment", tipColumns.min() > bounds.center.x)
    }

    private fun capturePixels(): PixelMap = compose.runOnIdle {
        val view = checkNotNull(drawingView)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        // Drawing the Compose root directly preserves the paused clock and its current animation frame.
        view.draw(Canvas(bitmap))
        bitmap.asImageBitmap().toPixelMap()
    }

    private fun matchesSurface(color: Color): Boolean =
        abs(color.red - surface.red) < 0.02f && abs(color.green - surface.green) < 0.02f && abs(color.blue - surface.blue) < 0.02f

    private fun contentCentrePixel(): Color {
        check(contentCentre.isSpecified)
        return capturePixels()[contentCentre.x.toInt(), contentCentre.y.toInt()]
    }

    private fun cardCentrePixel(): Color {
        val centre = cardBounds().center
        return capturePixels()[centre.x.toInt(), centre.y.toInt()]
    }

    private fun distance(color: Color, other: Color): Float =
        maxOf(abs(color.red - other.red), abs(color.green - other.green), abs(color.blue - other.blue))

    // Just inside the top of the dismiss circle: below its stroke and above the card, so only the circle covers the canvas there.
    private fun dismissInk(): Float {
        val bounds = cardBounds()
        val x = (bounds.right - DaxOnboardingDialogDefaults.DismissSize.value / 2).toInt()
        val y = (bounds.top + 3f).toInt()
        return distance(capturePixels()[x, y], Color.Magenta)
    }

    private fun matchesColor(color: Color, expected: Color): Boolean =
        abs(color.red - expected.red) < 0.02f && abs(color.green - expected.green) < 0.02f && abs(color.blue - expected.blue) < 0.02f

    private fun frames(count: Int = 3) {
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun advance(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }
}
