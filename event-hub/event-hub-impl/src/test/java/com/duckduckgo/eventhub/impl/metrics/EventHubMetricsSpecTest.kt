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

package com.duckduckgo.eventhub.impl.metrics

import android.annotation.SuppressLint
import com.duckduckgo.app.trackerdetection.blocklist.BlockListExperiment
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.eventhub.impl.EventHubFeature
import com.duckduckgo.eventhub.impl.pixels.EventHubRepository
import com.duckduckgo.eventhub.impl.pixels.RealEventHubPixelManager
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Android's coverage of the cross-platform experiment metrics specification
 * (`docs/event-hub/tests/metrics.md` in `duckduckgo/ddg-workflow`). Each case ID from that document leads the test name
 * so coverage is greppable and a failure names the spec case. Events go through [RealEventHubPixelManager] and
 * [RealExperimentMetricsHandler], and each [MetricsPixel] handed to the experiments framework is flattened into the
 * spec's `(experiment, metric, conversionWindowDays, threshold)` requests.
 *
 * Only requests for enrolled experiments are asserted. Android hands over requests for every active experiment that
 * declares the metric and leaves enrolment to the framework, which the spec allows.
 *
 * M-SEL-10 is not applicable: Android has no native (tab-less) event stream.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventHubMetricsSpecTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val metricsExtension = FakeMetricsPixelExtension()
    private val eventHubFeature: EventHubFeature = FakeFeatureToggleFactory.create(EventHubFeature::class.java)
    private val css = FeatureToggles.Builder().store(FakeToggleStore()).featureName("contentScopeExperiments").build()
        .create(CssExperiments::class.java)
    private val blockList = FeatureToggles.Builder().store(FakeToggleStore()).featureName("blockList").build()
        .create(BlockList::class.java)
    private val contentScopeExperiments = object : ContentScopeExperiments {
        override suspend fun getActiveExperiments(): List<Toggle> = listOf(
            css.contentScopeExperiment1(),
            css.contentScopeExperiment3(),
            css.contentScopeExperiment10(),
            css.contentScopeExperiment19(),
            css.contentScopeExperiment21(),
        ).filter { it.isEnabled() }
    }
    private val blockListExperiment = object : BlockListExperiment {
        override suspend fun activeExperimentCohort(): String? = null
        override suspend fun metric(metric: BlockListExperiment.Metric): MetricsPixel? = null
        override suspend fun activeExperiment(): Toggle? = blockList.tdsNextExperiment007().takeIf { it.isEnabled() }
    }

    private lateinit var manager: RealEventHubPixelManager
    private var page = 0

    @Before
    fun setup() {
        metricsExtension.register()
        setEventHub(enabled = true)
        configure(css.contentScopeExperiment1(), EXPERIMENT_1)
        configure(css.contentScopeExperiment3(), EXPERIMENT_3)
        configure(css.contentScopeExperiment10(), EXPERIMENT_10)
        configure(css.contentScopeExperiment19(), EXPERIMENT_19)
        configure(css.contentScopeExperiment21(), EXPERIMENT_21)
        configure(blockList.tdsNextExperiment007(), TDS_EXPERIMENT_007)

        manager = RealEventHubPixelManager(
            repository = mock<EventHubRepository>(),
            pixel = mock(),
            timeProvider = FakeCurrentTimeProvider(),
            appCoroutineScope = coroutineTestRule.testScope,
            pixelDispatcher = UnconfinedTestDispatcher(coroutineTestRule.testScope.testScheduler),
            eventHubFeature = eventHubFeature,
            metricsHandler = RealExperimentMetricsHandler(contentScopeExperiments, blockListExperiment),
        )
    }

    @Test
    fun `M-SEL-1 each experiment declaring a metric requests it`() {
        enroll(css.contentScopeExperiment1(), css.contentScopeExperiment10())

        val requests = deliver("captchaDetected")

        assertRequests(
            listOf(
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 1),
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 3),
                Request("contentScopeExperiment10", "captchaSeen", "0-7", 1),
            ),
            requests,
        )
    }

    @Test
    fun `M-SEL-2 only the declaring experiment requests`() {
        enroll(css.contentScopeExperiment1(), css.contentScopeExperiment3())

        val requests = deliver("reloadLoopDetected")

        assertRequests(
            listOf(
                Request("contentScopeExperiment3", "reloadLoop", "0", 1),
                Request("contentScopeExperiment3", "reloadLoop", "0-7", 1),
            ),
            requests,
        )
    }

    @Test
    fun `M-SEL-3 a shared metric name bound to different events requests per experiment`() {
        enroll(css.contentScopeExperiment1(), css.contentScopeExperiment3(), css.contentScopeExperiment21())

        val requests = deliver("reloadLoopDetected")

        assertRequests(
            listOf(
                Request("contentScopeExperiment21", "captchaSeen", "0-7", 1),
                Request("contentScopeExperiment3", "reloadLoop", "0", 1),
                Request("contentScopeExperiment3", "reloadLoop", "0-7", 1),
            ),
            requests,
        )
    }

    @Test
    fun `M-SEL-4 the event named by another experiment's identically-named metric requests nothing here`() {
        enroll(css.contentScopeExperiment1(), css.contentScopeExperiment21())

        val requests = deliver("captchaDetected")

        assertRequests(
            listOf(
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 1),
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 3),
            ),
            requests,
        )
    }

    @Test
    fun `M-SEL-5 an enrolled experiment declaring no metrics requests nothing`() {
        enroll(css.contentScopeExperiment19())

        val requests = deliver("reloadLoopDetected") + deliver("captchaDetected")

        assertRequests(emptyList(), requests)
    }

    @Test
    fun `M-SEL-6 a declared metric whose experiment is not enrolled requests nothing`() {
        enroll(css.contentScopeExperiment1())

        val requests = deliver("tdsDownloadFailed")

        assertRequests(emptyList(), requests)
    }

    @Test
    fun `M-SEL-7 a user enrolled in no experiments requests nothing`() {
        val requests = listOf("captchaDetected", "reloadLoopDetected", "searchPerformed", "pageLoaded", "tdsDownloadFailed")
            .flatMap { deliver(it) }

        assertRequests(emptyList(), requests)
    }

    @Test
    fun `M-SEL-8 metrics attach under both experiment parent features`() {
        enroll(css.contentScopeExperiment1(), blockList.tdsNextExperiment007())

        val requests = deliver("pageLoaded")

        assertRequests(
            listOf(
                Request("contentScopeExperiment1", "pageLoad", "0-5", 1),
                Request("tdsNextExperiment007", "pageLoad", "0-5", 1),
            ),
            requests,
        )
    }

    @Test
    fun `M-SEL-9 an event matching no declared metric is ignored`() {
        enroll(css.contentScopeExperiment1())

        val requests = deliver("unrelatedEvent")

        assertRequests(emptyList(), requests)
    }

    @Test
    fun `M-FAN-1 windows and thresholds multiply out within a group and groups do not combine`() {
        enroll(css.contentScopeExperiment1())

        val requests = deliver("searchPerformed")

        assertRequests(
            listOf(
                Request("contentScopeExperiment1", "searchLike", "0", 1),
                Request("contentScopeExperiment1", "searchLike", "1", 1),
                Request("contentScopeExperiment1", "searchLike", "0-7", 2),
                Request("contentScopeExperiment1", "searchLike", "0-7", 3),
            ),
            requests,
        )
    }

    @Test
    fun `M-DED-1 repeated occurrences on one page request once`() {
        enroll(css.contentScopeExperiment1())
        manager.onNavigationStarted(TAB, "https://example.com/")

        val requests = listOf(1, 2, 3).flatMap { listening { manager.handleWebEvent(event("captchaDetected"), TAB) } }

        assertRequests(
            listOf(
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 1),
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 3),
            ),
            requests,
        )
    }

    @Test
    fun `M-LIF-1 a metric and its experiment enabled in one config change both take effect`() {
        configure(css.contentScopeExperiment3(), settings = null, enabled = false)
        manager.onConfigChanged()
        val phase1 = deliver("reloadLoopDetected")

        configure(css.contentScopeExperiment3(), EXPERIMENT_3)
        enroll(css.contentScopeExperiment3())
        manager.onConfigChanged()
        val phase2 = deliver("reloadLoopDetected")

        assertRequests(emptyList(), phase1)
        assertRequests(
            listOf(
                Request("contentScopeExperiment3", "reloadLoop", "0", 1),
                Request("contentScopeExperiment3", "reloadLoop", "0-7", 1),
            ),
            phase2,
        )
    }

    @Test
    fun `M-LIF-2 a removed metric stops requesting when the new config applies`() {
        enroll(css.contentScopeExperiment1())
        val phase1 = deliver("captchaDetected")

        configure(css.contentScopeExperiment1(), EXPERIMENT_1_WITHOUT_CAPTCHA_SEEN, cohortFrom = css.contentScopeExperiment1())
        manager.onConfigChanged()
        val phase2 = deliver("captchaDetected")

        assertRequests(
            listOf(
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 1),
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 3),
            ),
            phase1,
        )
        assertRequests(emptyList(), phase2)
    }

    @Test
    fun `M-LIF-3 a metric declared before its experiment enrolls is inert, then requests on enrollment`() {
        val phase1 = deliver("captchaDetected")

        enroll(css.contentScopeExperiment1())
        val phase2 = deliver("captchaDetected")

        assertRequests(emptyList(), phase1)
        assertRequests(
            listOf(
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 1),
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 3),
            ),
            phase2,
        )
    }

    @Test
    fun `M-LIF-4 removing a shared-name metric from one experiment stops that experiment only`() {
        enroll(css.contentScopeExperiment1(), css.contentScopeExperiment10())
        val phase1 = deliver("captchaDetected")

        configure(css.contentScopeExperiment10(), NO_METRICS, cohortFrom = css.contentScopeExperiment10())
        manager.onConfigChanged()
        val phase2 = deliver("captchaDetected")

        val experiment1Requests = listOf(
            Request("contentScopeExperiment1", "captchaSeen", "0-7", 1),
            Request("contentScopeExperiment1", "captchaSeen", "0-7", 3),
        )
        assertRequests(experiment1Requests + Request("contentScopeExperiment10", "captchaSeen", "0-7", 1), phase1)
        assertRequests(experiment1Requests, phase2)
    }

    @Test
    fun `M-LIF-5 a disabled experiment stops requesting when the new config applies`() {
        enroll(css.contentScopeExperiment1())
        val phase1 = deliver("captchaDetected")

        configure(css.contentScopeExperiment1(), EXPERIMENT_1, enabled = false, cohortFrom = css.contentScopeExperiment1())
        manager.onConfigChanged()
        val phase2 = deliver("captchaDetected")

        assertRequests(
            listOf(
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 1),
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 3),
            ),
            phase1,
        )
        assertRequests(emptyList(), phase2)
    }

    @Test
    fun `M-LIF-6 disabling the eventHub feature stops events reaching metrics`() {
        enroll(css.contentScopeExperiment1())
        val phase1 = deliver("captchaDetected")

        setEventHub(enabled = false)
        manager.onConfigChanged()
        val phase2 = deliver("captchaDetected")

        assertRequests(
            listOf(
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 1),
                Request("contentScopeExperiment1", "captchaSeen", "0-7", 3),
            ),
            phase1,
        )
        assertRequests(emptyList(), phase2)
    }

    private data class Request(val experiment: String, val metric: String, val window: String, val threshold: Int)

    /** Delivers one event on a fresh page, so de-duplication never collapses events across phases. */
    private fun deliver(type: String): List<Request> {
        page++
        manager.onNavigationStarted(TAB, "https://example.com/page$page")
        return listening { manager.handleWebEvent(event(type), TAB) }
    }

    /** The requests [block] hands over for experiments the framework would act on: enrolled ones. */
    private fun listening(block: () -> Unit): List<Request> {
        val before = metricsExtension.sentMetrics.size
        block()
        return metricsExtension.sentMetrics.drop(before)
            .filter { it.toggle.getRawStoredState()?.assignedCohort != null }
            .flatMap { it.toRequests() }
    }

    // Same N / low-high window format as the framework's conversionWindowDays parameter
    private fun MetricsPixel.toRequests(): List<Request> = conversionWindow.map { window ->
        val days = if (window.lowerWindow == window.upperWindow) "${window.lowerWindow}" else "${window.lowerWindow}-${window.upperWindow}"
        Request(toggle.featureName().name, metric, days, value.toInt())
    }

    private fun assertRequests(expected: List<Request>, actual: List<Request>) {
        val order = compareBy<Request>({ it.experiment }, { it.metric }, { it.window }, { it.threshold })
        assertEquals(expected.sortedWith(order), actual.sortedWith(order))
    }

    private fun event(type: String) = JSONObject().put("type", type).put("data", JSONObject())

    @SuppressLint("DenyListedApi")
    private fun setEventHub(enabled: Boolean) {
        eventHubFeature.self().setRawStoredState(Toggle.State(remoteEnableState = enabled, enable = enabled, settings = "{}"))
    }

    @SuppressLint("DenyListedApi")
    private fun configure(experiment: Toggle, settings: String?, enabled: Boolean = true, cohortFrom: Toggle? = null) {
        experiment.setRawStoredState(
            Toggle.State(
                remoteEnableState = enabled,
                enable = enabled,
                settings = settings,
                cohorts = listOf(COHORT),
                assignedCohort = cohortFrom?.getRawStoredState()?.assignedCohort,
            ),
        )
    }

    @SuppressLint("DenyListedApi")
    private fun enroll(vararg experiments: Toggle) {
        val enrollmentDate = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        experiments.forEach { experiment ->
            val state = experiment.getRawStoredState()!!
            experiment.setRawStoredState(state.copy(assignedCohort = COHORT.copy(enrollmentDateET = enrollmentDate)))
        }
    }

    private class FakeCurrentTimeProvider : CurrentTimeProvider {
        override fun currentTimeMillis(): Long = 0L
        override fun elapsedRealtime(): Long = 0L
        override fun localDateTimeNow(): java.time.LocalDateTime = java.time.LocalDateTime.now()
    }

    interface CssExperiments {
        @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
        fun self(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun contentScopeExperiment1(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun contentScopeExperiment3(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun contentScopeExperiment10(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun contentScopeExperiment19(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun contentScopeExperiment21(): Toggle
    }

    interface BlockList {
        @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
        fun self(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun tdsNextExperiment007(): Toggle
    }

    private companion object {
        const val TAB = "tab1"
        val COHORT = Toggle.State.Cohort(name = "treatment", weight = 1)

        const val NO_METRICS = """{ "metrics": {} }"""

        val EXPERIMENT_1 = """
            {
                "metrics": {
                    "captchaSeen": { "event": "captchaDetected", "conversions": [ { "windows": [[0, 7]], "thresholds": [1, 3] } ] },
                    "searchLike": {
                        "event": "searchPerformed",
                        "conversions": [
                            { "windows": [[0, 0], [1, 1]], "thresholds": [1] },
                            { "windows": [[0, 7]], "thresholds": [2, 3] }
                        ]
                    },
                    "pageLoad": { "event": "pageLoaded", "conversions": [ { "windows": [[0, 5]], "thresholds": [1] } ] },
                    "appLaunched": { "event": "appLaunch", "conversions": [ { "windows": [[0, 7]], "thresholds": [1, 2] } ] }
                }
            }
        """.trimIndent()

        val EXPERIMENT_1_WITHOUT_CAPTCHA_SEEN = """
            {
                "metrics": {
                    "searchLike": {
                        "event": "searchPerformed",
                        "conversions": [
                            { "windows": [[0, 0], [1, 1]], "thresholds": [1] },
                            { "windows": [[0, 7]], "thresholds": [2, 3] }
                        ]
                    },
                    "pageLoad": { "event": "pageLoaded", "conversions": [ { "windows": [[0, 5]], "thresholds": [1] } ] },
                    "appLaunched": { "event": "appLaunch", "conversions": [ { "windows": [[0, 7]], "thresholds": [1, 2] } ] }
                }
            }
        """.trimIndent()

        val EXPERIMENT_3 = """
            { "metrics": { "reloadLoop": { "event": "reloadLoopDetected", "conversions": [ { "windows": [[0, 0], [0, 7]], "thresholds": [1] } ] } } }
        """.trimIndent()

        val EXPERIMENT_10 = """
            { "metrics": { "captchaSeen": { "event": "captchaDetected", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] } } }
        """.trimIndent()

        const val EXPERIMENT_19 = """{ }"""

        val EXPERIMENT_21 = """
            { "metrics": { "captchaSeen": { "event": "reloadLoopDetected", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] } } }
        """.trimIndent()

        val TDS_EXPERIMENT_007 = """
            {
                "metrics": {
                    "blocklistFailure": { "event": "tdsDownloadFailed", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] },
                    "pageLoad": { "event": "pageLoaded", "conversions": [ { "windows": [[0, 5]], "thresholds": [1] } ] }
                }
            }
        """.trimIndent()
    }
}
