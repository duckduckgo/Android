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

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.trackerdetection.Client.ClientName.TDS
import com.duckduckgo.app.trackerdetection.api.ActionJsonAdapter
import com.duckduckgo.app.trackerdetection.api.TdsJson
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import com.squareup.moshi.Moshi
import okio.buffer
import okio.source
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import java.io.File
import kotlin.random.Random

/**
 * Microbenchmark comparing V2 (Domain-based linear scan with cached sameOrSubdomain)
 * and V3 (HashMap label-walk) lookup paths in [TdsClient.matches], using the actual
 * TDS dataset bundled in the app (`R.raw.tds`).
 *
 * Not part of the regular test suite — the @Test method is @Ignore'd. To run, remove
 * the @Ignore annotation on `benchmarkV2vsV3`, then:
 *
 *   JAVA_HOME=/path/to/java21 ./gradlew :app:testPlayDebugUnitTest \
 *     --tests "com.duckduckgo.app.trackerdetection.TdsClientBenchmark" \
 *     --info
 *
 * The `--info` flag is what makes the println output visible in Gradle's stdout.
 *
 * URL mix:
 *  - 90% non-tracker URLs (synthetic `nontrackerN.example` hosts, guaranteed misses
 *    against any real TDS entry — the dominant case the optimization targets).
 *  - 10% tracker URLs (sampled deterministically from the loaded TDS list, half as
 *    exact-host requests, half as `cdn.<tracker>` subdomain requests).
 *
 * Caveats:
 *  - Robolectric (AndroidJUnit4) adds JVM-level overhead vs pure-JVM benchmarks.
 *    Absolute numbers are noisier than JMH; ratios are still meaningful.
 *  - The V2 path's `sameOrSubdomain(Domain, Domain)` cache (250k LRU) warms up
 *    quickly on a fixed URL set, so V2's measured cost reflects the warm-cache
 *    case — its best showing.
 *
 * For end-to-end production validation, query the page-load wide event in the
 * data warehouse with `tracker_optimization_enabled_v3` as the discriminator.
 */
@Ignore("Microbenchmark — remove this annotation and run with --info to see results")
@RunWith(AndroidJUnit4::class)
class TdsClientBenchmark {

    private val mockUrlToTypeMapper: UrlToTypeMapper = mock()

    private lateinit var trackers: List<TdsTracker>
    private lateinit var urls: List<String>

    @Before
    fun loadTds() {
        val moshi = Moshi.Builder().add(ActionJsonAdapter()).build()
        val adapter = moshi.adapter(TdsJson::class.java)
        val tdsJson = locateTdsFile().source().buffer().use { adapter.fromJson(it) }!!
        trackers = tdsJson.jsonToTrackers().values.toList()
        urls = buildUrlMix(trackers)
    }

    private fun locateTdsFile(): File {
        // Test runner CWD is usually the module root (`app/`); fall back to repo root.
        listOf("src/main/res/raw/tds.json", "app/src/main/res/raw/tds.json").forEach {
            val file = File(it)
            if (file.exists()) return file
        }
        error("Could not locate tds.json. CWD: ${File(".").absolutePath}")
    }

    @Test
    fun benchmarkV2vsV3() {
        val v2Client = TdsClient(TDS, trackers, mockUrlToTypeMapper, optimizeTrackerEvaluationV3 = false)
        val v3Client = TdsClient(TDS, trackers, mockUrlToTypeMapper, optimizeTrackerEvaluationV3 = true)

        repeat(WARMUP_ITERATIONS) {
            urls.forEach { v2Client.matches(it, DOCUMENT_URL, mapOf()) }
            urls.forEach { v3Client.matches(it, DOCUMENT_URL, mapOf()) }
        }

        val v2Total = measureNanos(v2Client)
        val v3Total = measureNanos(v3Client)

        val totalCalls = (MEASUREMENT_ITERATIONS * urls.size).toLong()
        val v2PerCall = v2Total / totalCalls
        val v3PerCall = v3Total / totalCalls

        println("===== TdsClient V2 vs V3 microbenchmark =====")
        println("Trackers loaded from tds.json:   ${trackers.size}")
        println("URL mix size:                    ${urls.size} (90% non-tracker, 10% tracker)")
        println("Warmup iterations:               $WARMUP_ITERATIONS")
        println("Measurement iterations:          $MEASUREMENT_ITERATIONS")
        println("Total calls per path:            $totalCalls")
        println()
        println("V2 (linear + cached sameOrSubdomain): $v2PerCall ns/call")
        println("V3 (HashMap label-walk):              $v3PerCall ns/call")
        if (v3PerCall > 0L) {
            val speedup = v2PerCall.toDouble() / v3PerCall.toDouble()
            println("Speedup:                              ${"%.1f".format(speedup)}x")
        }
        println("=============================================")
    }

    private fun measureNanos(client: TdsClient): Long {
        val start = System.nanoTime()
        repeat(MEASUREMENT_ITERATIONS) {
            urls.forEach { client.matches(it, DOCUMENT_URL, mapOf()) }
        }
        return System.nanoTime() - start
    }

    private fun buildUrlMix(trackers: List<TdsTracker>): List<String> {
        val random = Random(SEED)
        val nonTracker = List(URL_MIX_NON_TRACKER) {
            "https://nontracker$it.example/script.js"
        }
        val sampledTrackers = trackers.shuffled(random).take(URL_MIX_TRACKER)
        val trackerUrls = sampledTrackers.mapIndexed { idx, tracker ->
            if (idx % 2 == 0) {
                "https://${tracker.domain.value}/script.js"
            } else {
                "https://cdn.${tracker.domain.value}/script.js"
            }
        }
        return nonTracker + trackerUrls
    }

    companion object {
        private const val URL_MIX_NON_TRACKER = 180
        private const val URL_MIX_TRACKER = 20
        private const val WARMUP_ITERATIONS = 5
        private const val MEASUREMENT_ITERATIONS = 500
        private const val SEED = 42L

        private val DOCUMENT_URL = "https://example.com/page".toUri()
    }
}
