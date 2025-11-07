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
class RetentionMonthAttributedMetricTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val attributedMetricConfig: AttributedMetricConfig = mock()
    private val appInstall: AppInstall = mock()
    private val dateUtils: AttributedMetricsDateUtils = mock()
    private val retentionToggle = FakeFeatureToggleFactory.create(AttributedMetricsConfigFeature::class.java)

    private lateinit var testee: RetentionMonthAttributedMetric

    @Before
    fun setup() = runTest {
        retentionToggle.retention().setRawStoredState(State(true))
        retentionToggle.canEmitRetention().setRawStoredState(State(true))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(retentionToggle.retention(), retentionToggle.canEmitRetention()),
        )
        whenever(attributedMetricConfig.getBucketConfiguration()).thenReturn(
            mapOf(
                "attributed_metric_retention_month" to MetricBucket(
                    buckets = listOf(2, 3, 4, 5),
                    version = 0,
                ),
            ),
        )
        testee = RetentionMonthAttributedMetric(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appInstall = appInstall,
            attributedMetricClient = attributedMetricClient,
            dateUtils = dateUtils,
            attributedMetricConfig = attributedMetricConfig,
        )
    }

    @Test
    fun whenPixelNameRequestedThenReturnCorrectName() {
        assertEquals("attributed_metric_retention_month", testee.getPixelName())
    }

    @Test
    fun whenAtbNotChangedThenDoNotEmitMetric() = runTest {
        testee.onAppRetentionAtbRefreshed("atb", "atb")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenAppOpensAndDaysLessThan29ThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(28)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenAppOpensAndDaysIs29ThenEmitMetric() = runTest {
        givenDaysSinceInstalled(29)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient).emitMetric(testee)
    }

    @Test
    fun whenAppOpensAndDaysIs29ButFFDisabledThenDoNotEmitMetric() = runTest {
        retentionToggle.retention().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(retentionToggle.retention(), retentionToggle.canEmitRetention()),
        )
        givenDaysSinceInstalled(29)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenAppOpensAndDaysIs29AndEmitDisabledThenDoNotEmitMetric() = runTest {
        retentionToggle.retention().setRawStoredState(State(true))
        retentionToggle.canEmitRetention().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(retentionToggle.retention(), retentionToggle.canEmitRetention()),
        )
        givenDaysSinceInstalled(29)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenDaysLessThan29ThenReturnEmptyParameters() = runTest {
        givenDaysSinceInstalled(28)

        val params = testee.getMetricParameters()

        assertEquals(emptyMap<String, String>(), params)
    }

    @Test
    fun whenDaysInstalledThenReturnCorrectPeriod() = runTest {
        // Map of days installed to expected period number
        val periodRanges = mapOf(
            10 to -1, // Day 10 -> month 1, not captured by this metric
            28 to -1, // Day 28 -> month 1, not captured by this metric
            29 to 0, // Day 29 -> month 2, Bucket 0
            45 to 0, // Day 45 -> month 2, Bucket 0
            56 to 0, // Day 57 -> month 2, Bucket 0
            57 to 1, // Day 57 -> month 3, Bucket 1
            85 to 2, // Day 85 -> month 4, Bucket 2
            113 to 3, // Day 113 -> month 5, Bucket 3
            141 to 4, // Day 141 -> month 6, Bucket 4
            169 to 4, // Day 169 -> month 7, Bucket 4
            197 to 4, // Day 197 -> month 8, Bucket 4
        )

        periodRanges.forEach { (days, expectedPeriod) ->
            givenDaysSinceInstalled(days)

            val params = testee.getMetricParameters()["count"]

            val expectedCount = if (expectedPeriod > -1) {
                expectedPeriod.toString()
            } else {
                null
            }

            assertEquals(
                "For $days days installed, should return period $expectedPeriod",
                expectedCount,
                params,
            )
        }
    }

    @Test
    fun whenDaysInstalledThenReturnCorrectTag() = runTest {
        // Test different days and expected period numbers
        val testCases = mapOf(
            10 to "-1", // Day 10 -> month 1, not captured by this metric
            28 to "-1", // Day 28 -> month 1, not captured by this metric
            29 to "0", // Day 29 -> month 2, Bucket 0
            45 to "0", // Day 45 -> month 2, Bucket 0
            56 to "0", // Day 57 -> month 2, Bucket 0
            57 to "1", // Day 57 -> month 3, Bucket 1
            85 to "2", // Day 85 -> month 4, Bucket 2
            113 to "3", // Day 113 -> month 5, Bucket 3
            141 to "4", // Day 141 -> month 6, Bucket 4
            169 to "4", // Day 169 -> month 7, Bucket 4
            197 to "4", // Day 197 -> month 8, Bucket 4
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

    @Test
    fun whenGetMetricParametersThenReturnVersion() = runTest {
        givenDaysSinceInstalled(29)

        val version = testee.getMetricParameters()["version"]

        assertEquals("0", version)
    }

    private fun givenDaysSinceInstalled(days: Int) {
        whenever(appInstall.getInstallationTimestamp()).thenReturn(123L)
        whenever(dateUtils.daysSince(123L)).thenReturn(days)
    }
}
