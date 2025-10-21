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

package com.duckduckgo.app.attributed.metrics.search

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.attributed.metrics.AttributedMetricsConfigFeature
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricConfig
import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SearchAttributedMetricTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val appInstall: AppInstall = mock()
    private val statisticsDataStore: StatisticsDataStore = mock()
    private val dateUtils: AttributedMetricsDateUtils = mock()
    private val attributedMetricConfig: AttributedMetricConfig = mock()
    private val searchToggle = FakeFeatureToggleFactory.create(AttributedMetricsConfigFeature::class.java)

    private lateinit var testee: SearchAttributedMetric

    @Before
    fun setup() = runTest {
        searchToggle.searchCountAvg().setRawStoredState(State(true))
        searchToggle.canEmitSearchCountAvg().setRawStoredState(State(true))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(searchToggle.searchCountAvg(), searchToggle.canEmitSearchCountAvg()),
        )
        whenever(attributedMetricConfig.getBucketConfiguration()).thenReturn(
            mapOf(
                "user_average_searches_past_week_first_month" to MetricBucket(
                    buckets = listOf(5, 9),
                    version = 0,
                ),
                "user_average_searches_past_week" to MetricBucket(
                    buckets = listOf(5, 9),
                    version = 0,
                ),
            ),
        )
        testee = SearchAttributedMetric(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            attributedMetricClient = attributedMetricClient,
            appInstall = appInstall,
            statisticsDataStore = statisticsDataStore,
            dateUtils = dateUtils,
            attributedMetricConfig = attributedMetricConfig,
        )
    }

    @Test
    fun whenOnSearchThenCollectEventCalled() = runTest {
        testee.onSearchRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient).collectEvent("ddg_search")
    }

    @Test
    fun whenOnSearchAndAtbNotChangedThenDoNotEmitMetric() = runTest {
        testee.onSearchRetentionAtbRefreshed("same", "same")

        verify(attributedMetricClient).collectEvent("ddg_search")
        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenGetTagThenReturnSearchRetentionAtb() = runTest {
        whenever(statisticsDataStore.searchRetentionAtb).thenReturn("v123-1")

        assertEquals("v123-1", testee.getTag())
    }

    @Test
    fun whenDaysSinceInstalledLessThan4WThenReturnFirstMonthPixelName() {
        givenDaysSinceInstalled(15)

        assertEquals("user_average_searches_past_week_first_month", testee.getPixelName())
    }

    @Test
    fun whenDaysSinceInstalledMoreThan4WThenReturnRegularPixelName() {
        givenDaysSinceInstalled(45)

        assertEquals("user_average_searches_past_week", testee.getPixelName())
    }

    @Test
    fun whenDaysSinceInstalledIsEndOf4WThenReturnFirstMonthPixelName() {
        givenDaysSinceInstalled(28)

        assertEquals("user_average_searches_past_week_first_month", testee.getPixelName())
    }

    @Test
    fun whenFirstSearchOfDayIfInstallationDayThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(0)

        testee.onSearchRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenFirstSearchOfDayIfRollingAverageIsZeroThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(3)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 0,
                daysWithEvents = 0,
                rollingAverage = 0.0,
            ),
        )

        testee.onSearchRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenFirstSearchOfDayIfRollingAverageIsNotZeroThenEmitMetric() = runTest {
        givenDaysSinceInstalled(3)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        testee.onSearchRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient).emitMetric(testee)
    }

    @Test
    fun whenSearchedButFFDisabledThenDoNotCollectAndDoNotEmitMetric() = runTest {
        searchToggle.searchCountAvg().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(searchToggle.searchCountAvg(), searchToggle.canEmitSearchCountAvg()),
        )
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        testee.onSearchRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).collectEvent("ddg_search")
        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenSearchedButEmitDisabledThenCollectButDoNotEmitMetric() = runTest {
        searchToggle.searchCountAvg().setRawStoredState(State(true))
        searchToggle.canEmitSearchCountAvg().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(searchToggle.searchCountAvg(), searchToggle.canEmitSearchCountAvg()),
        )
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        testee.onSearchRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient).collectEvent("ddg_search")
        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun given7dAverageThenReturnCorrectAverageBucketInParams() = runTest {
        whenever(attributedMetricConfig.getBucketConfiguration()).thenReturn(
            mapOf(
                "user_average_searches_past_week_first_month" to MetricBucket(
                    buckets = listOf(5, 9),
                    version = 0,
                ),
                "user_average_searches_past_week" to MetricBucket(
                    buckets = listOf(5, 9),
                    version = 0,
                ),
            ),
        )
        givenDaysSinceInstalled(7)

        // Map of 7d average to expected bucket
        val searches7dAvgExpectedBuckets = mapOf(
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

        searches7dAvgExpectedBuckets.forEach { (avg, bucket) ->
            whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
                EventStats(
                    totalEvents = 16,
                    daysWithEvents = 3,
                    rollingAverage = avg,
                ),
            )

            val params = testee.getMetricParameters()

            assertEquals(
                "For $avg searches, should return bucket $bucket",
                mapOf("count" to bucket.toString()),
                params,
            )
        }
    }

    @Test
    fun getMetricParametersAndDaysSinceInstalledLessThan7ThenIncludeDayAverage() = runTest {
        givenDaysSinceInstalled(5)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        val params = testee.getMetricParameters()

        assertEquals("5", params["dayAverage"])
    }

    @Test
    fun getMetricParametersAndDaysSinceInstalledMoreThan7ThenDoNotIncludeDaysSinceInstall() =
        runTest {
            givenDaysSinceInstalled(10)
            whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
                EventStats(
                    totalEvents = 16,
                    daysWithEvents = 3,
                    rollingAverage = 5.3,
                ),
            )

            val params = testee.getMetricParameters()

            assertNull(params["dayAverage"])
        }

    @Test
    fun getMetricParametersAndDaysSinceInstalledLessThan7ThenCalculateStatsWithExistingWindow() =
        runTest {
            givenDaysSinceInstalled(3)
            whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
                EventStats(
                    totalEvents = 16,
                    daysWithEvents = 3,
                    rollingAverage = 5.3,
                ),
            )

            testee.getMetricParameters()

            verify(attributedMetricClient).getEventStats(eq("ddg_search"), eq(3))
        }

    @Test
    fun getMetricParametersAndDaysSinceInstalledIsCompleteDataWindowThenCalculateStats7d() =
        runTest {
            givenDaysSinceInstalled(7)
            whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
                EventStats(
                    totalEvents = 16,
                    daysWithEvents = 3,
                    rollingAverage = 5.3,
                ),
            )

            testee.getMetricParameters()

            verify(attributedMetricClient).getEventStats(eq("ddg_search"), eq(7))
        }

    private fun givenDaysSinceInstalled(days: Int) {
        whenever(appInstall.getInstallationTimestamp()).thenReturn(123L)
        whenever(dateUtils.daysSince(123L)).thenReturn(days)
    }
}
