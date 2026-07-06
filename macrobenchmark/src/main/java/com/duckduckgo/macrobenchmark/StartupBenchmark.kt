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
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
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
 * POC macrobenchmark: measures cold/warm startup of the DuckDuckGo app.
 *
 * Run on a connected device:
 *   ./gradlew :macrobenchmark:connectedReleaseAndroidTest
 *
 * Target is :app's internalRelease (internal flavor): non-debuggable, profileable, debug-signed.
 *
 * Fresh-install setup: AGP installs the app fresh for each run, so we must get past onboarding
 * before measuring. We reuse the same seam the Maestro E2E suite uses — grant the notifications
 * permission (avoids the system dialog) and tap the internal-build "skipOnboardingButton" — so the
 * app lands in the browser. Onboarding-complete state then persists across the COLD iterations
 * (StartupMode.COLD kills the process but does not clear app data).
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun completeOnboarding() {
        // Avoid the POST_NOTIFICATIONS system dialog covering the app on first launch.
        device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.POST_NOTIFICATIONS")

        // Launch the app and skip onboarding via the internal-build debug button (same id the
        // Maestro flows tap). No-op if onboarding is already complete (button absent).
        val launch = instrumentation.context.packageManager
            .getLaunchIntentForPackage(TARGET_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.context.startActivity(launch)

        val skipButton = By.res(TARGET_PACKAGE, SKIP_ONBOARDING_BUTTON_ID)
        if (device.wait(Until.hasObject(skipButton), ONBOARDING_TIMEOUT_MS)) {
            device.findObject(skipButton)?.click()
            device.waitForIdle()
        }
    }

    @Test
    fun startupCold() = startup(StartupMode.COLD)

    @Test
    fun startupWarm() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = mode,
    ) {
        pressHome()
        startActivityAndWait()
    }

    companion object {
        private const val TARGET_PACKAGE = "com.duckduckgo.mobile.android"
        private const val SKIP_ONBOARDING_BUTTON_ID = "skipOnboardingButton"
        private const val ONBOARDING_TIMEOUT_MS = 20_000L
    }
}
