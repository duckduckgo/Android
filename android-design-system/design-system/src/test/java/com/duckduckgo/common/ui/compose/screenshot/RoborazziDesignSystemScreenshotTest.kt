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

package com.duckduckgo.common.ui.compose.screenshot

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import com.duckduckgo.mobile.android.R
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every [designSystemScreenshots] case with Roborazzi.
 *
 * Images are only written when Roborazzi is asked to record (`recordRoborazziDebug`); a plain unit test run just renders them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RoborazziDesignSystemScreenshotTest(private val case: ScreenshotCase) {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun screenshot() {
        val screen = case.frame as? ScreenshotFrame.Screen
        RuntimeEnvironment.setQualifiers("w${screen?.widthDp ?: SCREEN_WIDTH_DP}dp-h${screen?.heightDp ?: SCREEN_HEIGHT_DP}dp-hdpi")
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Drawables fill with `?attr/daxColor*`, which only resolve under the app theme, and popup windows read the activity's.
                activity.setTheme(R.style.Theme_DuckDuckGo_Light)
                activity.setContent { ScreenshotFrame(case, Modifier.testTag(FRAME_TAG)) }
            }
            composeRule.waitForIdle()
            val path = "$OUTPUT_DIR/${case.name}.png"
            if (screen != null) {
                captureScreenRoboImage(path)
            } else {
                composeRule.onNodeWithTag(FRAME_TAG).captureRoboImage(path)
            }
        }
    }

    companion object {
        private const val OUTPUT_DIR = "src/test/screenshots/roborazzi"
        private const val FRAME_TAG = "screenshot-frame"
        private const val SCREEN_WIDTH_DP = 360
        private const val SCREEN_HEIGHT_DP = 640

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = designSystemScreenshots.map { arrayOf(it) }
    }
}
