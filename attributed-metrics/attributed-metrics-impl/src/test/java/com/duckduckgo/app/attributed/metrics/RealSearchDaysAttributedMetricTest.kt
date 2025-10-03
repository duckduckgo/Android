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

package com.duckduckgo.app.attributed.metrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.test.CoroutineTestRule
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

@RunWith(AndroidJUnit4::class)
class RealSearchDaysAttributedMetricTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val appInstall: AppInstall = mock()
    private val statisticsDataStore: StatisticsDataStore = mock()
    private val dateUtils: AttributedMetricsDateUtils = mock()
    private lateinit var testee: RealSearchDaysAttributedMetric

    @Before
    fun setup() {
        testee = RealSearchDaysAttributedMetric(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            attributedMetricClient = attributedMetricClient,
            appInstall = appInstall,
            statisticsDataStore = statisticsDataStore,
            dateUtils = dateUtils,
        )
    }

    @Test
    fun whenOnFirstSearchThenCollectEventCalled() {
        testee.onSearchRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient).collectEvent("ddg_search_days")
    }

    @Test
    fun whenOnEachSearchThenCollectEventCalled() {
        testee.onSearchRetentionAtbRefreshed("same", "same")

        verify(attributedMetricClient).collectEvent("ddg_search_days")
    }

    @Test
    fun whenPixelNameRequestedThenReturnCorrectName() {
        assertEquals("user_active_past_week", testee.getPixelName())
    }

    @Test
    fun whenFirstSearchOfDayIfInstallationDayThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(0)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenFirstSearchOfDayIfNoDaysWithEventsThenDoNotEmitMetric() = runTest {
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
    fun whenFirstSearchOfDayIfHasDaysWithEventsThenEmitMetric() = runTest {
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
    fun whenGetTagThenReturnAppRetentionAtb() = runTest {
        whenever(statisticsDataStore.appRetentionAtb).thenReturn("v123-1")

        assertEquals("v123-1", testee.getTag())
    }

    @Test
    fun givenCompleteDataWindowThenReturnCorrectDaysBucketInParams() = runTest {
        givenDaysSinceInstalled(7)

        // Map of days with events to expected bucket
        val daysWithEventsExpectedBuckets = mapOf(
            0 to 0,
            1 to 0,
            2 to 0,
            3 to 1,
            4 to 1,
            5 to 2,
            6 to 2,
            7 to 2,
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
                "days" to "2",
                "daysSinceInstalled" to "5",
            ),
            params,
        )
    }

    @Test
    fun whenDaysSinceInstalledIs8ThenDoNotIncludeDaysSinceInstalled() = runTest {
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
            mapOf("days" to "2"),
            params,
        )
    }

    private fun givenDaysSinceInstalled(days: Int) {
        whenever(appInstall.getInstallationTimestamp()).thenReturn(123L)
        whenever(dateUtils.daysSince(123L)).thenReturn(days)
    }
}
