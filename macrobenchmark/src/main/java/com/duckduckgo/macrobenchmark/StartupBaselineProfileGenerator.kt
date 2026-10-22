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

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app Baseline Profile through `:app:generateBaselineProfile`.
 *
 * The cold launch is also written to the startup profile used by R8 for DEX layout.
 */
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun completeOnboarding() {
        device.executeShellCommand("pm clear $TARGET_PACKAGE")
        launchWithOnboardingSkipped(instrumentation)
        check(device.wait(Until.hasObject(BROWSER_TAB), SCREEN_TIMEOUT_MS)) { "Browser tab was not shown after onboarding" }
        device.pressHome()
    }

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        stableIterations = 3,
        maxIterations = 10,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        check(device.wait(Until.hasObject(BROWSER_TAB), SCREEN_TIMEOUT_MS)) { "Browser tab was not shown" }
    }

    private companion object {
        const val SCREEN_TIMEOUT_MS = 30_000L
        val BROWSER_TAB = By.res(TARGET_PACKAGE, "newTabRootLayout")
    }
}
