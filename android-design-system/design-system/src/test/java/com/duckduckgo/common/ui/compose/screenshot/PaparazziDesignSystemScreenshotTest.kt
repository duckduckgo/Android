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

import androidx.compose.runtime.Recomposer
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** Renders every [designSystemScreenshots] case with Paparazzi. */
@RunWith(Parameterized::class)
class PaparazziDesignSystemScreenshotTest(private val case: ScreenshotCase) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = screen(SCREEN_WIDTH_DP, SCREEN_HEIGHT_DP),
        theme = THEME,
        renderingMode = RenderingMode.SHRINK,
    )

    @Test
    fun screenshot() {
        val screen = case.frame as? ScreenshotFrame.Screen
        paparazzi.unsafeUpdateConfig(
            deviceConfig = screen(screen?.widthDp ?: SCREEN_WIDTH_DP, screen?.heightDp ?: SCREEN_HEIGHT_DP),
            theme = THEME,
            renderingMode = if (screen != null) RenderingMode.NORMAL else RenderingMode.SHRINK,
        )
        openWindowsInTouchMode()
        // Paparazzi never advances Compose's frame clock, so anything that animates in (DropdownMenu, ModalBottomSheet) would be
        // captured at its first, fully transparent frame. A zero MotionDurationScale makes those animations finish on their first
        // frame, matching the settled state Roborazzi and Compose Preview Screenshot Testing capture.
        val instantAnimations = AndroidUiDispatcher.CurrentThread + object : MotionDurationScale {
            override val scaleFactor = 0f
        }
        // The recomposer is deliberately never cancelled: cancelling it leaves later snapshots in the same JVM stuck on their first
        // animation frame again. Each test class runs in its own JVM (forkEvery = 1), so the idle recomposers go away with it.
        val recomposer = Recomposer(instantAnimations)
        CoroutineScope(instantAnimations).launch(start = CoroutineStart.UNDISPATCHED) { recomposer.runRecomposeAndApplyChanges() }
        val view = ComposeView(paparazzi.context).apply {
            setParentCompositionContext(recomposer)
            setContent { ScreenshotFrame(case) }
        }
        paparazzi.snapshot(view, case.name)
    }

    companion object {
        private const val THEME = "Theme.DuckDuckGo.Light"
        private const val SCREEN_WIDTH_DP = 360
        private const val SCREEN_HEIGHT_DP = 640
        private const val DPI = 240

        private fun screen(widthDp: Int, heightDp: Int) = DeviceConfig.PIXEL_5.copy(
            screenWidth = widthDp * DPI / 160,
            screenHeight = heightDp * DPI / 160,
            xdpi = DPI,
            ydpi = DPI,
            density = Density.HIGH,
            orientation = if (widthDp > heightDp) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
        )

        /**
         * Makes dialog and popup windows start in touch mode, as they would on a phone.
         *
         * A new window takes its touch mode from the flags its window session returns when it's added, and layoutlib's session never
         * sets the touch-mode flag. Out of touch mode, a dialog gives keyboard focus to its first control, so focus highlights would
         * show up that Roborazzi and Compose Preview Screenshot Testing (both in touch mode) never draw. Hidden APIs are reached by
         * reflection because they're not in the compile-time android.jar.
         */
        private fun openWindowsInTouchMode() {
            val windowManagerGlobal = Class.forName("android.view.WindowManagerGlobal")
            val sessionField = windowManagerGlobal.getDeclaredField("sWindowSession").apply { isAccessible = true }
            windowManagerGlobal.getMethod("getWindowSession").invoke(null)
            val session = sessionField.get(null)
            if (Proxy.isProxyClass(session.javaClass)) return

            val sessionInterface = Class.forName("android.view.IWindowSession")
            val inTouchModeFlag = windowManagerGlobal.getField("ADD_FLAG_IN_TOUCH_MODE").getInt(null)
            val touchModeSession = Proxy.newProxyInstance(sessionInterface.classLoader, arrayOf(sessionInterface)) { _, method, args ->
                val result = try {
                    method.invoke(session, *(args ?: emptyArray()))
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
                if (method.name.startsWith("addToDisplay") && result is Int) result or inTouchModeFlag else result
            }
            sessionField.set(null, touchModeSession)
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = designSystemScreenshots.map { arrayOf(it) }
    }
}
