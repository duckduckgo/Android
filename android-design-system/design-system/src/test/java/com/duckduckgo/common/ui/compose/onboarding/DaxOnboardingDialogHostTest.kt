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

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.LocalOnboardingColors
import com.duckduckgo.common.ui.compose.theme.Mandarin50
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaxOnboardingDialogHostTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val contentKey = mutableStateOf("first")
    private val title = mutableStateOf("First title")
    private val animate = mutableStateOf(false)
    private val contentHeight = mutableStateOf(48.dp)
    private val state = DaxOnboardingDialogState()
    private val finished = mutableListOf<String>()

    @Test
    fun aVisibleCardReplacementUnderAHeightProbingHostStartsBeforeTheEntranceDelayExpires() {
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var view: ComposeView
            scenario.onActivity { activity -> view = showDialog(activity, HeightProbingHost(activity)) }
            frames()
            val originalHeight = compose.runOnIdle { view.height }
            compose.runOnIdle {
                animate.value = true
                contentKey.value = "second"
                contentHeight.value = 200.dp
            }
            frames()
            advance(100)
            val intermediateHeight = compose.runOnIdle { view.height }
            advance(500)
            val finalHeight = compose.runOnIdle { view.height }

            assertTrue(intermediateHeight > originalHeight)
            assertTrue(intermediateHeight < finalHeight)
        }
    }

    @Test
    fun aSettledResizeUnderAHostThatMeasuresTwiceWithDifferentWidthsReachesTheNewHeightInOneBoundsDuration() {
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var view: ComposeView
            scenario.onActivity { activity -> view = showDialog(activity, WidthAlternatingHost(activity)) }
            frames()
            compose.runOnIdle {
                animate.value = true
                contentHeight.value = 200.dp
            }
            frames()
            repeat(((DaxOnboardingDialogDefaults.BoundsAnimationDuration.inWholeMilliseconds + 50) / FrameMillis).toInt()) { advance(FrameMillis) }
            val heightAfterBoundsDuration = compose.runOnIdle { view.height }
            advance(5_000)
            val settledHeight = compose.runOnIdle { view.height }

            assertTrue(settledHeight > 200)
            assertEquals(settledHeight, heightAfterBoundsDuration)
        }
    }

    @Test
    fun contentResizingMidMorphUnderAHeightProbingViewHostStillRevealsTheNewIntroWithoutASkip() {
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> showDialog(activity, HeightProbingHost(activity)) }
            frames()
            compose.runOnIdle {
                animate.value = true
                contentKey.value = "second"
                title.value = "Next title"
                contentHeight.value = 160.dp
            }
            frames()
            advance(250)
            compose.runOnIdle { contentHeight.value = 100.dp }
            frames()
            advance(1_000)
            compose.onNodeWithText("Next title").assertExists()
            advance(5_000)

            assertEquals(listOf("first", "second"), finished)
            assertTrue(!state.isAnimating)
            compose.onNodeWithText("Continue").assertExists()
        }
    }

    @Test
    fun secondaryCtaKeepsItsRebrandAppearanceUnderAHeightProbingViewHost() {
        assertSecondaryAppearance { HeightProbingHost(it) }
    }

    @Test
    fun secondaryCtaKeepsItsRebrandAppearanceUnderAWidthAlternatingViewHost() {
        assertSecondaryAppearance { WidthAlternatingHost(it) }
    }

    @Test
    fun ctasFadeWithoutADisabledColorJumpUnderAHeightProbingViewHost() {
        compose.mainClock.autoAdvance = false
        animate.value = true
        title.value = ""
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var view: ComposeView
            scenario.onActivity { activity -> view = showDialog(activity, HeightProbingHost(activity), secondary = true, paletteReference = true) }
            compose.assertCtasFinishWithoutColorJump(view, state) { referenceBounds }
        }
    }

    private var referenceBounds = Rect.Zero
    private var surface = Color.Unspecified

    private fun assertSecondaryAppearance(host: (ComponentActivity) -> FrameLayout) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var view: ComposeView
            scenario.onActivity { activity -> view = showDialog(activity, host(activity), secondary = true) }
            frames()
            compose.assertSecondaryCtaAppearance(view, surface)
        }
    }

    private fun showDialog(
        activity: ComponentActivity,
        host: FrameLayout,
        secondary: Boolean = false,
        paletteReference: Boolean = false,
    ): ComposeView {
        val view = ComposeView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setContent {
                DuckDuckGoTheme(isDarkTheme = false) {
                    OnboardingTheme {
                        surface = LocalOnboardingColors.current.surfaceTertiary
                        DaxOnboardingDialog(
                            contentKey = contentKey.value,
                            title = title.value,
                            state = state,
                            animate = animate.value,
                            primaryCta = DaxOnboardingDialogCta(text = "Continue", onClick = {}),
                            secondaryCta = if (secondary) DaxOnboardingDialogCta(text = "Skip for now", onClick = {}) else null,
                            onIntroFinished = { finished.add(it) },
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(contentHeight.value).then(
                                    if (paletteReference) {
                                        Modifier.background(
                                            Mandarin50,
                                        ).onGloballyPositioned { referenceBounds = it.boundsInRoot() }
                                    } else {
                                        Modifier
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
        host.addView(view)
        activity.setContentView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return view
    }

    private companion object {
        const val FrameMillis = 16L
    }

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

    /** Like a View parent that probes the natural height with an unspecified spec on every other pass. */
    private class HeightProbingHost(context: Context) : FrameLayout(context) {
        private var pass = 0

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val child = getChildAt(0)
            val width = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY)
            child.measure(width, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST))
            if (++pass % 2 == 0) child.measure(width, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            setMeasuredDimension(child.measuredWidth, child.measuredHeight)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val child = getChildAt(0)
            child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        }
    }

    /** Like a View parent that measures its child twice per pass with different widths, as the POC host does on every animation frame. */
    private class WidthAlternatingHost(context: Context) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val child = getChildAt(0)
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST)
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), height)
            child.measure(MeasureSpec.makeMeasureSpec(width - 20, MeasureSpec.EXACTLY), height)
            setMeasuredDimension(child.measuredWidth, child.measuredHeight)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val child = getChildAt(0)
            child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        }
    }
}
