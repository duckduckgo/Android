/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.macrobenchmark

import android.os.Build
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark for Browser Tab Startup. Run this benchmark on a physical device
 * from Android Studio to see startup measurements, and system trace recordings
 * for investigating app's performance.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarkBrowserTab {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupBrowserTab() = startupBenchmark(
        setupBlock = {
            launchBrowserTab()
            pressHome()
        }
    )

    private fun MacrobenchmarkScope.launchBrowserTab() {
        startActivityAndWait()

        // Handle Notifications permissions on Android 13 and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device.wait(Until.hasObject(By.text("Allow DuckDuckGo to send you notifications?")), TIMEOUT_MS)
            val allowButton = device.findObject(By.text("Allow"))
            allowButton?.run {
                click()
            }
        }

        device.wait(Until.hasObject(By.text("Let's Do it!")), TIMEOUT_MS)
        val daxPrimaryCtaButton = device.findObject(By.res(packageName, "primaryCta"))
        daxPrimaryCtaButton?.run {
            click()
            device.wait(Until.hasObject(By.text("Cancel")), TIMEOUT_MS)
            val cancelButton = device.findObject(By.text("Cancel"))
            cancelButton.click()
        }

        device.wait(Until.hasObject(By.text("Next,")), TIMEOUT_MS)
    }

    private fun startupBenchmark(
        startupMode: StartupMode= StartupMode.WARM,
        setupBlock: MacrobenchmarkScope.() -> Unit,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = startupMode,
        setupBlock = setupBlock
    ) {
        startActivityAndWait()
    }
}
