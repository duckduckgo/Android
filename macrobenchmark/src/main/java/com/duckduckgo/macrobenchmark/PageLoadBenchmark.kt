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
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.Collections
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Page-load benchmark: drives N sequential navigations over a deterministic synthetic page inside a
 * SINGLE measured iteration, producing ONE Perfetto trace containing N `ddg.pageLoad` async sections
 * (added by the app in BrowserWebViewClient via PageLoadTraceMarker).
 *
 * We deliberately do NOT rely on the [TraceSectionMetric] value — its per-iteration windowing was
 * unreliable for an async section spanning an async navigation. The real signal is the captured
 * `.perfetto-trace`, post-processed offline (perf-benchmarks/pageload_benchmark.py) which counts
 * every `ddg.pageLoad` slice and computes the stats itself.
 *
 * Run on a connected device:
 *   ./gradlew :macrobenchmark:connectedReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.macrobenchmark.PageLoadBenchmark
 *
 * Deterministic fixture: an in-test MockWebServer serves a synthetic HTML page with ~10 subresources
 * (css/js/img/xhr), all over localhost cleartext (permitted on internal builds). No network, no
 * redirects -> one clean load per navigation, and every subresource exercises shouldInterceptRequest.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class PageLoadBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var server: MockWebServer
    private lateinit var pageUrl: String

    // Records subresource paths served, so we can assert interception actually fired (setup guard).
    private val servedPaths = Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    servedPaths += path
                    return when {
                        path.endsWith(".css") -> ok("text/css", "body{background:#fff;font-family:sans-serif}")
                        path.endsWith(".js") -> ok("application/javascript", "console.log('bench')")
                        path.endsWith(".png") -> ok("image/png", "")
                        path.startsWith("/xhr") -> ok("application/json", "{\"ok\":true}")
                        else -> ok("text/html", PAGE_HTML)
                    }
                }
            }
            start()
        }
        pageUrl = server.url("/index.html").toString()

        completeOnboarding()
    }

    @After
    fun tearDown() {
        assertInterceptionExercised()
        server.shutdown()
    }

    @Test
    fun pageLoad() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        // We do NOT rely on this metric — it only satisfies the framework. The real signal is
        // the captured .perfetto-trace, post-processed offline (see pageload_benchmark.py).
        metrics = listOf(TraceSectionMetric("ddg.pageLoad", TraceSectionMetric.Mode.Sum)),
        iterations = 1,
    ) {
        // Warm-up navigation (discarded in post-processing), then NAV_COUNT measured navigations.
        // Unique URL per navigation (?i=$i) guarantees a fresh main-frame load each time.
        repeat(NAV_COUNT + 1) { i ->
            navigateViaOmnibar("$pageUrl?i=$i")
        }
    }

    private fun navigateViaOmnibar(url: String) {
        val omnibar = By.res(TARGET_PACKAGE, "omnibarTextInput")
        device.wait(Until.hasObject(omnibar), 10_000L)
        val field = device.findObject(omnibar)
        field.click()
        device.waitForIdle()
        field.text = "" // clear any current URL
        field.text = url
        device.pressEnter()
        // Deterministic wait for the synthetic page to finish rendering.
        device.wait(Until.hasObject(By.textContains(PAGE_MARKER)), 15_000L)
    }

    private fun assertInterceptionExercised() {
        check(servedPaths.any { it.endsWith(".css") } && servedPaths.any { it.startsWith("/xhr") }) {
            "shouldInterceptRequest did not fire for subresources — measuring the wrong thing. Served: $servedPaths"
        }
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

    private fun ok(contentType: String, body: String) =
        MockResponse()
            .setHeader("Content-Type", contentType)
            .setHeader("Cache-Control", "no-store")
            .setBody(body)

    companion object {
        private const val TARGET_PACKAGE = "com.duckduckgo.mobile.android"
        private const val PAGE_MARKER = "Benchmark Page"
        private const val NAV_COUNT = 10
        private val PAGE_HTML = """
            <!doctype html><html><head>
            <link rel="stylesheet" href="/a.css">
            <link rel="stylesheet" href="/b.css">
            <script src="/a.js"></script>
            <script src="/b.js"></script>
            </head><body>
            <h1>Benchmark Page</h1>
            <img src="/img1.png"><img src="/img2.png"><img src="/img3.png">
            <script>fetch('/xhr1');fetch('/xhr2');</script>
            </body></html>
        """.trimIndent()
    }
}
