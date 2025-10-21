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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SearchDaysAttributedMetricTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val appInstall: AppInstall = mock()
    private val statisticsDataStore: StatisticsDataStore = mock()
    private val dateUtils: AttributedMetricsDateUtils = mock()
    private val attributedMetricConfig: AttributedMetricConfig = mock()
    private val searchDaysToggle = FakeFeatureToggleFactory.create(AttributedMetricsConfigFeature::class.java)

    private lateinit var testee: SearchDaysAttributedMetric

    @Before
    fun setup() = runTest {
        searchDaysToggle.searchDaysAvg().setRawStoredState(State(true))
        searchDaysToggle.canEmitSearchDaysAvg().setRawStoredState(State(true))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(searchDaysToggle.searchDaysAvg(), searchDaysToggle.canEmitSearchDaysAvg()),
        )
        whenever(attributedMetricConfig.getBucketConfiguration()).thenReturn(
            mapOf(
                "user_active_past_week" to MetricBucket(
                    buckets = listOf(2, 4),
                    version = 0,
                ),
            ),
        )
        testee = SearchDaysAttributedMetric(
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
    fun whenOnFirstSearchThenCollectEventCalled() = runTest {
        testee.onSearchRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient).collectEvent("ddg_search_days")
    }

    @Test
    fun whenOnEachSearchThenCollectEventCalled() = runTest {
        testee.onSearchRetentionAtbRefreshed("same", "same")

        verify(attributedMetricClient).collectEvent("ddg_search_days")
    }

    @Test
    fun whenSearchedButFFDisabledThenDoNotCollectMetric() = runTest {
        searchDaysToggle.searchDaysAvg().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(searchDaysToggle.searchDaysAvg(), searchDaysToggle.canEmitSearchDaysAvg()),
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

        verify(attributedMetricClient, never()).collectEvent("ddg_search_days")
    }

    @Test
    fun whenPixelNameRequestedThenReturnCorrectName() {
        assertEquals("user_active_past_week", testee.getPixelName())
    }

    @Test
    fun whenAtbRefreshedIfInstallationDayThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(0)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenAtbRefreshedIfNoDaysWithEventsThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(3)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 0,
                daysWithEvents = 0,
                rollingAverage = 0.0,
            ),
        )

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenAtbRefreshedIfHasDaysWithEventsThenEmitMetric() = runTest {
        givenDaysSinceInstalled(3)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient).emitMetric(testee)
    }

    @Test
    fun whenAtbNotChangedThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(3)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        testee.onAppRetentionAtbRefreshed("same", "same")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenAtbRefreshedButEmitDisabledThenDoNotEmitMetric() = runTest {
        searchDaysToggle.searchDaysAvg().setRawStoredState(State(true))
        searchDaysToggle.canEmitSearchDaysAvg().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(searchDaysToggle.searchDaysAvg(), searchDaysToggle.canEmitSearchDaysAvg()),
        )
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 16,
                daysWithEvents = 3,
                rollingAverage = 5.3,
            ),
        )

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenGetTagThenReturnAppRetentionAtb() = runTest {
        whenever(statisticsDataStore.appRetentionAtb).thenReturn("v123-1")

        assertEquals("v123-1", testee.getTag())
    }

    @Test
    fun givenCompleteDataWindowThenReturnCorrectDaysBucketInParams() = runTest {
        givenDaysSinceInstalled(7)

        // Map of days with events to expected bucket
        val daysWithEventsExpectedBuckets = mapOf(
            0 to 0, // 0 days ≤2 -> bucket 0
            1 to 0, // 1 day ≤2 -> bucket 0
            2 to 0, // 2 days ≤2 -> bucket 0
            3 to 1, // 3 days >2 and ≤4 -> bucket 1
            4 to 1, // 4 days >2 and ≤4 -> bucket 1
            5 to 2, // 5 days >4 -> bucket 2
            6 to 2, // 6 days >4 -> bucket 2
            7 to 2, // 7 days >4 -> bucket 2
        )

        daysWithEventsExpectedBuckets.forEach { (days, bucket) ->
            whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
                EventStats(
                    totalEvents = days * 5, // Not relevant for this test
                    daysWithEvents = days,
                    rollingAverage = days.toDouble(), // Not relevant for this test
                ),
            )

            val params = testee.getMetricParameters()

            assertEquals(
                "For $days days with events, should return bucket $bucket",
                mapOf("days" to bucket.toString()),
                params,
            )
        }
    }

    @Test
    fun whenDaysSinceInstalledLessThan7ThenIncludeDaysSinceInstalled() = runTest {
        givenDaysSinceInstalled(5)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 25,
                daysWithEvents = 5,
                rollingAverage = 5.0,
            ),
        )

        val params = testee.getMetricParameters()

        assertEquals(
            mapOf(
                "days" to "2", // 5 days >4 -> bucket 2
                "daysSinceInstalled" to "5",
            ),
            params,
        )
    }

    @Test
    fun whenDaysSinceInstalledIs7ThenDoNotIncludeDaysSinceInstalled() = runTest {
        givenDaysSinceInstalled(7)
        whenever(attributedMetricClient.getEventStats(any(), any())).thenReturn(
            EventStats(
                totalEvents = 25,
                daysWithEvents = 5,
                rollingAverage = 5.0,
            ),
        )

        val params = testee.getMetricParameters()

        assertEquals(
            mapOf("days" to "2"), // 5 days >4 -> bucket 2
            params,
        )
    }

    private fun givenDaysSinceInstalled(days: Int) {
        whenever(appInstall.getInstallationTimestamp()).thenReturn(123L)
        whenever(dateUtils.daysSince(123L)).thenReturn(days)
    }
}
