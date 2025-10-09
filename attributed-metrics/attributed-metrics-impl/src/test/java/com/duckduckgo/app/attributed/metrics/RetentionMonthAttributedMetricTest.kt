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
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.test.CoroutineTestRule
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

@RunWith(AndroidJUnit4::class)
class RetentionMonthAttributedMetricTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val appInstall: AppInstall = mock()
    private val dateUtils: AttributedMetricsDateUtils = mock()

    private lateinit var testee: RetentionMonthAttributedMetric

    @Before
    fun setup() {
        testee = RetentionMonthAttributedMetric(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appInstall = appInstall,
            attributedMetricClient = attributedMetricClient,
            dateUtils = dateUtils,
        )
    }

    @Test
    fun whenPixelNameRequestedThenReturnCorrectName() {
        assertEquals("user_retention_month", testee.getPixelName())
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
    fun whenDaysLessThan29ThenReturnEmptyParameters() = runTest {
        givenDaysSinceInstalled(28)

        val params = testee.getMetricParameters()

        assertEquals(emptyMap<String, String>(), params)
    }

    @Test
    fun whenDaysInstalledThenReturnCorrectPeriod() = runTest {
        // Map of days installed to expected period number
        val periodRanges = mapOf(
            28 to 0, // Day 28 -> No period (empty map)
            29 to 1, // Day 29 -> Period 1 (first month)
            45 to 1, // Day 45 -> Period 1 (still first month)
            57 to 2, // Day 57 -> Period 2 (second month)
            85 to 3, // Day 85 -> Period 3 (third month)
            113 to 4, // Day 113 -> Period 4 (fourth month)
            141 to 5, // Day 141 -> Period 5 (fifth month)
            169 to 6, // Day 169 -> Period 6 (sixth month)
            197 to 7, // Day 197 -> Period 7 (seventh month)
        )

        periodRanges.forEach { (days, expectedPeriod) ->
            givenDaysSinceInstalled(days)

            val params = testee.getMetricParameters()

            val expectedParams = if (expectedPeriod > 0) {
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
        // Test different days and expected period numbers
        val testCases = mapOf(
            28 to "0", // Day 28 -> No period
            29 to "1", // Day 29 -> Period 1
            57 to "2", // Day 57 -> Period 2
            85 to "3", // Day 85 -> Period 3
            113 to "4", // Day 113 -> Period 4
            141 to "5", // Day 141 -> Period 5
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
