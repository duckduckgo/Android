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
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealExperimentMetricsHandlerTest {

    private val metricsExtension = FakeMetricsPixelExtension()
    private val cssExperiments = FeatureToggles.Builder()
        .store(FakeToggleStore())
        .featureName("contentScopeExperiments")
        .build()
        .create(TestContentScopeExperiments::class.java)
    private val blockList = FeatureToggles.Builder()
        .store(FakeToggleStore())
        .featureName("blockList")
        .build()
        .create(TestBlockList::class.java)

    private val contentScopeExperiments = FakeContentScopeExperiments()
    private val blockListExperiment = FakeBlockListExperiment()
    private val handler = RealExperimentMetricsHandler(contentScopeExperiments, blockListExperiment)

    @Before
    fun setup() {
        metricsExtension.register()
    }

    @Test
    fun `one metrics pixel is sent per threshold with that threshold's windows`() = runTest {
        activate(
            cssExperiments.contentScopeExperiment1(),
            metrics(
                """
                "searchLike": {
                    "event": "searchPerformed",
                    "conversions": [
                        { "windows": [[0, 0], [1, 1]], "thresholds": [1] },
                        { "windows": [[0, 7]], "thresholds": [2, 3] }
                    ]
                }
                """,
            ),
        )

        handler.onEvent("searchPerformed")

        val sent = metricsExtension.sentMetrics
        assertEquals(3, sent.size)
        sent.forEach {
            assertEquals("searchLike", it.metric)
            assertEquals(MetricType.COUNT_WHEN_IN_WINDOW, it.type)
            assertEquals(cssExperiments.contentScopeExperiment1().featureName(), it.toggle.featureName())
        }
        assertEquals(
            mapOf(
                "1" to listOf(ConversionWindow(0, 0), ConversionWindow(1, 1)),
                "2" to listOf(ConversionWindow(0, 7)),
                "3" to listOf(ConversionWindow(0, 7)),
            ),
            sent.associate { it.value to it.conversionWindow },
        )
    }

    @Test
    fun `an event no metric listens for sends nothing`() = runTest {
        activate(cssExperiments.contentScopeExperiment1(), captchaMetric())

        handler.onEvent("somethingElse")

        assertTrue(metricsExtension.sentMetrics.isEmpty())
    }

    @Test
    fun `every active experiment declaring a metric on the event is sent`() = runTest {
        activate(cssExperiments.contentScopeExperiment1(), captchaMetric())
        activate(cssExperiments.contentScopeExperiment2(), captchaMetric())
        blockListExperiment.active = blockList.tdsNextExperiment001().withSettings(captchaMetric())

        handler.onEvent("captchaDetected")

        assertEquals(
            setOf("contentScopeExperiment1", "contentScopeExperiment2", "tdsNextExperiment001"),
            metricsExtension.sentMetrics.map { it.toggle.featureName().name }.toSet(),
        )
    }

    @Test
    fun `an experiment its feature doesn't report as active sends nothing`() = runTest {
        cssExperiments.contentScopeExperiment1().withSettings(captchaMetric())
        blockList.tdsNextExperiment001().withSettings(captchaMetric())
        blockListExperiment.active = blockList.tdsNextExperiment002().withSettings(captchaMetric())

        handler.onEvent("captchaDetected")

        assertEquals(listOf("tdsNextExperiment002"), metricsExtension.sentMetrics.map { it.toggle.featureName().name })
    }

    @Test
    fun `a change of active experiment applies to the next event`() = runTest {
        blockListExperiment.active = blockList.tdsNextExperiment001().withSettings(captchaMetric())
        handler.onEvent("captchaDetected")

        blockListExperiment.active = blockList.tdsNextExperiment002().withSettings(captchaMetric())
        handler.onEvent("captchaDetected")

        assertEquals(
            listOf("tdsNextExperiment001", "tdsNextExperiment002"),
            metricsExtension.sentMetrics.map { it.toggle.featureName().name },
        )
    }

    @Test
    fun `changed metric definitions apply to the next event`() = runTest {
        activate(cssExperiments.contentScopeExperiment1(), captchaMetric())
        handler.onEvent("captchaDetected")

        cssExperiments.contentScopeExperiment1().withSettings(
            metrics(""""reload": { "event": "reloadLoop", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }"""),
        )
        handler.onEvent("captchaDetected")
        handler.onEvent("reloadLoop")

        assertEquals(listOf("captchaSeen", "reload"), metricsExtension.sentMetrics.map { it.metric })
    }

    @Test
    fun `a second event waits for the first instead of handling it concurrently`() = runTest {
        activate(cssExperiments.contentScopeExperiment1(), captchaMetric())
        contentScopeExperiments.gate = CompletableDeferred()

        val firstEvent = launch { handler.onEvent("captchaDetected") }
        val secondEvent = launch { handler.onEvent("captchaDetected") }
        testScheduler.runCurrent()

        assertEquals(1, contentScopeExperiments.calls)
        assertTrue(metricsExtension.sentMetrics.isEmpty())

        contentScopeExperiments.gate!!.complete(Unit)
        firstEvent.join()
        secondEvent.join()

        assertEquals(2, contentScopeExperiments.calls)
        assertEquals(2, metricsExtension.sentMetrics.size)
    }

    @Test
    fun `a failing send doesn't escape or stop the other sends`() = runTest {
        activate(
            cssExperiments.contentScopeExperiment1(),
            metrics(
                """
                "failing": { "event": "captchaDetected", "conversions": [ { "windows": [[0, 7]], "thresholds": [1, 2] } ] },
                "working": { "event": "captchaDetected", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }
                """,
            ),
        )
        metricsExtension.failingMetrics += "failing"

        handler.onEvent("captchaDetected")

        assertEquals(listOf("working"), metricsExtension.sentMetrics.map { it.metric })
    }

    @Test
    fun `a failure reading the active experiments sends nothing and is retried on the next event`() = runTest {
        activate(cssExperiments.contentScopeExperiment1(), captchaMetric())
        contentScopeExperiments.failure = IllegalStateException("experiments unavailable")

        handler.onEvent("captchaDetected")
        assertTrue(metricsExtension.sentMetrics.isEmpty())

        contentScopeExperiments.failure = null
        handler.onEvent("captchaDetected")

        assertEquals(listOf("captchaSeen"), metricsExtension.sentMetrics.map { it.metric })
    }

    @Test
    fun `an experiment without settings sends nothing`() = runTest {
        activate(cssExperiments.contentScopeExperiment1(), settings = null)

        handler.onEvent("captchaDetected")

        assertTrue(metricsExtension.sentMetrics.isEmpty())
    }

    private fun activate(experiment: Toggle, settings: String?) {
        contentScopeExperiments.active += experiment.withSettings(settings)
    }

    private fun captchaMetric() =
        metrics(""""captchaSeen": { "event": "captchaDetected", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }""")

    private fun metrics(entries: String) = """{ "metrics": { ${entries.trimIndent()} } }"""

    @SuppressLint("DenyListedApi")
    private fun Toggle.withSettings(settings: String?): Toggle = apply {
        setRawStoredState(Toggle.State(remoteEnableState = true, enable = true, settings = settings))
    }

    private class FakeContentScopeExperiments : ContentScopeExperiments {
        val active = mutableListOf<Toggle>()
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null

        override suspend fun getActiveExperiments(): List<Toggle> {
            calls++
            gate?.await()
            failure?.let { throw it }
            return active.toList()
        }
    }

    private class FakeBlockListExperiment : BlockListExperiment {
        var active: Toggle? = null

        override suspend fun activeExperimentCohort(): String? = null
        override suspend fun metric(metric: BlockListExperiment.Metric): MetricsPixel? = null
        override suspend fun activeExperiment(): Toggle? = active
    }

    interface TestContentScopeExperiments {
        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun self(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun contentScopeExperiment1(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun contentScopeExperiment2(): Toggle
    }

    interface TestBlockList {
        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun self(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun tdsNextExperiment001(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun tdsNextExperiment002(): Toggle
    }
}
