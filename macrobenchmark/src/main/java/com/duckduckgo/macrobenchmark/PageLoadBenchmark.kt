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
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
 * POC macrobenchmark: measures page-load via the app's `ddg.pageLoad` async trace section
 * (onPageStarted -> onPageFinished, added in BrowserWebViewClient).
 *
 * Run on a connected device:
 *   ./gradlew :macrobenchmark:connectedReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.macrobenchmark.PageLoadBenchmark
 *
 * Deterministic fixture: an in-test MockWebServer serves a synthetic HTML page with ~10 subresources
 * (css/js/img/xhr), all over localhost cleartext (permitted on internal builds). No network, no
 * redirects -> one clean load per iteration, and every subresource exercises shouldInterceptRequest
 * / the URL-classification path. (It does not exercise tracker *blocking* by domain — that needs DNS
 * control — but it does exercise the per-request interception overhead, which is the measured cost.)
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
    private var loadIndex = 0

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
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
        server.shutdown()
    }

    @Test
    fun pageLoad() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(TraceSectionMetric("ddg.pageLoad", TraceSectionMetric.Mode.Sum)),
        iterations = 5,
    ) {
        // Warm app + a unique URL per iteration guarantees a fresh main-frame navigation
        // (new onPageStarted -> section) without the cold-start race that killProcess() caused.
        navigateTo("$pageUrl?i=${loadIndex++}")
    }

    private fun navigateTo(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(TARGET_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        instrumentation.context.startActivity(intent)
        device.wait(Until.hasObject(By.textContains(PAGE_MARKER)), 15_000L)
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
