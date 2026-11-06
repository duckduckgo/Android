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
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.duckduckgo.testseeder.api.TestSeederKey
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

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
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PageLoadBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val explicitPageUrl = InstrumentationRegistry.getArguments().getString("pageUrl")

    @Test
    fun noTrackers() = measurePageLoad("no-trackers", NoTrackersFixture)

    @Test
    fun manyTrackersBlocked() = measurePageLoad("many-trackers-blocked", ManyTrackersBlockedFixture)

    @Test
    fun firstPartyTrackers() = measurePageLoad("first-party-trackers", FirstPartyTrackersFixture)

    @Test
    fun cpm() = measurePageLoad("cpm", CpmFixture)

    @Test
    fun allScenariosProtectionsOn() = measurePageLoad("all-protections-on", AllScenariosFixture, protectionsEnabled = true)

    @Test
    fun allScenariosProtectionsOff() = measurePageLoad("all-protections-off", AllScenariosFixture, protectionsEnabled = false)

    private fun measurePageLoad(scenario: String, fixture: PageLoadFixture, protectionsEnabled: Boolean = true) {
        val pageSettleMs = if (protectionsEnabled) PAGE_SETTLE_MS_PROTECTED else PAGE_SETTLE_MS_UNPROTECTED
        // Started before seeding: the app proxies all WebView traffic to this port, so it must exist (and be known) first.
        val fixtureServer = if (explicitPageUrl == null) {
            PageLoadFixtureServer(fixture, scenario).also { it.start() }
        } else {
            null
        }
        try {
            val seedingPid = launchAndVerifyProtectionState(protectionsEnabled, fixtureServer?.port)
            val pageUrl = explicitPageUrl ?: fixtureServer!!.baseUrl
            benchmarkRule.measureRepeated(
                packageName = TARGET_PACKAGE,
                metrics = listOf(TraceSectionMetric("ddg.pageLoad", TraceSectionMetric.Mode.Sum)),
                iterations = 1,
                compilationMode = CompilationMode.Full(),
            ) {
                startMeasuredProcessAndAwaitMockEnvironment(seedingPid, fixtureServer)
                // The extra leading navigation is the warmup, discarded by position; ?i= forces a fresh main-frame load.
                repeat(NAV_COUNT + 1) { i ->
                    navigateTo("$pageUrl?i=$i", pageSettleMs)
                }
                // Closes the last measured navigation's slice; its own trailing slice is also discarded by position.
                navigateTo(fixtureServer?.traceSentinelUrl ?: "$pageUrl?i=${NAV_COUNT + 1}", pageSettleMs)
            }
        } finally {
            fixtureServer?.shutdown()
            clearMockEnvironment()
        }
    }

    /**
     * A plain launcher intent adds no page-load slice; the app must report ready before any navigation.
     */
    private fun startMeasuredProcessAndAwaitMockEnvironment(seedingPid: Int?, fixtureServer: PageLoadFixtureServer?) {
        device.executeShellCommand("logcat -c")
        instrumentation.context.startActivity(
            instrumentation.context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        if (seedingPid != null) awaitMockEnvironmentState("ready", excludePid = seedingPid)
        fixtureServer?.startCounting()
    }

    /**
     * The seeded mock environment persists in app storage and would proxy later local launches to a dead port.
     */
    private fun clearMockEnvironment() {
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        device.executeShellCommand("pm clear $TARGET_PACKAGE")
    }

    private fun navigateTo(url: String, pageSettleMs: Long) {
        val intent = Intent().apply {
            component = ComponentName(TARGET_PACKAGE, BROWSER_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(OPEN_IN_CURRENT_TAB_EXTRA, true)
        }
        instrumentation.context.startActivity(intent)
        // Spans the load so the next navigation starts after onPageFinished; a stuck load costs at most one sample.
        SystemClock.sleep(pageSettleMs)
    }

    /**
     * Returns the pid of the seeding process, or null when no mock environment was requested.
     */
    private fun launchAndVerifyProtectionState(protectionsEnabled: Boolean, fixturePort: Int?): Int? {
        val userAllowListValue = if (protectionsEnabled) "" else PageLoadFixtureServer.HOST
        // Seeding only runs from the intent that creates LaunchBridgeActivity, so a warm process
        // resumes the existing task and skips seeding; force-stop also makes the app's allow-list
        // repository re-read its cache-at-init.
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        device.executeShellCommand("pm clear $TARGET_PACKAGE")
        device.executeShellCommand("logcat -c")
        device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.POST_NOTIFICATIONS")
        val launch = instrumentation.context.packageManager
            .getLaunchIntentForPackage(TARGET_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.putExtra(TestSeederKey.IS_MACROBENCHMARK.key, "true")
            ?.apply { if (fixturePort != null) putExtra(TestSeederKey.MOCK_ENVIRONMENT.key, fixturePort.toString()) }
            ?.putExtra(TestSeederKey.USER_ALLOW_LIST.key, userAllowListValue)
        instrumentation.context.startActivity(launch)
        val skipButton = By.res(TARGET_PACKAGE, "skipOnboardingButton")
        // The skip button only appears once the live privacy config is downloaded, so this also gates on that download.
        check(device.wait(Until.hasObject(skipButton), SKIP_ONBOARDING_TIMEOUT_MS)) {
            "Onboarding skip button not shown within ${SKIP_ONBOARDING_TIMEOUT_MS}ms: the privacy config download was too slow or failed"
        }
        device.findObject(skipButton).click()
        device.waitForIdle()
        val seedingPid = if (fixturePort != null) awaitMockEnvironmentState("seeded", excludePid = null) else null
        verifyProtectionState(userAllowListValue)
        return seedingPid
    }

    /**
     * Polls slowly so shell spawns don't perturb a measured window. The app gives up after 45 s and logs why, so this
     * longer timeout sees the app's failure reason first; only a hung app reaches the timeout.
     */
    private fun awaitMockEnvironmentState(state: String, excludePid: Int?): Int {
        val deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS
        while (true) {
            val lines = device.executeShellCommand("logcat -d -s DdgTestSeeder").lineSequence()
                .mapNotNull { MOCK_ENVIRONMENT_LINE.find(it) }
                .map { Triple(it.groupValues[1], it.groupValues[2].trim(), it.groupValues[3].toInt()) }
                .filter { it.third != excludePid }
                .toList()
            lines.firstOrNull { it.first == "failed" }?.let { (_, detail, pid) ->
                error("Mock environment failed in process $pid: $detail")
            }
            lines.firstOrNull { it.first == state }?.let { return it.third }
            if (SystemClock.elapsedRealtime() >= deadline) break
            SystemClock.sleep(READY_POLL_MS)
        }
        error("The app never reported ready: no \"$state\" line within ${READY_TIMEOUT_MS}ms (excluding pid $excludePid)")
    }

    /**
     * Seeding awaits the allow-list Flow before onboarding is shown, so the DdgTestSeeder line is final past the skip button.
     */
    private fun verifyProtectionState(expectedUserAllowList: String) {
        val log = device.executeShellCommand("logcat -d -s DdgTestSeeder")
        val observed = log.lineSequence().lastOrNull { it.contains("userAllowList=") }
            ?.substringAfter("userAllowList=")?.trim()
        check(observed == expectedUserAllowList) {
            "userAllowList seeding did not settle on \"$expectedUserAllowList\"; observed: ${observed ?: "<none>"}"
        }
    }

    companion object {
        private const val NAV_COUNT = 10
        private const val READY_TIMEOUT_MS = 60_000L
        private const val READY_POLL_MS = 500L
        private const val SKIP_ONBOARDING_TIMEOUT_MS = 60_000L
        private val MOCK_ENVIRONMENT_LINE = Regex("mockEnvironment=(seeded|ready|failed) (.*) pid=(\\d+)\\s*$")
        private const val PAGE_SETTLE_MS_PROTECTED = 8_000L

        // Trackers only hit the real network when protections are off, so the ~25 third-party
        // subresources take longer than the protected-page settle time.
        private const val PAGE_SETTLE_MS_UNPROTECTED = 20_000L
        private const val BROWSER_ACTIVITY = "com.duckduckgo.app.browser.BrowserActivity"
        private const val OPEN_IN_CURRENT_TAB_EXTRA = "OPEN_IN_CURRENT_TAB_EXTRA"
    }
}
