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

package com.duckduckgo.app.trackerdetection

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.trackerdetection.Client.ClientName.TDS
import com.duckduckgo.app.trackerdetection.api.ActionJsonAdapter
import com.duckduckgo.app.trackerdetection.api.TdsJson
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import com.squareup.moshi.Moshi
import okio.buffer
import okio.source
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * On-device microbenchmark mirroring [TdsClientPerfTest] (the JVM unit test).
 *
 * Compares precompileRegex=false (legacy: compile-on-every-call) versus precompileRegex=true
 * (new: compile-once-at-construction) on a real Android device or emulator, using the bundled
 * tracker dataset shipped with the app at app/src/main/res/raw/tds.json (R.raw.tds).
 *
 * NOTE: this class is annotated @Ignore so CI does not run it. To execute manually:
 *   1. From Android Studio: right-click the test method or class and choose "Run". The IDE
 *      bypasses @Ignore for explicit invocations.
 *   2. From the CLI: temporarily comment out the @Ignore annotation, run the gradle command
 *      below, then revert. JUnit honors @Ignore even when --tests targets the class directly.
 *
 *     ./gradlew :app:connectedPlayDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.app.trackerdetection.TdsClientPerfAndroidTest
 *
 * Output is emitted to logcat under tag "TdsClientPerf"; capture with:
 *     adb logcat -s TdsClientPerf:I *:S
 *
 * Numbers are observation-only — no pass/fail assertion.
 */
@Ignore("Performance benchmark — run manually, not in CI. See class kdoc for instructions.")
@RunWith(AndroidJUnit4::class)
class TdsClientPerfAndroidTest {

    private val mapper: UrlToTypeMapper = mock<UrlToTypeMapper>().also {
        whenever(it.map(anyString(), anyMap())).thenReturn("script")
    }

    @Test
    fun benchmarkRegexCompileOffVsOnUsingRealTds() {
        logRuntimeContext()
        val trackers = loadAllTrackersFromTdsJson()
        val totalRules = trackers.sumOf { it.rules.size }
        val urls = buildWorstCaseUrls(trackers, sampleSize = URL_SAMPLE_SIZE)
        val documentUrl = Uri.parse("http://example.com/index.htm")

        // JIT/ART warmup — both paths and both code branches.
        repeat(WARMUP_ROUNDS) {
            val warmOff = TdsClient(TDS, trackers, mapper, true, false)
            val warmOn = TdsClient(TDS, trackers, mapper, true, true)
            urls.forEach {
                warmOff.matches(it, documentUrl, emptyMap())
                warmOn.matches(it, documentUrl, emptyMap())
            }
        }

        // Construction cost — pre-compile work happens here when flag is on.
        val tStartConstructOff = System.nanoTime()
        val clientOff = TdsClient(TDS, trackers, mapper, true, false)
        val constructOffNs = System.nanoTime() - tStartConstructOff

        val tStartConstructOn = System.nanoTime()
        val clientOn = TdsClient(TDS, trackers, mapper, true, true)
        val constructOnNs = System.nanoTime() - tStartConstructOn

        // Match throughput.
        val matchOffNs = measureMatchNs(clientOff, urls, documentUrl)
        val matchOnNs = measureMatchNs(clientOn, urls, documentUrl)

        val totalCalls = MEASURED_ITERATIONS.toLong() * urls.size
        val perCallOffNs = matchOffNs / totalCalls
        val perCallOnNs = matchOnNs / totalCalls
        val speedup = matchOffNs.toDouble() / matchOnNs.toDouble()

        log("=== TdsClient regex pre-compile benchmark (on-device) ===")
        log(
            "Device: ${Build.MANUFACTURER} ${Build.MODEL} | " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | " +
                "ABI ${Build.SUPPORTED_ABIS.firstOrNull()}",
        )
        log("Trackers loaded: ${trackers.size} (total rules across all trackers: $totalRules)")
        log("URL sample: ${urls.size} (each hits a tracker domain but no rule matches → all rules evaluated)")
        log("Iterations: $MEASURED_ITERATIONS × ${urls.size} URLs = $totalCalls match calls per flag state")
        log("")
        log("[precompile=false] construct: ${constructOffNs / 1_000} µs | total match: ${matchOffNs / 1_000_000} ms | per-call: $perCallOffNs ns")
        log("[precompile=true ] construct: ${constructOnNs / 1_000} µs | total match: ${matchOnNs / 1_000_000} ms | per-call: $perCallOnNs ns")
        log("Match speedup: ${"%.2f".format(speedup)}x")
        log("Construction overhead (on vs off): ${(constructOnNs - constructOffNs) / 1_000_000} ms")
    }

    private fun measureMatchNs(
        client: TdsClient,
        urls: List<String>,
        documentUrl: Uri,
    ): Long {
        val start = System.nanoTime()
        repeat(MEASURED_ITERATIONS) {
            urls.forEach { client.matches(it, documentUrl, emptyMap()) }
        }
        return System.nanoTime() - start
    }

    private fun loadAllTrackersFromTdsJson(): List<TdsTracker> {
        val moshi = Moshi.Builder().add(ActionJsonAdapter()).build()
        val adapter = moshi.adapter(TdsJson::class.java)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tdsJson = context.resources.openRawResource(R.raw.tds).source().buffer().use { adapter.fromJson(it) }
            ?: error("Failed to parse R.raw.tds")
        return tdsJson.jsonToTrackers().values.toList()
    }

    /**
     * Build URLs that hit a tracker domain (so we enter matchesTrackerEntry) but with paths
     * crafted to NOT match any rule. Forces every rule's regex to be evaluated — the worst case
     * the optimization targets. Biased toward trackers with many rules to amplify the signal.
     */
    private fun buildWorstCaseUrls(trackers: List<TdsTracker>, sampleSize: Int): List<String> {
        return trackers.asSequence()
            .filter { it.rules.isNotEmpty() }
            .sortedByDescending { it.rules.size }
            .take(sampleSize)
            .map { tracker ->
                val nonce = "z9q8w7e6r5t4y3u2-${tracker.domain.value.hashCode()}"
                "http://${tracker.domain.value}/no-rule-match-path/$nonce/end"
            }
            .toList()
    }

    /**
     * Prints proof-of-environment up front so anyone reading the output knows this is genuinely
     * running on the device's ART runtime and not on a JVM.
     *
     * Key signals to verify:
     *   - vmName="Dalvik"  (Android keeps the legacy name; HotSpot would say "OpenJDK 64-Bit Server VM")
     *   - PID is an Android-side PID; the test runs inside a forked zygote process
     *   - heap.max reflects the device's heap budget (typically 512 MB on phones, larger on AVDs)
     *   - SoC details identify the silicon — useful when comparing numbers across devices later
     */
    private fun logRuntimeContext() {
        val rt = Runtime.getRuntime()
        val vmName = System.getProperty("java.vm.name") ?: "?"
        val vmVersion = System.getProperty("java.vm.version") ?: "?"
        val mb = 1024L * 1024L
        log("--- Runtime context ---")
        log("PID: ${android.os.Process.myPid()} (this is the on-device app_process running ART)")
        log("VM: $vmName $vmVersion (\"Dalvik\" = ART; not HotSpot)")
        log("Cores available: ${rt.availableProcessors()}")
        log("Heap: max=${rt.maxMemory() / mb} MB, total=${rt.totalMemory() / mb} MB, free=${rt.freeMemory() / mb} MB")
        log("Build fingerprint: ${Build.FINGERPRINT}")
        logSocDetails()
        log("-----------------------")
    }

    /**
     * Capture SoC identity. Build.SOC_MANUFACTURER/MODEL are the official source (API 31+);
     * /proc/cpuinfo gives a fallback hardware/board hint and the actual core count seen by the
     * kernel (which may differ from Runtime.availableProcessors() if the process is restricted).
     */
    private fun logSocDetails() {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} / ${Build.SOC_MODEL}"
        } else {
            "n/a (requires API 31+, this device is API ${Build.VERSION.SDK_INT})"
        }
        log("SoC: $soc")
        log("Hardware: ${Build.HARDWARE} | Board: ${Build.BOARD} | Device: ${Build.DEVICE}")
        runCatching {
            val cpuinfo = File("/proc/cpuinfo").readText()
            val processorCount = cpuinfo.lineSequence().count { it.startsWith("processor") }
            val hardwareLine = cpuinfo.lineSequence()
                .firstOrNull { it.startsWith("Hardware") }
                ?.substringAfter(":")?.trim()
            val cpuParts = cpuinfo.lineSequence()
                .filter { it.startsWith("CPU part") }
                .map { it.substringAfter(":").trim() }
                .toSet()
            log("/proc/cpuinfo: kernel-visible cores=$processorCount, hardware=\"${hardwareLine ?: "(unset)"}\", distinct CPU parts=$cpuParts")
        }.onFailure {
            log("/proc/cpuinfo: not readable (${it.message})")
        }
    }

    private fun log(message: String) {
        // Single write per line. On Android, println() would route through System.out into logcat
        // as a second entry under tag "System.out", causing duplicate lines in any unfiltered view.
        Log.i(LOG_TAG, message)
    }

    companion object {
        private const val LOG_TAG = "TdsClientPerf"
        private const val WARMUP_ROUNDS = 5
        private const val MEASURED_ITERATIONS = 500
        private const val URL_SAMPLE_SIZE = 200
    }
}
