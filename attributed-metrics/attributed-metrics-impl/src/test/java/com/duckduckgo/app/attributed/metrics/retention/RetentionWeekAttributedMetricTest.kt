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

package com.duckduckgo.app.attributed.metrics.retention

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.attributed.metrics.AttributedMetricsConfigFeature
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricConfig
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RetentionWeekAttributedMetricTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val attributedMetricConfig: AttributedMetricConfig = mock()
    private val appInstall: AppInstall = mock()
    private val dateUtils: AttributedMetricsDateUtils = mock()
    private val retentionToggle = FakeFeatureToggleFactory.create(AttributedMetricsConfigFeature::class.java)

    private lateinit var testee: RetentionWeekAttributedMetric

    @Before
    fun setup() = runTest {
        retentionToggle.retention().setRawStoredState(State(true))
        retentionToggle.canEmitRetention().setRawStoredState(State(true))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(retentionToggle.retention(), retentionToggle.canEmitRetention()),
        )
        testee = RetentionWeekAttributedMetric(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            attributedMetricClient = attributedMetricClient,
            appInstall = appInstall,
            dateUtils = dateUtils,
            attributedMetricConfig = attributedMetricConfig,
        )
    }

    @Test
    fun whenPixelNameRequestedThenReturnCorrectName() {
        assertEquals("user_retention_week", testee.getPixelName())
    }

    @Test
    fun whenAtbNotChangedThenDoNotEmitMetric() = runTest {
        testee.onAppRetentionAtbRefreshed("atb", "atb")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenAppOpensAndDaysLessThan1ThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(0)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenAppOpensAndDaysIs1ThenEmitMetric() = runTest {
        givenDaysSinceInstalled(1)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient).emitMetric(testee)
    }

    @Test
    fun whenAppOpensAndDaysIs1ButFFDisabledThenDoNotEmitMetric() = runTest {
        retentionToggle.retention().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(retentionToggle.retention(), retentionToggle.canEmitRetention()),
        )
        givenDaysSinceInstalled(1)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenAppOpensAndDaysIs1AndEmitDisabledThenDoNotEmitMetric() = runTest {
        retentionToggle.retention().setRawStoredState(State(true))
        retentionToggle.canEmitRetention().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(retentionToggle.retention(), retentionToggle.canEmitRetention()),
        )
        givenDaysSinceInstalled(1)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenDaysLessThan1ThenReturnEmptyParameters() = runTest {
        givenDaysSinceInstalled(0)

        val params = testee.getMetricParameters()

        assertEquals(emptyMap<String, String>(), params)
    }

    @Test
    fun whenDaysInstalledThenReturnCorrectPeriod() = runTest {
        whenever(attributedMetricConfig.getBucketConfiguration()).thenReturn(
            mapOf(
                "user_retention_week" to MetricBucket(
                    buckets = listOf(1, 2, 3, 4),
                    version = 0,
                ),
            ),
        )
        // Map of days installed to expected period number
        val periodRanges = mapOf(
            0 to -1, // Day 0 -> not captured
            1 to 0, // Day 1 -> week 1, Bucket 0
            4 to 0, // Day 4 -> week 1, Bucket 0
            7 to 0, // Day 7 -> week 1, Bucket 0
            8 to 1, // Day 8 -> week 2, Bucket 1
            11 to 1, // Day 11 -> week 2, Bucket 1
            14 to 1, // Day 14 -> week 2, Bucket 1
            15 to 2, // Day 15 -> week 3, Bucket 2
            18 to 2, // Day 18 -> week 3, Bucket 2
            21 to 2, // Day 21 -> week 3, Bucket 2
            22 to 3, // Day 22 -> week 4, Bucket 3
            25 to 3, // Day 25 -> week 4, Bucket 3
            28 to 3, // Day 28 -> week 4, Bucket 3
            29 to -1, // Day 29 -> outside first month
            35 to -1, // Day 35 -> outside first month
        )

        periodRanges.forEach { (days, expectedPeriod) ->
            givenDaysSinceInstalled(days)

            val params = testee.getMetricParameters()

            val expectedParams = if (expectedPeriod > -1) {
                mapOf("count" to expectedPeriod.toString())
            } else {
                emptyMap()
            }

            assertEquals(
                "For $days days installed, should return period $expectedPeriod",
                expectedParams,
                params,
            )
        }
    }

    @Test
    fun whenDaysInstalledThenReturnCorrectTag() = runTest {
        whenever(attributedMetricConfig.getBucketConfiguration()).thenReturn(
            mapOf(
                "user_retention_week" to MetricBucket(
                    buckets = listOf(1, 2, 3, 4),
                    version = 0,
                ),
            ),
        )

        // Test different days and expected period numbers
        val testCases = mapOf(
            0 to "-1", // Day 0 -> not captured
            1 to "0", // Day 1 -> week 1, Bucket 0
            4 to "0", // Day 4 -> week 1, Bucket 0
            7 to "0", // Day 7 -> week 1, Bucket 0
            8 to "1", // Day 8 -> week 2, Bucket 1
            11 to "1", // Day 11 -> week 2, Bucket 1
            14 to "1", // Day 14 -> week 2, Bucket 1
            15 to "2", // Day 15 -> week 3, Bucket 2
            18 to "2", // Day 18 -> week 3, Bucket 2
            21 to "2", // Day 21 -> week 3, Bucket 2
            22 to "3", // Day 22 -> week 4, Bucket 3
            25 to "3", // Day 25 -> week 4, Bucket 3
            28 to "3", // Day 28 -> week 4, Bucket 3
            29 to "-1", // Day 29 -> outside first month
            35 to "-1", // Day 35 -> outside first month
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

    private fun givenDaysSinceInstalled(days: Int) {
        whenever(appInstall.getInstallationTimestamp()).thenReturn(123L)
        whenever(dateUtils.daysSince(123L)).thenReturn(days)
    }
}
