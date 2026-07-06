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
import android.net.Uri
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

/**
 * Page-load benchmark: drives N sequential navigations to a deterministic, company-maintained test
 * page inside a SINGLE measured iteration, producing ONE Perfetto trace containing N `ddg.pageLoad`
 * async sections (added by the app in BrowserWebViewClient via PageLoadTraceMarker).
 *
 * We deliberately do NOT rely on the [TraceSectionMetric] value — its per-iteration windowing was
 * unreliable for an async section spanning an async navigation. The real signal is the captured
 * `.perfetto-trace`, post-processed offline (perf-benchmarks/pageload_benchmark.py) which counts
 * every `ddg.pageLoad` slice and computes the stats itself.
 *
 * Fixture: a real HTTPS publisher test page maintained by the company (PAGE_URL). A local
 * MockWebServer on localhost was tried first but DDG's browser never fetched it end-to-end, so we
 * use a real domain. This adds network variance (quantified/gated by Phase 0) but exercises the real
 * subresource/tracker interception path.
 *
 * Run on a connected device:
 *   ./gradlew :macrobenchmark:connectedReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.macrobenchmark.PageLoadBenchmark \
 *       -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class PageLoadBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun pageLoad() {
        completeOnboarding()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            // We do NOT rely on this metric — it only satisfies the framework. The real signal is
            // the captured .perfetto-trace, post-processed offline (see pageload_benchmark.py).
            metrics = listOf(TraceSectionMetric("ddg.pageLoad", TraceSectionMetric.Mode.Sum)),
            iterations = 1,
        ) {
            // Warm-up navigation (discarded in post-processing), then NAV_COUNT measured navigations.
            // Unique URL per navigation (?i=$i) forces a fresh main-frame load each time.
            repeat(NAV_COUNT + 1) { i ->
                navigateTo("$PAGE_URL?i=$i")
            }
        }
    }

    private fun navigateTo(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(TARGET_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        instrumentation.context.startActivity(intent)
        // The trace section captures the real timing; this settle just needs to reliably span the
        // load so the next navigation doesn't start before onPageFinished fires. A stuck (slow) load
        // is closed by PageLoadTraceMarker on the next onPageStarted, so worst case is one lost sample.
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
        private const val TARGET_PACKAGE = "com.duckduckgo.mobile.android"
        private const val PAGE_URL = "https://www.publisher-company.site/"
        private const val NAV_COUNT = 10
        private const val PAGE_SETTLE_MS = 8_000L
    }
}
