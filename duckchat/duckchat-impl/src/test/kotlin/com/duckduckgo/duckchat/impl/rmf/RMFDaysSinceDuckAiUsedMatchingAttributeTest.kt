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

package com.duckduckgo.duckchat.impl.rmf

import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RMFDaysSinceDuckAiUsedMatchingAttributeTest {

    private lateinit var matcher: RMFDaysSinceDuckAiUsedMatchingAttribute
    private val mockRepository: DuckChatFeatureRepository = mock()

    @Before
    fun setup() {
        matcher = RMFDaysSinceDuckAiUsedMatchingAttribute(mockRepository)
    }

    @Test
    fun whenAttributeIsDefaultThenReturnsFalse() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute()

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun whenExactValueMatchesThenReturnsTrue() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(value = 5)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(5 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun whenExactValueDoesNotMatchThenReturnsFalse() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(value = 5)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(3 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun whenValueIsInRangeThenReturnsTrue() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(min = 5, max = 10)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(7 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun whenValueIsEqualToMinThenReturnsTrue() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(min = 5, max = 10)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(5 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun whenValueIsEqualToMaxThenReturnsTrue() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(min = 5, max = 10)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(10 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun whenValueIsBelowMinThenReturnsFalse() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(min = 5, max = 10)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(3 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun whenValueIsAboveMaxThenReturnsFalse() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(min = 5, max = 10)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(12 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun whenOnlyMinIsSetAndValueIsAboveMinThenReturnsTrue() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(min = 5)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(10 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun whenOnlyMinIsSetAndValueIsBelowMinThenReturnsFalse() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(min = 5)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(3 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun whenOnlyMaxIsSetAndValueIsBelowMaxThenReturnsTrue() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(max = 10)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(5 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun whenOnlyMaxIsSetAndValueIsAboveMaxThenReturnsFalse() = runTest {
        val attribute = DaysSinceDuckAiUsedMatchingAttribute(max = 10)
        whenever(mockRepository.sessionDeltaInMinutes()).thenReturn(15 * 24 * 60L)

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun whenNonMatchingAttributeThenReturnsNull() = runTest {
        val attribute = object : com.duckduckgo.remote.messaging.api.MatchingAttribute {}

        val result = matcher.evaluate(attribute)

        assertNull(result)
    }

    @Test
    fun whenMappingValidJsonWithValueThenReturnsAttribute() {
        val json = JsonMatchingAttribute(value = 5)

        val result = matcher.map(DaysSinceDuckAiUsedMatchingAttribute.KEY, json)

        assertEquals(DaysSinceDuckAiUsedMatchingAttribute(value = 5), result)
    }

    @Test
    fun whenMappingValidJsonWithRangeThenReturnsAttribute() {
        val json = JsonMatchingAttribute(min = 5, max = 10)

        val result = matcher.map(DaysSinceDuckAiUsedMatchingAttribute.KEY, json)

        assertEquals(DaysSinceDuckAiUsedMatchingAttribute(min = 5, max = 10), result)
    }

    @Test
    fun whenMappingJsonWithDoubleThenConvertsToInt() {
        val json = JsonMatchingAttribute(value = 5.0)

        val result = matcher.map(DaysSinceDuckAiUsedMatchingAttribute.KEY, json)

        assertEquals(DaysSinceDuckAiUsedMatchingAttribute(value = 5), result)
    }

    @Test
    fun whenMappingJsonWithLongThenConvertsToInt() {
        val json = JsonMatchingAttribute(value = 5L)

        val result = matcher.map(DaysSinceDuckAiUsedMatchingAttribute.KEY, json)

        assertEquals(DaysSinceDuckAiUsedMatchingAttribute(value = 5), result)
    }

    @Test
    fun whenMappingJsonWithNullValuesThenReturnsDefaultAttribute() {
        val json = JsonMatchingAttribute(value = null, min = null, max = null)

        val result = matcher.map(DaysSinceDuckAiUsedMatchingAttribute.KEY, json)

        assertEquals(DaysSinceDuckAiUsedMatchingAttribute(), result)
    }

    @Test
    fun whenMappingWithWrongKeyThenReturnsNull() {
        val json = JsonMatchingAttribute(value = 5)

        val result = matcher.map("wrongKey", json)

        assertNull(result)
    }
}
