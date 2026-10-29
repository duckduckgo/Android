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

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
                startActivityAndWait(seededLaunchIntent(instrumentation))
                awaitBrowserTab(instrumentation)
                pressHome()
            },
        ) {
            uiAutomator { startApp(packageName) }
            check(device.wait(Until.hasObject(BROWSER_TAB), SCREEN_TIMEOUT_MS)) { "Browser tab was not shown" }
        }
    }

    private companion object {
        const val ITERATIONS = 20
    }
}
