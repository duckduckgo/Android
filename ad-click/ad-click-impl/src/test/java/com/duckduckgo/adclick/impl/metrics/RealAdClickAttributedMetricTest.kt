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

package com.duckduckgo.adclick.impl.metrics

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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.ZoneId

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealAdClickAttributedMetricTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val appInstall: AppInstall = mock()
    private val attributedMetricConfig: AttributedMetricConfig = mock()
    private val adClickToggle = FakeFeatureToggleFactory.create(FakeAttributedMetricsConfigFeature::class.java)

    private lateinit var testee: RealAdClickAttributedMetric

    @Before fun setup() = runTest {
        adClickToggle.adClickCountAvg().setRawStoredState(State(true))
        adClickToggle.canEmitAdClickCountAvg().setRawStoredState(State(true))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(adClickToggle.adClickCountAvg(), adClickToggle.canEmitAdClickCountAvg()),
        )
        whenever(attributedMetricConfig.getBucketConfiguration()).thenReturn(
            mapOf(
                "attributed_metric_average_ad_clicks_past_week" to MetricBucket(
                    buckets = listOf(2, 5),
                    version = 0,
                ),
            ),
        )
        testee = RealAdClickAttributedMetric(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            attributedMetricClient = attributedMetricClient,
            appInstall = appInstall,
            attributedMetricConfig = attributedMetricConfig,
        )
    }

    @Test fun whenPixelNameRequestedThenReturnCorrectName() {
        assertEquals("attributed_metric_average_ad_clicks_past_week", testee.getPixelName())
    }

    @Test fun whenAdClickAndDaysInstalledIsZeroThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(0)

        testee.onAdClick()

        verify(attributedMetricClient).collectEvent("ad_click")
        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test fun whenAdClickAndNoEventsThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats("ad_click", 7)).thenReturn(
            EventStats(
                daysWithEvents = 0,
                rollingAverage = 0.0,
                totalEvents = 0,
            ),
        )

        testee.onAdClick()

        verify(attributedMetricClient).collectEvent("ad_click")
        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test fun whenAdClickAndHasEventsThenEmitMetric() = runTest {
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats("ad_click", 7)).thenReturn(
            EventStats(
                daysWithEvents = 1,
                rollingAverage = 1.0,
                totalEvents = 1,
            ),
        )

        testee.onAdClick()

        verify(attributedMetricClient).collectEvent("ad_click")
        verify(attributedMetricClient).emitMetric(testee)
    }

    @Test
    fun whenAdClickedButFFDisabledThenDoNotCollectAndDoNotEmitMetric() = runTest {
        adClickToggle.adClickCountAvg().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(adClickToggle.adClickCountAvg(), adClickToggle.canEmitAdClickCountAvg()),
        )
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats("ad_click", 7)).thenReturn(
            EventStats(
                daysWithEvents = 1,
                rollingAverage = 1.0,
                totalEvents = 1,
            ),
        )

        testee.onAdClick()

        verify(attributedMetricClient, never()).collectEvent("ad_click")
        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test fun whenAdClickedButEmitDisabledThenCollectButDoNotEmitMetric() = runTest {
        adClickToggle.adClickCountAvg().setRawStoredState(State(true))
        adClickToggle.canEmitAdClickCountAvg().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(adClickToggle.adClickCountAvg(), adClickToggle.canEmitAdClickCountAvg()),
        )
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats("ad_click", 7)).thenReturn(
            EventStats(
                daysWithEvents = 1,
                rollingAverage = 1.0,
                totalEvents = 1,
            ),
        )

        testee.onAdClick()

        verify(attributedMetricClient).collectEvent("ad_click")
        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test fun whenDaysInstalledLessThanWindowThenIncludeDayAverageParameter() = runTest {
        givenDaysSinceInstalled(5)
        whenever(attributedMetricClient.getEventStats("ad_click", 5)).thenReturn(
            EventStats(
                daysWithEvents = 1,
                rollingAverage = 1.0,
                totalEvents = 1,
            ),
        )

        val params = testee.getMetricParameters()

        assertEquals("5", params["dayAverage"])
    }

    @Test fun whenDaysInstalledGreaterThanWindowThenOmitDayAverageParameter() = runTest {
        givenDaysSinceInstalled(8)
        whenever(attributedMetricClient.getEventStats("ad_click", 7)).thenReturn(
            EventStats(
                daysWithEvents = 1,
                rollingAverage = 1.0,
                totalEvents = 1,
            ),
        )

        val params = testee.getMetricParameters()

        assertNull(params["dayAverage"])
    }

    @Test fun whenGetMetricParametersThenReturnCorrectBucketValue() = runTest {
        // Map of average clicks to expected bucket value
        // clicks avg -> bucket
        val bucketRanges = mapOf(
            0.0 to 0, // 0 clicks -> bucket 0 (≤2)
            1.0 to 0, // 1 click -> bucket 0 (≤2)
            2.0 to 0, // 2 clicks -> bucket 0 (≤2)
            2.1 to 0, // 2.1 clicks rounds to 2 -> bucket 0 (≤2)
            2.5 to 1, // 2.5 clicks rounds to 3 -> bucket 1 (≤5)
            2.7 to 1, // 2.7 clicks rounds to 3 -> bucket 1 (≤5)
            3.0 to 1, // 3 clicks -> bucket 1 (≤5)
            5.0 to 1, // 5 clicks -> bucket 1 (≤5)
            5.1 to 1, // 5.1 clicks rounds to 5 -> bucket 1 (≤5)
            6.0 to 2, // 6 clicks -> bucket 2 (>5)
            10.0 to 2, // 10 clicks -> bucket 2 (>5)
        )

        bucketRanges.forEach { (clicksAvg, expectedBucket) ->
            givenDaysSinceInstalled(8)
            whenever(attributedMetricClient.getEventStats("ad_click", 7)).thenReturn(
                EventStats(
                    daysWithEvents = 1, // not relevant for this test
                    rollingAverage = clicksAvg,
                    totalEvents = 1, // not relevant for this test
                ),
            )

            val count = testee.getMetricParameters()["count"]

            assertEquals(
                "For $clicksAvg clicks, should return bucket $expectedBucket",
                expectedBucket.toString(),
                count,
            )
        }
    }

    @Test fun whenDaysInstalledThenReturnCorrectTag() = runTest {
        // Test different days
        // days installed -> expected tag
        val testCases = mapOf(
            0 to "0",
            1 to "1",
            7 to "7",
            30 to "30",
        )

        testCases.forEach { (days, expectedTag) ->
            givenDaysSinceInstalled(days)

            val tag = testee.getTag()

            assertEquals(
                "For $days days installed, should return tag $expectedTag",
                expectedTag,
                tag,
            )
        }
    }

    @Test fun whenGetMetricParametersThenReturnVersion() = runTest {
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats("ad_click", 7)).thenReturn(
            EventStats(
                daysWithEvents = 1,
                rollingAverage = 1.0,
                totalEvents = 1,
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

interface FakeAttributedMetricsConfigFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun adClickCountAvg(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun canEmitAdClickCountAvg(): Toggle
}
