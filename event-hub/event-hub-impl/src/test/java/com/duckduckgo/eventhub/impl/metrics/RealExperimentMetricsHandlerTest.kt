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
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricType
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
    private val otherFeature = FeatureToggles.Builder()
        .store(FakeToggleStore())
        .featureName("otherFeature")
        .build()
        .create(TestOtherFeature::class.java)

    private val inventory = FakeInventory(
        listOf(
            cssExperiments.self(),
            cssExperiments.contentScopeExperiment1(),
            cssExperiments.contentScopeExperiment2(),
            blockList.self(),
            blockList.notAnExperiment(),
            blockList.tdsNextExperiment001(),
            blockList.tdsNextExperiment002(),
            otherFeature.self(),
            otherFeature.experimentLike(),
        ),
    )
    private val handler = RealExperimentMetricsHandler(inventory)

    @Before
    fun setup() {
        metricsExtension.register()
    }

    @Test
    fun `one metrics pixel is sent per threshold with that threshold's windows`() = runTest {
        cssExperiments.contentScopeExperiment1().enable(
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
        cssExperiments.contentScopeExperiment1().enable(captchaMetric())

        handler.onEvent("somethingElse")

        assertTrue(metricsExtension.sentMetrics.isEmpty())
    }

    @Test
    fun `every experiment declaring a metric on the event is sent`() = runTest {
        cssExperiments.contentScopeExperiment1().enable(captchaMetric())
        cssExperiments.contentScopeExperiment2().enable(captchaMetric())

        handler.onEvent("captchaDetected")

        assertEquals(
            setOf("contentScopeExperiment1", "contentScopeExperiment2"),
            metricsExtension.sentMetrics.map { it.toggle.featureName().name }.toSet(),
        )
    }

    @Test
    fun `experiments under other parents are ignored`() = runTest {
        otherFeature.experimentLike().enable(captchaMetric())

        handler.onEvent("captchaDetected")

        assertTrue(metricsExtension.sentMetrics.isEmpty())
    }

    @Test
    fun `every enabled blockList subfeature declaring the metric is sent`() = runTest {
        blockList.notAnExperiment().enable(captchaMetric())
        blockList.tdsNextExperiment001().enable(captchaMetric())
        blockList.tdsNextExperiment002().disable(captchaMetric())

        handler.onEvent("captchaDetected")

        assertEquals(
            setOf("notAnExperiment", "tdsNextExperiment001"),
            metricsExtension.sentMetrics.map { it.toggle.featureName().name }.toSet(),
        )
    }

    @Test
    fun `a disabled experiment sends nothing`() = runTest {
        cssExperiments.contentScopeExperiment1().disable(captchaMetric())

        handler.onEvent("captchaDetected")

        assertTrue(metricsExtension.sentMetrics.isEmpty())
    }

    @Test
    fun `an experiment enabled after the definitions are cached is sent without a config change`() = runTest {
        cssExperiments.contentScopeExperiment1().disable(captchaMetric())
        handler.onEvent("captchaDetected")

        cssExperiments.contentScopeExperiment1().enable(captchaMetric())
        handler.onEvent("captchaDetected")

        assertEquals(1, inventory.getAllCalls)
        assertEquals(listOf("captchaSeen"), metricsExtension.sentMetrics.map { it.metric })
    }

    @Test
    fun `an older tds experiment that is still enabled keeps sending alongside the newer one`() = runTest {
        blockList.tdsNextExperiment001().enable(captchaMetric())
        blockList.tdsNextExperiment002().enable(captchaMetric())

        handler.onEvent("captchaDetected")

        assertEquals(
            listOf("tdsNextExperiment001", "tdsNextExperiment002"),
            metricsExtension.sentMetrics.map { it.toggle.featureName().name }.sorted(),
        )
    }

    @Test
    fun `definitions are cached across events`() = runTest {
        cssExperiments.contentScopeExperiment1().enable(captchaMetric())

        handler.onEvent("captchaDetected")
        handler.onEvent("captchaDetected")
        handler.onEvent("otherEvent")

        assertEquals(1, inventory.getAllCalls)
        assertEquals(2, metricsExtension.sentMetrics.size)
    }

    @Test
    fun `config change rebuilds definitions from the current settings`() = runTest {
        cssExperiments.contentScopeExperiment1().enable(captchaMetric())
        handler.onEvent("captchaDetected")

        cssExperiments.contentScopeExperiment1().enable(
            metrics(""""reload": { "event": "reloadLoop", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }"""),
        )
        handler.onConfigChanged()
        handler.onEvent("captchaDetected")
        handler.onEvent("reloadLoop")

        assertEquals(2, inventory.getAllCalls)
        assertEquals(listOf("captchaSeen", "reload"), metricsExtension.sentMetrics.map { it.metric })
    }

    @Test
    fun `definitions built before a config change are not reused after it`() = runTest {
        cssExperiments.contentScopeExperiment1().enable(captchaMetric())
        inventory.gate = CompletableDeferred()

        val firstEvent = launch { handler.onEvent("captchaDetected") }
        testScheduler.runCurrent()
        handler.onConfigChanged()
        inventory.gate!!.complete(Unit)
        firstEvent.join()

        inventory.gate = null
        handler.onEvent("captchaDetected")

        assertEquals(2, inventory.getAllCalls)
    }

    @Test
    fun `a second event waits for the first instead of handling it concurrently`() = runTest {
        cssExperiments.contentScopeExperiment1().enable(captchaMetric())
        inventory.gate = CompletableDeferred()

        val firstEvent = launch { handler.onEvent("captchaDetected") }
        val secondEvent = launch { handler.onEvent("captchaDetected") }
        testScheduler.runCurrent()

        assertEquals(1, inventory.getAllCalls)
        assertTrue(metricsExtension.sentMetrics.isEmpty())

        inventory.gate!!.complete(Unit)
        firstEvent.join()
        secondEvent.join()

        assertEquals(1, inventory.getAllCalls)
        assertEquals(2, metricsExtension.sentMetrics.size)
    }

    @Test
    fun `a failing send doesn't escape or stop the other sends`() = runTest {
        cssExperiments.contentScopeExperiment1().enable(
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
    fun `a failure reading the experiments sends nothing and is retried on the next event`() = runTest {
        cssExperiments.contentScopeExperiment1().enable(captchaMetric())
        inventory.failure = IllegalStateException("inventory unavailable")

        handler.onEvent("captchaDetected")
        assertTrue(metricsExtension.sentMetrics.isEmpty())

        inventory.failure = null
        handler.onEvent("captchaDetected")

        assertEquals(2, inventory.getAllCalls)
        assertEquals(listOf("captchaSeen"), metricsExtension.sentMetrics.map { it.metric })
    }

    @Test
    fun `an experiment without settings sends nothing`() = runTest {
        cssExperiments.contentScopeExperiment1().enable(settings = null)

        handler.onEvent("captchaDetected")

        assertTrue(metricsExtension.sentMetrics.isEmpty())
    }

    private fun captchaMetric() =
        metrics(""""captchaSeen": { "event": "captchaDetected", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }""")

    private fun metrics(entries: String) = """{ "metrics": { ${entries.trimIndent()} } }"""

    @SuppressLint("DenyListedApi")
    private fun Toggle.enable(settings: String?) = setRawStoredState(Toggle.State(remoteEnableState = true, enable = true, settings = settings))

    @SuppressLint("DenyListedApi")
    private fun Toggle.disable(settings: String?) = setRawStoredState(Toggle.State(remoteEnableState = false, enable = false, settings = settings))

    private class FakeInventory(private val toggles: List<Toggle>) : FeatureTogglesInventory {
        var getAllCalls = 0
        var gate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null

        override suspend fun getAll(): List<Toggle> {
            getAllCalls++
            gate?.await()
            failure?.let { throw it }
            return toggles
        }
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
        fun notAnExperiment(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun tdsNextExperiment001(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun tdsNextExperiment002(): Toggle
    }

    interface TestOtherFeature {
        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun self(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun experimentLike(): Toggle
    }
}
