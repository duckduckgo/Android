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

package com.duckduckgo.privacy.config.impl.features.trackerallowlist

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import com.duckduckgo.privacy.config.store.features.trackerallowlist.CompiledRule
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.buildRulesByDomain
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * On-device microbenchmark for [RealTrackerAllowlist].
 *
 * Compares the legacy read path (precompileRegexAndCacheDomains=false: linear filter over all
 * entities + compile-on-every-call regex) against the optimized read path
 * (precompileRegexAndCacheDomains=true: hash-keyed lookup over the repository's pre-built
 * `rulesByDomain` view) on a real Android device or emulator.
 *
 * ## Data source
 * Loads the bundled privacy-config fallback resource (`R.raw.privacy_config`).
 *
 * ## Running it
 * NOTE: this class is annotated @Ignore so CI does not run it. To execute manually:
 *   1. From Android Studio: right-click the test method or class and choose "Run". The IDE
 *      bypasses @Ignore for explicit invocations.
 *   2. From the CLI: temporarily comment out the @Ignore annotation, run the gradle command
 *      below, then revert. JUnit honors @Ignore even when --tests targets the class directly.
 *     ./gradlew :app:connectedPlayDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.privacy.config.impl.features.trackerallowlist.RealTrackerAllowlistPerfAndroidTest
 *
 * Output is emitted to logcat under tag "TrackerAllowlistPerf"; capture with:
 *     adb logcat -s TrackerAllowlistPerf:I *:S
 *
 * Numbers are observation-only — no pass/fail assertion.
 */
@Ignore("Performance benchmark — run manually, not in CI. See class kdoc for instructions.")
@RunWith(AndroidJUnit4::class)
class RealTrackerAllowlistPerfAndroidTest {

    private val featureToggle: FeatureToggle = mock<FeatureToggle>().also {
        whenever(it.isFeatureEnabled(anyString(), anyBoolean())).thenReturn(true)
    }

    @Test
    fun benchmarkOptimizedOffVsOnUsingRealAllowlist() {
        logRuntimeContext()
        val source = loadAllowlistEntities()
        val entities = source.entities
        val totalRules = entities.sumOf { it.rules.size }
        val urls = buildWorstCaseUrls(entities, sampleSize = URL_SAMPLE_SIZE)
        val documentUrl = "http://example.com/index.htm"

        val repository = fakeRepository(entities)
        val testeeOff = RealTrackerAllowlist(repository, featureToggle, ToggleStub(false))
        val testeeOn = RealTrackerAllowlist(repository, featureToggle, ToggleStub(true))

        // JIT/ART warmup — both paths.
        repeat(WARMUP_ROUNDS) {
            urls.forEach {
                testeeOff.isAnException(documentUrl, it)
                testeeOn.isAnException(documentUrl, it)
            }
        }

        // Build cost — paid once per privacy-config refresh on the repository side, regardless of
        // toggle state. Reported here so the trade-off is visible.
        val tStartBuild = System.nanoTime()
        val rulesByDomain = buildRulesByDomain(entities)
        val buildNs = System.nanoTime() - tStartBuild

        // Match throughput.
        val matchOffNs = measureMatchNs(testeeOff, urls, documentUrl)
        val matchOnNs = measureMatchNs(testeeOn, urls, documentUrl)

        val totalCalls = MEASURED_ITERATIONS.toLong() * urls.size
        val perCallOffNs = matchOffNs / totalCalls
        val perCallOnNs = matchOnNs / totalCalls
        val speedup = matchOffNs.toDouble() / matchOnNs.toDouble()

        log("=== RealTrackerAllowlist optimized-path benchmark (on-device) ===")
        log(
            "Device: ${Build.MANUFACTURER} ${Build.MODEL} | " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | " +
                "ABI ${Build.SUPPORTED_ABIS.firstOrNull()}",
        )
        log("Data source: ${source.label}")
        log("Allowlist entries: ${entities.size} (total rules across all entries: $totalRules)")
        log("Domains in index: ${rulesByDomain.size} (entries with the same normalized key are merged)")
        log("URL sample: ${urls.size} (each hits an allowlisted domain but no rule matches → full rule scan)")
        log("Iterations: $MEASURED_ITERATIONS × ${urls.size} URLs = $totalCalls match calls per toggle state")
        log("")
        log("Repository build cost (buildRulesByDomain): ${buildNs / 1_000_000} ms — paid once per config refresh")
        log("")
        log("[optimized=false] total match: ${matchOffNs / 1_000_000} ms | per-call: $perCallOffNs ns")
        log("[optimized=true ] total match: ${matchOnNs / 1_000_000} ms | per-call: $perCallOnNs ns")
        log("Match speedup: ${"%.2f".format(speedup)}x")
    }

    private fun measureMatchNs(
        testee: RealTrackerAllowlist,
        urls: List<String>,
        documentUrl: String,
    ): Long {
        val start = System.nanoTime()
        repeat(MEASURED_ITERATIONS) {
            urls.forEach { testee.isAnException(documentUrl, it) }
        }
        return System.nanoTime() - start
    }

    /**
     * Build URLs that hit an allowlisted domain (so we enter the rules loop) but with paths
     * crafted to NOT match any rule. Forces every rule's regex to be evaluated — the worst case
     * the optimization targets. Biased toward entries with many rules to amplify the signal.
     */
    private fun buildWorstCaseUrls(entities: List<TrackerAllowlistEntity>, sampleSize: Int): List<String> {
        return entities.asSequence()
            .filter { it.rules.isNotEmpty() }
            .sortedByDescending { it.rules.size }
            .take(sampleSize)
            .map { entity ->
                val nonce = "z9q8w7e6r5t4y3u2-${entity.domain.hashCode()}"
                "http://${entity.domain}/no-rule-match-path/$nonce/end"
            }
            .toList()
    }

    private fun fakeRepository(entities: List<TrackerAllowlistEntity>): TrackerAllowlistRepository {
        val index = buildRulesByDomain(entities)
        return object : TrackerAllowlistRepository {
            override fun updateAll(exceptions: List<TrackerAllowlistEntity>) = Unit
            override val exceptions: List<TrackerAllowlistEntity> = entities
            override val rulesByDomain: Map<String, List<CompiledRule>> = index
        }
    }

    private data class LoadedAllowlist(val entities: List<TrackerAllowlistEntity>, val label: String)

    private fun loadAllowlistEntities(): LoadedAllowlist {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val jsonText = targetContext.resources
            .openRawResource(com.duckduckgo.privacy.config.impl.R.raw.privacy_config)
            .bufferedReader()
            .use { it.readText() }

        val trackerAllowlistJson = JSONObject(jsonText)
            .getJSONObject("features")
            .getJSONObject("trackerAllowlist")
            .toString()
        val adapter: JsonAdapter<TrackerAllowlistFeature> =
            Moshi.Builder().build().adapter(TrackerAllowlistFeature::class.java)
        val feature = adapter.fromJson(trackerAllowlistJson)
            ?: error("Failed to parse trackerAllowlist feature from R.raw.privacy_config")

        val entities = feature.settings.allowlistedTrackers.entries.map { (domain, value) ->
            TrackerAllowlistEntity(domain = domain, rules = value.rules)
        }
        return LoadedAllowlist(entities, "R.raw.privacy_config")
    }

    /**
     * Prints proof-of-environment up front so anyone reading the output knows this is genuinely
     * running on the device's ART runtime and not on a JVM.
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

    private class ToggleStub(override val enabled: Boolean) : OptimizeTrackerAllowListRCWrapper

    companion object {
        private const val LOG_TAG = "TrackerAllowlistPerf"
        private const val WARMUP_ROUNDS = 5
        private const val MEASURED_ITERATIONS = 500
        private const val URL_SAMPLE_SIZE = 200
    }
}
