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

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.trackerdetection.api.ActionJsonAdapter
import com.duckduckgo.app.trackerdetection.api.TdsJson
import com.duckduckgo.app.trackerdetection.db.TdsDomainEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsEntityDao
import com.squareup.moshi.Moshi
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device microbenchmark comparing [TdsEntityLookup] (DB-walking) against
 * [CachedTdsEntityLookup] (map-walking) on a real Room database seeded with the
 * production tracker dataset bundled in the app at app/src/main/res/raw/tds.json
 * (R.raw.tds).
 *
 * The two impls share the same `EntityLookup` surface — they differ only in how
 * `entityForUrl` resolves a host:
 *   - legacy: recursive DAO walk (2 queries per subdomain level)
 *   - cached: in-memory HashMap label walk (1 hash lookup per level)
 *
 * Three URL workloads exercise the dominant real-world cases:
 *   1. EXACT — host equals a known tracker domain (one lookup either way).
 *   2. DEEP — host is a deep subdomain of a known tracker domain (label walk).
 *   3. MISS — host has no entry anywhere in the data (worst case; eTLD-bounded
 *      walk until no match).
 *
 * NOTE: this class is annotated @Ignore so CI does not run it. To execute manually:
 *   1. From Android Studio: right-click the test method or class and choose "Run".
 *      The IDE bypasses @Ignore for explicit invocations.
 *   2. From the CLI: temporarily comment out the @Ignore annotation, run the gradle
 *      command below, then revert. JUnit honors @Ignore even when --tests targets
 *      the class directly.
 *
 *     ./gradlew :app:connectedPlayDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.app.trackerdetection.CachedTdsEntityLookupPerfAndroidTest
 *
 * Output is emitted to logcat under tag "EntityLookupPerf"; capture with:
 *     adb logcat -s EntityLookupPerf:I *:S
 *
 * Numbers are observation-only — no pass/fail assertion.
 */
// @Ignore("Performance benchmark — run manually, not in CI. See class kdoc for instructions.")
@RunWith(AndroidJUnit4::class)
class CachedTdsEntityLookupPerfAndroidTest {

    private lateinit var db: AppDatabase
    private lateinit var entityDao: TdsEntityDao
    private lateinit var domainEntityDao: TdsDomainEntityDao
    private lateinit var legacy: TdsEntityLookup
    private lateinit var cached: CachedTdsEntityLookup

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        entityDao = db.tdsEntityDao()
        domainEntityDao = db.tdsDomainEntityDao()

        val tdsJson = loadTdsJson()
        entityDao.insertAll(tdsJson.jsonToEntities())
        domainEntityDao.insertAll(tdsJson.jsonToDomainEntities())

        legacy = TdsEntityLookup(entityDao, domainEntityDao)
        cached = CachedTdsEntityLookup(entityDao, domainEntityDao).also { it.refresh() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun benchmarkLegacyVsCachedAcrossWorkloads() {
        logRuntimeContext()

        val knownDomains: List<String> = domainEntityDao.getAll().map { it.domain }
        val entityCount = entityDao.getAll().size
        val urls = buildUrlSample(knownDomains)

        log("--- Dataset ---")
        log("Entities loaded: $entityCount")
        log("Domain-entity rows: ${knownDomains.size}")
        log("URL sample: ${urls.exact.size} EXACT + ${urls.deep.size} DEEP + ${urls.miss.size} MISS = ${urls.total} URLs")
        log("Measured iterations per workload: $MEASURED_ITERATIONS")
        log("---------------")

        // JIT / ART warmup — both impls, all three workloads.
        repeat(WARMUP_ROUNDS) {
            runWorkload(legacy, urls)
            runWorkload(cached, urls)
        }

        val legacyResults = measureAllWorkloads(legacy)
        val cachedResults = measureAllWorkloads(cached)

        log("")
        log("=== EntityLookup benchmark (on-device) ===")
        logWorkloadComparison("EXACT (host equals known tracker domain)", legacyResults.exact, cachedResults.exact, urls.exact.size)
        logWorkloadComparison("DEEP  (deep subdomain of known tracker)", legacyResults.deep, cachedResults.deep, urls.deep.size)
        logWorkloadComparison("MISS  (host with no entry — worst case)", legacyResults.miss, cachedResults.miss, urls.miss.size)

        val legacyTotalNs = legacyResults.exact + legacyResults.deep + legacyResults.miss
        val cachedTotalNs = cachedResults.exact + cachedResults.deep + cachedResults.miss
        val totalCalls = urls.total.toLong() * MEASURED_ITERATIONS
        val legacyPerCall = legacyTotalNs / totalCalls
        val cachedPerCall = cachedTotalNs / totalCalls
        val speedup = legacyTotalNs.toDouble() / cachedTotalNs.toDouble()

        log("")
        log("--- Overall ($totalCalls calls per impl) ---")
        log("Legacy : total ${legacyTotalNs / 1_000_000} ms | per-call $legacyPerCall ns")
        log("Cached : total ${cachedTotalNs / 1_000_000} ms | per-call $cachedPerCall ns")
        log("Speedup: ${"%.2f".format(speedup)}x")
    }

    private data class WorkloadResults(val exact: Long, val deep: Long, val miss: Long)

    private fun measureAllWorkloads(lookup: EntityLookup): WorkloadResults {
        val urls = buildUrlSample(domainEntityDao.getAll().map { it.domain })
        val exactNs = measureNs(lookup, urls.exact)
        val deepNs = measureNs(lookup, urls.deep)
        val missNs = measureNs(lookup, urls.miss)
        return WorkloadResults(exactNs, deepNs, missNs)
    }

    private fun measureNs(lookup: EntityLookup, hosts: List<String>): Long {
        val start = System.nanoTime()
        repeat(MEASURED_ITERATIONS) {
            hosts.forEach { lookup.entityForUrl(it) }
        }
        return System.nanoTime() - start
    }

    private fun runWorkload(lookup: EntityLookup, urls: UrlSample) {
        urls.exact.forEach { lookup.entityForUrl(it) }
        urls.deep.forEach { lookup.entityForUrl(it) }
        urls.miss.forEach { lookup.entityForUrl(it) }
    }

    private data class UrlSample(val exact: List<String>, val deep: List<String>, val miss: List<String>) {
        val total: Int get() = exact.size + deep.size + miss.size
    }

    /**
     * Build a representative URL sample across three workloads.
     *
     * Hosts are taken deterministically from the loaded data so the sample is reproducible
     * across runs. The DEEP workload prepends three labels to amplify the per-level cost
     * the cached impl optimises away.
     */
    private fun buildUrlSample(knownDomains: List<String>): UrlSample {
        val exactBase = knownDomains.take(SAMPLE_PER_WORKLOAD)
        val exact = exactBase.map { "http://$it/index" }
        val deep = exactBase.map { "http://a.b.c.$it/index" }
        val miss = (0 until SAMPLE_PER_WORKLOAD).map { i -> "http://unrelated$i.example-not-tracking.com/index" }
        return UrlSample(exact = exact, deep = deep, miss = miss)
    }

    private fun loadTdsJson(): TdsJson {
        val moshi = Moshi.Builder().add(ActionJsonAdapter()).build()
        val adapter = moshi.adapter(TdsJson::class.java)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.resources.openRawResource(R.raw.tds).source().buffer().use { adapter.fromJson(it) }
            ?: error("Failed to parse R.raw.tds")
    }

    private fun logWorkloadComparison(label: String, legacyNs: Long, cachedNs: Long, urlsInWorkload: Int) {
        val calls = urlsInWorkload.toLong() * MEASURED_ITERATIONS
        val legacyPerCall = legacyNs / calls
        val cachedPerCall = cachedNs / calls
        val speedup = legacyNs.toDouble() / cachedNs.toDouble()
        log(label)
        log("  Legacy : total ${legacyNs / 1_000_000} ms | per-call $legacyPerCall ns")
        log("  Cached : total ${cachedNs / 1_000_000} ms | per-call $cachedPerCall ns")
        log("  Speedup: ${"%.2f".format(speedup)}x")
    }

    /**
     * Prints proof-of-environment up front so anyone reading the output knows this is genuinely
     * running on the device's ART runtime and not on a JVM. Same shape as TdsClientPerfAndroidTest
     * so the output is grep-friendly across benchmarks.
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

    @SuppressLint("DenyListedApi")
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
        Log.i(LOG_TAG, message)
    }

    companion object {
        private const val LOG_TAG = "EntityLookupPerf"
        private const val WARMUP_ROUNDS = 5
        private const val MEASURED_ITERATIONS = 200
        private const val SAMPLE_PER_WORKLOAD = 100
    }
}
