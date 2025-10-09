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
class RetentionWeekAttributedMetricTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val appInstall: AppInstall = mock()
    private val dateUtils: AttributedMetricsDateUtils = mock()

    private lateinit var testee: RetentionWeekAttributedMetric

    @Before
    fun setup() {
        testee = RetentionWeekAttributedMetric(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            attributedMetricClient = attributedMetricClient,
            appInstall = appInstall,
            dateUtils = dateUtils,
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
    fun whenDaysInstalledIsZeroThenReturnEmptyParameters() = runTest {
        givenDaysSinceInstalled(0)

        val params = testee.getMetricParameters()

        assertEquals(emptyMap<String, String>(), params)
    }

    @Test
    fun whenDaysInstalledIs29ThenReturnEmptyParameters() = runTest {
        givenDaysSinceInstalled(29)

        val params = testee.getMetricParameters()

        assertEquals(emptyMap<String, String>(), params)
    }

    @Test
    fun whenDaysInstalledThenReturnCorrectWeek() = runTest {
        // Map of days installed to expected week number
        val weekRanges = mapOf(
            1 to 1, // Day 1 -> Week 1
            3 to 1, // Day 3 -> Week 1
            8 to 2, // Day 8 -> Week 2
            10 to 2, // Day 10 -> Week 2
            15 to 3, // Day 15 -> Week 3
            17 to 3, // Day 17 -> Week 3
            22 to 4, // Day 22 -> Week 4
            24 to 4, // Day 24 -> Week 4
        )

        weekRanges.forEach { (days, expectedWeek) ->
            givenDaysSinceInstalled(days)

            val params = testee.getMetricParameters()

            assertEquals(
                "For $days days installed, should return week $expectedWeek",
                mapOf("count" to expectedWeek.toString()),
                params,
            )
        }
    }

    @Test
    fun whenDaysInstalledThenReturnCorrectTag() = runTest {
        // Test different days and expected week numbers
        val testCases = mapOf(
            1 to "1", // Week 1
            8 to "2", // Week 2
            15 to "3", // Week 3
            22 to "4", // Week 4
            29 to "-1", // Outside range
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
    fun whenAppOpensAndDaysOutsideRangeThenDoNotEmitMetric() = runTest {
        givenDaysSinceInstalled(29)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenAppOpensAndDaysInRangeThenEmitMetric() = runTest {
        givenDaysSinceInstalled(7)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verify(attributedMetricClient).emitMetric(testee)
    }

    private fun givenDaysSinceInstalled(days: Int) {
        whenever(appInstall.getInstallationTimestamp()).thenReturn(123L)
        whenever(dateUtils.daysSince(123L)).thenReturn(days)
    }
}
