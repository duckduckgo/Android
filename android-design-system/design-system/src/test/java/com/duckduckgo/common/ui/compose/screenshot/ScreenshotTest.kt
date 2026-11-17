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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base class for Roborazzi screenshot tests of design system composables.
 *
 * Images are only written or compared when Roborazzi is asked to through its Gradle tasks
 * (`recordRoborazziDebug`, `verifyRoborazziDebug`, `compareRoborazziDebug`); a plain unit test run just renders them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
abstract class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Drawables fill with `?attr/daxColor*`, which only resolve under the app theme. PreviewBox themes its own content, but
    // composables outside it and popup windows (which read the activity's context) would otherwise render those icons transparent.
    @Before
    fun applyAppTheme() {
        composeRule.activity.setTheme(R.style.Theme_DuckDuckGo_Light)
    }

    /**
     * Captures [content] at its own size, or at [widthDp] x [heightDp] when given, on a white background.
     */
    protected fun captureComposable(
        widthDp: Int? = null,
        heightDp: Int? = null,
        content: @Composable () -> Unit,
    ) {
        val sizeModifier = if (widthDp != null && heightDp != null) Modifier.size(widthDp.dp, heightDp.dp) else Modifier
        composeRule.setContent {
            Box(modifier = sizeModifier.background(Color.White)) {
                content()
            }
        }
        composeRule.onRoot().captureRoboImage()
    }

    /**
     * Captures the whole screen, including dialog and popup windows that [captureComposable] can't see.
     * The screen size comes from the Robolectric qualifiers.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    protected fun captureScreen(content: @Composable () -> Unit) {
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                content()
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage()
    }
}
