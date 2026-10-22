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

package com.duckduckgo.macrobenchmark

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.uiAutomator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Measures ordinary cold startup after onboarding has been completed. Run each method on a physical device. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun completeOnboardingIfNeeded() {
        val launchIntent = checkNotNull(instrumentation.context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.context.startActivity(launchIntent)

        val skipButton = By.res(TARGET_PACKAGE, "skipOnboardingButton")
        val deadline = SystemClock.elapsedRealtime() + SCREEN_TIMEOUT_MS
        while (!device.hasObject(BROWSER_TAB) && !device.hasObject(skipButton)) {
            check(SystemClock.elapsedRealtime() < deadline) { "Neither onboarding nor the browser tab was shown" }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        if (!device.hasObject(BROWSER_TAB)) {
            device.findObject(skipButton)?.click()
            check(device.wait(Until.hasObject(BROWSER_TAB), SCREEN_TIMEOUT_MS)) { "Browser tab was not shown after onboarding" }
        }
    }

    @Test
    fun noCompilation() = measureStartup(CompilationMode.None())

    @Test
    fun partialNoProfile() = measureStartup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Disable, warmupIterations = 3),
    )

    @Test
    fun baselineProfile() = measureStartup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    )

    @Test
    fun fullCompilation() = measureStartup(CompilationMode.Full())

    private fun measureStartup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                // Compilation reset reinstalls the app on unrooted devices, clearing onboarding, so re-check every time.
                completeOnboardingIfNeeded()
                device.executeShellCommand("am force-stop $TARGET_PACKAGE")
                pressHome()
            },
        ) {
            uiAutomator { startApp(packageName) }
            check(device.wait(Until.hasObject(BROWSER_TAB), SCREEN_TIMEOUT_MS)) { "Browser tab was not shown" }
        }
    }

    private companion object {
        val BROWSER_TAB = By.res(TARGET_PACKAGE, "newTabRootLayout")
        const val ITERATIONS = 20
        const val SCREEN_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 250L
    }
}
