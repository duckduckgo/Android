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
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.LocalOnboardingColors
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** Software drawing does not establish hardware-layer clipping behaviour. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaxOnboardingStepIndicatorHostTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val step = mutableStateOf<DaxOnboardingDialogStep?>(
        DaxOnboardingDialogStep(2, (1..5).map { "$it of 5" }.toImmutableList()),
    )
    private var surface = Color.Unspecified
    private var frameLayout: FrameLayout? = null
    private var container: ConstraintLayout? = null
    private var composeView: ComposeView? = null

    @Test
    fun pillOverhangIsNotClippedByTheComposeViewHost() {
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                val host = ConstraintLayout(activity).apply {
                    setPadding(0, (40 * density).toInt(), 0, 0)
                    clipChildren = false
                    clipToPadding = false
                }
                val view = ComposeView(activity).apply {
                    layoutParams = ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        verticalBias = 0f
                        constrainedHeight = true
                        marginStart = (16 * density).toInt()
                        marginEnd = (16 * density).toInt()
                    }
                    setContent {
                        DuckDuckGoTheme(isDarkTheme = false) {
                            OnboardingTheme {
                                surface = LocalOnboardingColors.current.surfaceTertiary
                                DaxOnboardingDialog(contentKey = "host", title = "", animate = true, step = step.value)
                            }
                        }
                    }
                }
                host.addView(view)
                val frame = FrameLayout(activity).apply {
                    setBackgroundColor(android.graphics.Color.MAGENTA)
                    addView(host, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                frameLayout = frame
                container = host
                composeView = view
                activity.setContentView(frame)
            }
            frames()
            advance(350)
            val pill = compose.onNodeWithTag("daxOnboardingStepIndicator").fetchSemanticsNode().boundsInRoot
            val x = pill.left.toInt() + checkNotNull(container).left + checkNotNull(composeView).left + 5
            val y = checkNotNull(container).top + checkNotNull(composeView).top - 2
            val mid = pixel(x, y)
            assertTrue("The overhang must fade with the shell", mid.green > 0f && mid.green < surface.green)
            advance(1_000)
            assertColor(surface, pixel(x, y))

            compose.runOnIdle { step.value = null }
            frames(2)
            val start = compose.mainClock.currentTime
            advance(144)
            val elapsed = (compose.mainClock.currentTime - start).toFloat()
            val alpha = 1f - ((cos((elapsed / 300f + 1) * PI) / 2.0) + 0.5).toFloat()
            assertColor(surface.copy(alpha = alpha).compositeOver(Color.Magenta), pixel(x, y))
            advance(400)
            compose.onNodeWithTag("daxOnboardingStepIndicator").assertDoesNotExist()
            assertColor(Color.Magenta, pixel(x, y))
        }
    }

    private fun pixel(x: Int, y: Int): Color = compose.runOnIdle {
        val frame = checkNotNull(frameLayout)
        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bitmap))
        bitmap.asImageBitmap().toPixelMap()[x, y]
    }

    private fun assertColor(expected: Color, actual: Color) {
        val distance = maxOf(abs(expected.red - actual.red), abs(expected.green - actual.green), abs(expected.blue - actual.blue))
        assertTrue("Expected $expected, got $actual", distance <= 0.03f)
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
}
