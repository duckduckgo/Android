/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.metric

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricConfig
import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.ZoneId

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class DuckAiAttributedMetricTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val appInstall: AppInstall = mock()
    private val attributedMetricConfig: AttributedMetricConfig = mock()
    private val aiToggle = FakeFeatureToggleFactory.create(FakeDuckAiMetricsConfigFeature::class.java)

    private lateinit var testee: DuckAiAttributedMetric

    @Before
    fun setup() = runTest {
        aiToggle.aiUsageAvg().setRawStoredState(State(true))
        aiToggle.canEmitAIUsageAvg().setRawStoredState(State(true))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(aiToggle.aiUsageAvg(), aiToggle.canEmitAIUsageAvg()),
        )
        whenever(attributedMetricConfig.getBucketConfiguration()).thenReturn(
            mapOf(
                "attributed_metric_average_duck_ai_usage_past_week" to MetricBucket(
                    buckets = listOf(5, 9),
                    version = 0,
                ),
            ),
        )

        testee = DuckAiAttributedMetric(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            attributedMetricClient = attributedMetricClient,
            appInstall = appInstall,
            attributedMetricConfig = attributedMetricConfig,
        )
    }

    @Test
    fun whenPixelNameRequestedThenReturnCorrectName() {
        assertEquals("attributed_metric_average_duck_ai_usage_past_week", testee.getPixelName())
    }

    @Test
    fun whenMessageSentButFFDisabledThenDoNotCollectMetric() = runTest {
        aiToggle.aiUsageAvg().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(aiToggle.aiUsageAvg(), aiToggle.canEmitAIUsageAvg()),
        )
        givenDaysSinceInstalled(7)

        testee.onMessageSent()

        verify(attributedMetricClient, never()).collectEvent("submit_prompt")
    }

    @Test
    fun whenMessageSentAndFFEnabledThenCollectMetric() = runTest {
        givenDaysSinceInstalled(7)

        testee.onMessageSent()

        verify(attributedMetricClient).collectEvent("submit_prompt")
    }

    @Test
    fun whenMessageSentAndInstallationDayThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(0)

        testee.onMessageSent()

        verify(attributedMetricClient).collectEvent("submit_prompt")
        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenMessageSentAndNoEventsThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(3)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 0,
                daysWithEvents = 0,
                rollingAverage = 0.0,
            ),
        )

        testee.onMessageSent()

        verify(attributedMetricClient).collectEvent("submit_prompt")
        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenMessageSentAndHasEventsThenEmitMetric() = runTest {
        givenDaysSinceInstalled(3)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        testee.onMessageSent()

        verify(attributedMetricClient).collectEvent("submit_prompt")
        verify(attributedMetricClient).emitMetric(testee)
    }

    @Test
    fun whenMessageSentButEmitDisabledThenCollectButDoNotEmitMetric() = runTest {
        aiToggle.aiUsageAvg().setRawStoredState(State(true))
        aiToggle.canEmitAIUsageAvg().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(aiToggle.aiUsageAvg(), aiToggle.canEmitAIUsageAvg()),
        )
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        testee.onMessageSent()

        verify(attributedMetricClient).collectEvent("submit_prompt")
        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenGetMetricParametersThenReturnCorrectBucketValue() = runTest {
        givenDaysSinceInstalled(7)

        // Map of average usage to expected bucket
        val usageAvgExpectedBuckets = mapOf(
            2.2 to 0, // ≤5 -> bucket 0
            4.4 to 0, // ≤5 -> bucket 0
            5.0 to 0, // ≤5 -> bucket 0
            5.1 to 0, // rounds to 5, ≤5 -> bucket 0
            5.8 to 1, // rounds to 6, >5 and ≤9 -> bucket 1
            6.6 to 1, // >5 and ≤9 -> bucket 1
            9.0 to 1, // >5 and ≤9 -> bucket 1
            9.3 to 1, // rounds to 9, >5 and ≤9 -> bucket 1
            10.0 to 2, // >9 -> bucket 2
            14.1 to 2, // >9 -> bucket 2
        )

        usageAvgExpectedBuckets.forEach { (avg, bucket) ->
            whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
                EventStats(
                    totalEvents = 16,
                    daysWithEvents = 3,
                    rollingAverage = avg,
                ),
            )

            val realBucket = testee.getMetricParameters()["count"]

            assertEquals(
                "For $avg average usage, should return bucket $bucket",
                bucket.toString(),
                realBucket,
            )
        }
    }

    @Test
    fun whenDaysSinceInstalledLessThan7ThenIncludeDayAverage() = runTest {
        givenDaysSinceInstalled(5)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        val dayAverage = testee.getMetricParameters()["dayAverage"]

        assertEquals("5", dayAverage)
    }

    @Test
    fun whenDaysSinceInstalledIs7ThenDoNotIncludeDayAverage() = runTest {
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        val dayAverage = testee.getMetricParameters()["dayAverage"]

        assertNull(dayAverage)
    }

    @Test
    fun whenGetTagThenReturnDaysSinceInstalled() = runTest {
        givenDaysSinceInstalled(7)

        val tag = testee.getTag()

        assertEquals("7", tag)
    }

    @Test
    fun whenGetMetricParametersThenReturnVersion() = runTest {
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        val version = testee.getMetricParameters()["version"]

        assertEquals("0", version)
    }

    private fun givenDaysSinceInstalled(days: Int) {
        val etZone = ZoneId.of("America/New_York")
        val now = Instant.now()
        val nowInEt = now.atZone(etZone)
        val installInEt = nowInEt.minusDays(days.toLong())
        whenever(appInstall.getInstallationTimestamp()).thenReturn(installInEt.toInstant().toEpochMilli())
    }
}

interface FakeDuckAiMetricsConfigFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun aiUsageAvg(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun canEmitAIUsageAvg(): Toggle
}
