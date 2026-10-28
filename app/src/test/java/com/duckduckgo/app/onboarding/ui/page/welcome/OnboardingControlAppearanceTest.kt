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
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboardingquicksetup.ui.QuickSetupSwitchRow
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import kotlin.math.abs
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w1000dp-h1200dp-450dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingControlAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun radioMatchesNativeInBothStatesAndThemes() = compareControl(radio = true)

    @Test fun switchMatchesNativeInBothStatesAndThemes() = compareControl(radio = false)

    @Test fun radioUpdatesItsVisibleStateAfterSelectionAndDeselection() = compareControl(radio = true, recreateEachState = false)

    private fun compareControl(radio: Boolean, recreateEachState: Boolean = true) {
        val checked = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val app = ApplicationProvider.getApplicationContext<Application>()
        var drawingView: View? = null
        fun context() = ContextThemeWrapper(
            app,
            if (dark.value) CommonR.style.Theme_DuckDuckGo_Dark_Onboarding else CommonR.style.Theme_DuckDuckGo_Light_Onboarding,
        )
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context()) {
                DuckDuckGoTheme(isDarkTheme = dark.value) {
                    OnboardingTheme {
                        drawingView = LocalView.current
                        Box(Modifier.background(Color.White)) {
                            key(dark.value, if (recreateEachState) checked.value else false) {
                                if (radio) {
                                    OnboardingRadioButton(checked.value, Modifier.testTag("control"))
                                } else {
                                    OnboardingSwitch(checked.value, Modifier.testTag("control"))
                                }
                            }
                        }
                    }
                }
            }
        }
        listOf(false, true).forEach { night ->
            (if (recreateEachState) listOf(false, true) else listOf(false, true, false)).forEach { selected ->
                compose.runOnIdle {
                    dark.value = night
                    checked.value = selected
                }
                if (recreateEachState) {
                    compose.mainClock.advanceTimeBy(320)
                } else {
                    val view = checkNotNull(drawingView)
                    val frame = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    try {
                        repeat(40) {
                            compose.mainClock.advanceTimeByFrame()
                            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                            compose.runOnIdle { view.draw(Canvas(frame)) }
                        }
                    } finally {
                        frame.recycle()
                    }
                }
                compose.waitForIdle()
                val bounds = compose.onNodeWithTag("control", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val native = if (radio) {
                    LayoutInflater.from(context()).inflate(R.layout.include_brand_design_single_choice_row, null)
                        .findViewById<CompoundButton>(R.id.singleChoiceRowRadioButton)
                } else {
                    QuickSetupSwitchRow(context()).findViewById<CompoundButton>(R.id.quickSetupSwitchRowSwitch)
                }
                native.isChecked = selected
                native.jumpDrawablesToCurrentState()
                native.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                assertEquals(native.measuredWidth.toFloat(), bounds.width, .1f)
                assertEquals(native.measuredHeight.toFloat(), bounds.height, .1f)
                native.layout(0, 0, native.measuredWidth, native.measuredHeight)
                compose.runOnIdle {
                    val view = checkNotNull(drawingView)
                    val actual = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val expected = Bitmap.createBitmap(native.width, native.height, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(actual))
                        expected.eraseColor(android.graphics.Color.WHITE)
                        native.draw(Canvas(expected))
                        var delta = 0L
                        var ink = 0
                        repeat(native.height) { y ->
                            repeat(native.width) { x ->
                                val reference = expected.getPixel(x, y)
                                val candidate = actual.getPixel(bounds.left.toInt() + x, bounds.top.toInt() + y)
                                if (reference != android.graphics.Color.WHITE) ink++
                                for (shift in listOf(0, 8, 16)) delta += abs((reference shr shift and 255) - (candidate shr shift and 255))
                            }
                        }
                        assertTrue("Native control must be visible", ink > 20)
                        val score = 100 * (1 - delta.toDouble() / (native.width * native.height * 3 * 255))
                        if (score < 99) {
                            val output = java.io.File("build/onboarding-control-comparison").apply { mkdirs() }
                            val label = "radio-$radio-dark-$night-checked-$selected"
                            java.io.File(output, "$label-native.png").outputStream().use { expected.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            val crop = Bitmap.createBitmap(actual, bounds.left.toInt(), bounds.top.toInt(), native.width, native.height)
                            try {
                                java.io.File(output, "$label-compose.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            } finally {
                                crop.recycle()
                            }
                        }
                        assertTrue("radio=$radio dark=$night checked=$selected score=$score", score >= 99)
                    } finally {
                        actual.recycle()
                        expected.recycle()
                    }
                }
            }
        }
    }
}
