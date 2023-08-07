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

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark for App Startup (Onboarding). Run this benchmark on a physical device
 * from Android Studio to see startup measurements, and system trace recordings
 * for investigating app's performance.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarkOnboarding {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupOnboardingCold() = startupBenchmark(
        startupMode = StartupMode.COLD
    )

    @Test
    fun startupOnboardingWarm() = startupBenchmark(
        startupMode = StartupMode.WARM
    )

    @Test
    fun startupOnboardingHot() = startupBenchmark(
        startupMode = StartupMode.HOT
    )

    private fun startupBenchmark(
        startupMode: StartupMode,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = startupMode,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
    }
}
