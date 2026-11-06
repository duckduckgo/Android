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

package com.duckduckgo.duckchat.impl.rmf

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class LastDuckAiUsageSurveyParameterPluginTest {

    private val mockRepository: DuckChatFeatureRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

    private lateinit var plugin: LastDuckAiUsageSurveyParameterPlugin

    private val now = 1_750_000_000_000L

    @Before
    fun setup() {
        plugin = LastDuckAiUsageSurveyParameterPlugin(mockRepository, mockCurrentTimeProvider)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
    }

    @Test
    fun whenDuckAiWasNeverUsedThenReturnsNone() = runTest {
        whenever(mockRepository.lastSessionTimestamp()).thenReturn(0L)

        assertEquals("none", plugin.evaluate("last_duck_ai_usage"))
    }

    @Test
    fun whenLastUsageWasLessThanADayAgoThenReturnsDay() = runTest {
        whenever(mockRepository.lastSessionTimestamp()).thenReturn(now - TimeUnit.HOURS.toMillis(6))

        assertEquals("day", plugin.evaluate("last_duck_ai_usage"))
    }

    @Test
    fun whenLastUsageWasOneDayAgoThenReturnsDay() = runTest {
        whenever(mockRepository.lastSessionTimestamp()).thenReturn(now - TimeUnit.DAYS.toMillis(1))

        assertEquals("day", plugin.evaluate("last_duck_ai_usage"))
    }

    @Test
    fun whenLastUsageWasTwoDaysAgoThenReturnsWeek() = runTest {
        whenever(mockRepository.lastSessionTimestamp()).thenReturn(now - TimeUnit.DAYS.toMillis(2))

        assertEquals("week", plugin.evaluate("last_duck_ai_usage"))
    }

    @Test
    fun whenLastUsageWasSevenDaysAgoThenReturnsWeek() = runTest {
        whenever(mockRepository.lastSessionTimestamp()).thenReturn(now - TimeUnit.DAYS.toMillis(7))

        assertEquals("week", plugin.evaluate("last_duck_ai_usage"))
    }

    @Test
    fun whenLastUsageWasEightDaysAgoThenReturnsNone() = runTest {
        whenever(mockRepository.lastSessionTimestamp()).thenReturn(now - TimeUnit.DAYS.toMillis(8))

        assertEquals("none", plugin.evaluate("last_duck_ai_usage"))
    }

    @Test
    fun whenMatchesReturnsTrueForCorrectKey() {
        assertTrue(plugin.matches("last_duck_ai_usage"))
    }

    @Test
    fun whenMatchesReturnsFalseForOtherKeys() {
        assertFalse(plugin.matches("last_search_state"))
    }
}
