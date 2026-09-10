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

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal const val TARGET_PACKAGE = "com.duckduckgo.mobile.android"

/**
 * Page-load benchmark: drives N sequential navigations to a deterministic test page inside a SINGLE
 * measured iteration, producing one Perfetto trace containing N `ddg.pageLoad` async sections
 * (added by the app in BrowserWebViewClient via PageLoadTraceMarker).
 *
 * We deliberately do NOT rely on the [TraceSectionMetric] value, its per-iteration windowing was
 * unreliable for an async section spanning an async navigation. The real signal is the captured
 * `.perfetto-trace`, post-processed offline (perf-benchmarks/pageload_benchmark.py) which counts
 * every `ddg.pageLoad` slice and computes the stats itself.
 *
 * Run on a connected device:
 *   ./gradlew :macrobenchmark:connectedReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.macrobenchmark.PageLoadBenchmark \
 *       -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
 *
 * Pass -Pandroid.testInstrumentationRunnerArguments.pageUrl=<url> while selecting one test method
 * to navigate to an explicit URL instead of its on-device fixture.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class PageLoadBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val explicitPageUrl = InstrumentationRegistry.getArguments().getString("pageUrl")

    @Test
    fun noTrackers() = measurePageLoad(NoTrackersFixture)

    @Test
    fun manyTrackersBlocked() = measurePageLoad(ManyTrackersBlockedFixture)

    @Test
    fun firstPartyTrackers() = measurePageLoad(FirstPartyTrackersFixture)

    @Test
    fun cpm() = measurePageLoad(CpmFixture)

    @Test
    fun allScenarios() = measurePageLoad(AllScenariosFixture)

    private fun measurePageLoad(fixture: PageLoadFixture) {
        completeOnboarding()
        val fixtureServer = if (explicitPageUrl == null) PageLoadFixtureServer(fixture).also { it.start() } else null
        val pageUrl = explicitPageUrl ?: fixtureServer!!.baseUrl
        try {
            benchmarkRule.measureRepeated(
                packageName = TARGET_PACKAGE,
                metrics = listOf(TraceSectionMetric("ddg.pageLoad", TraceSectionMetric.Mode.Sum)),
                iterations = 1,
            ) {
                // +1: leading warmup navigation, discarded by position in post-processing.
                // ?i=$i forces a fresh main-frame load each time.
                repeat(NAV_COUNT + 1) { i ->
                    navigateTo("$pageUrl?i=$i")
                }
                // Closes the last measured navigation's slice; its own slice is the trailing sample,
                // also discarded by position.
                fixtureServer?.let { navigateTo(it.traceSentinelUrl) }
            }
        } finally {
            fixtureServer?.shutdown()
        }
    }

    private fun navigateTo(url: String) {
        val intent = Intent().apply {
            component = ComponentName(TARGET_PACKAGE, BROWSER_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(OPEN_IN_CURRENT_TAB_EXTRA, true)
        }
        instrumentation.context.startActivity(intent)
        // Just needs to reliably span the load so the next navigation doesn't start before
        // onPageFinished fires; a stuck load costs at most one lost sample.
        SystemClock.sleep(PAGE_SETTLE_MS)
    }

    private fun completeOnboarding() {
        device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.POST_NOTIFICATIONS")
        val launch = instrumentation.context.packageManager
            .getLaunchIntentForPackage(TARGET_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.context.startActivity(launch)
        val skipButton = By.res(TARGET_PACKAGE, "skipOnboardingButton")
        if (device.wait(Until.hasObject(skipButton), 20_000L)) {
            device.findObject(skipButton)?.click()
            device.waitForIdle()
        }
    }

    companion object {
        private const val NAV_COUNT = 10
        private const val PAGE_SETTLE_MS = 8_000L
        private const val BROWSER_ACTIVITY = "com.duckduckgo.app.browser.BrowserActivity"
        private const val OPEN_IN_CURRENT_TAB_EXTRA = "OPEN_IN_CURRENT_TAB_EXTRA"
    }
}
