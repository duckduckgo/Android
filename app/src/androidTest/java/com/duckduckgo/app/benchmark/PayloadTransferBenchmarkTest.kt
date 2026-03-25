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

package com.duckduckgo.app.benchmark

import android.os.Debug
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import logcat.LogPriority.INFO
import logcat.logcat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PayloadTransferBenchmarkTest {

    private lateinit var webView: WebView
    private lateinit var loopbackServer: NanoHTTPD

    private val payloadSizesKb = listOf(100, 512, 1024, 2048, 3072, 5120, 10240, 20480, 30720, 40960, 51200, 61440)

    data class BenchmarkResult(val sizeKb: Int, val elapsedMs: Double, val heapDeltaBytes: Long, val jsHeapDeltaBytes: Long = 0L)

    @Before
    fun setUp() {
        // WebView must be created on the main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = WebView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                settings.javaScriptEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }

        // Minimal NanoHTTPD server — returns N KB of 'A' chars, CORS open for test
        loopbackServer = object : NanoHTTPD("127.0.0.1", 0) {
            override fun serve(session: IHTTPSession): Response {
                val sizeKb = session.uri.substringAfterLast("/").toIntOrNull() ?: 0
                val data = "A".repeat(sizeKb * 1024)
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, data).apply {
                    addHeader("Access-Control-Allow-Origin", "*")
                }
            }
        }
        loopbackServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { webView.destroy() }
        loopbackServer.stop()
    }

    /** Serves a payload of `sizeKb` KB as a plain string — the thing being benchmarked. */
    inner class PayloadBridge {
        @JavascriptInterface
        fun getPayload(sizeKb: Int): String = "A".repeat(sizeKb * 1024)
    }

    private fun measureBridge(sizeKb: Int): BenchmarkResult {
        val latch = CountDownLatch(1)
        val sink = mutableListOf<BenchmarkResult>()
        var heapBefore = 0L // set after page load, before evaluateJavascript

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.removeJavascriptInterface("PayloadBridge")
            webView.removeJavascriptInterface("ResultBridge")
            webView.addJavascriptInterface(PayloadBridge(), "PayloadBridge")
            webView.addJavascriptInterface(
                object : Any() {
                    @JavascriptInterface
                    fun report(sizeKb: Int, elapsedMs: Double, jsHeapDelta: Long) {
                        val heapDelta = Debug.getNativeHeapAllocatedSize() - heapBefore
                        sink += BenchmarkResult(sizeKb, elapsedMs, heapDelta, jsHeapDelta)
                        latch.countDown()
                    }
                },
                "ResultBridge",
            )
            // Load a blank page so the bridge is reachable
            webView.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "utf-8", null)
        }

        Thread.sleep(500) // wait for page to initialise

        Runtime.getRuntime().gc()
        System.gc()
        Thread.sleep(100) // let GC settle before baseline
        heapBefore = Debug.getNativeHeapAllocatedSize() // baseline: after page load, before transfer

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(
                """
                (function() {
                    var memBefore = (performance.memory) ? performance.memory.usedJSHeapSize : 0;
                    var start = performance.now();
                    var data = PayloadBridge.getPayload($sizeKb);
                    var elapsed = performance.now() - start;
                    var memAfter = (performance.memory) ? performance.memory.usedJSHeapSize : 0;
                    ResultBridge.report($sizeKb, elapsed, memAfter - memBefore);
                })();
                """.trimIndent(),
                null,
            )
        }

        check(latch.await(30, TimeUnit.SECONDS)) { "Timed out waiting for JS bridge result ($sizeKb KB)" }
        return sink.first()
    }

    @Test
    fun benchmark_jsBridge() {
        measureBridge(1) // warm-up: discard first call to allow JIT to settle
        val results = payloadSizesKb.map { measureBridgeMedian(it) }
        logcat(INFO) { "=== JS BRIDGE BENCHMARK ===" }
        logcat(INFO) { "${"Size (KB)".padStart(9)} | ${"Elapsed (ms)".padStart(12)} | ${"Native heap".padStart(11)} | JS heap delta" }
        results.forEach {
            logcat(INFO) { "${it.sizeKb.toString().padStart(9)} | ${it.elapsedMs.toString().padStart(12)} | ${it.heapDeltaBytes.toString().padStart(11)} | ${it.jsHeapDeltaBytes}" }
        }
    }

    private fun measureLoopback(sizeKb: Int): BenchmarkResult {
        val latch = CountDownLatch(1)
        val sink = mutableListOf<BenchmarkResult>()
        val port = loopbackServer.listeningPort

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.removeJavascriptInterface("ResultBridge")
            webView.addJavascriptInterface(
                object : Any() {
                    @JavascriptInterface
                    fun report(sizeKb: Int, elapsedMs: Double, jsHeapDelta: Long) {
                        sink += BenchmarkResult(sizeKb, elapsedMs, 0L, jsHeapDelta)
                        latch.countDown()
                    }
                },
                "ResultBridge",
            )
            webView.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "utf-8", null)
        }

        Thread.sleep(500) // wait for page to initialise

        Runtime.getRuntime().gc()
        System.gc()
        Thread.sleep(100) // let GC settle before baseline
        val heapBefore = Debug.getNativeHeapAllocatedSize()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(
                """
                (function() {
                    var memBefore = (performance.memory) ? performance.memory.usedJSHeapSize : 0;
                    var start = performance.now();
                    fetch('http://127.0.0.1:$port/benchmark/$sizeKb')
                        .then(function(r) { return r.text(); })
                        .then(function(data) {
                            var elapsed = performance.now() - start;
                            var memAfter = (performance.memory) ? performance.memory.usedJSHeapSize : 0;
                            ResultBridge.report($sizeKb, elapsed, memAfter - memBefore);
                        });
                })();
                """.trimIndent(),
                null,
            )
        }

        check(latch.await(30, TimeUnit.SECONDS)) { "Timed out waiting for loopback result ($sizeKb KB)" }
        return sink.first().copy(heapDeltaBytes = Debug.getNativeHeapAllocatedSize() - heapBefore)
    }

    @Test
    fun benchmark_loopback() {
        measureLoopback(1) // warm-up
        val results = payloadSizesKb.map { measureLoopbackMedian(it) }
        logcat(INFO) { "=== LOOPBACK HTTP BENCHMARK ===" }
        logcat(INFO) { "${"Size (KB)".padStart(9)} | ${"Elapsed (ms)".padStart(12)} | ${"Native heap".padStart(11)} | JS heap delta" }
        results.forEach {
            logcat(INFO) { "${it.sizeKb.toString().padStart(9)} | ${it.elapsedMs.toString().padStart(12)} | ${it.heapDeltaBytes.toString().padStart(11)} | ${it.jsHeapDeltaBytes}" }
        }
    }

    private fun measureBridgeMedian(sizeKb: Int, iterations: Int = 3): BenchmarkResult {
        val samples = (1..iterations).map { measureBridge(sizeKb) }
        return samples.sortedBy { it.elapsedMs }[iterations / 2]
    }

    private fun measureLoopbackMedian(sizeKb: Int, iterations: Int = 3): BenchmarkResult {
        val samples = (1..iterations).map { measureLoopback(sizeKb) }
        return samples.sortedBy { it.elapsedMs }[iterations / 2]
    }

    @Test
    fun benchmark_comparison() {
        // warm-up: discard first call to allow JIT to settle before timed measurements
        measureBridge(1)
        measureLoopback(1)
        logcat(INFO) { "=== PAYLOAD TRANSFER COMPARISON: JS BRIDGE vs LOOPBACK HTTP ===" }
        logcat(INFO) {
            "${"Size (KB)".padStart(9)} | ${"Bridge (ms)".padStart(11)} | ${"Loopback (ms)".padStart(13)} | ${"Bridge JS heap".padStart(14)} | Loopback JS heap"
        }
        payloadSizesKb.forEach { sizeKb ->
            val bridge = measureBridgeMedian(sizeKb)
            val loopback = measureLoopbackMedian(sizeKb)
            logcat(INFO) {
                "${sizeKb.toString().padStart(9)} | " +
                    "${bridge.elapsedMs.toString().padStart(11)} | " +
                    "${loopback.elapsedMs.toString().padStart(13)} | " +
                    "${bridge.jsHeapDeltaBytes.toString().padStart(14)} | " +
                    loopback.jsHeapDeltaBytes
            }
        }
    }
}
