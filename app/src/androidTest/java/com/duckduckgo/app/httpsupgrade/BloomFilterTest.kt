/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.httpsupgrade

import org.junit.Assert.*
import org.junit.Test
import java.util.*
import kotlin.collections.ArrayList


class BloomFilterTest {

    private lateinit var testee: BloomFilter

    @Test
    fun whenBloomFilterEmptyThenContainsIsFalse() {
        testee = BloomFilter(FILTER_ELEMENT_COUNT, TARGET_ERROR_RATE)
        assertFalse(testee.contains("abc"))
    }

    @Test
    fun whenBloomFilterContainsElementThenContainsIsTrue() {
        testee = BloomFilter(FILTER_ELEMENT_COUNT, TARGET_ERROR_RATE)
        testee.add("abc")
        assertTrue(testee.contains("abc"))
    }

    @Test
    fun whenBloomFilterContainsItemsThenLookupResultsAreWithinRange() {

        val bloomData = createRandomStrings(FILTER_ELEMENT_COUNT)
        val testData = bloomData + createRandomStrings(ADDITIONAL_TEST_DATA_ELEMENT_COUNT)

        testee = BloomFilter(bloomData.size, TARGET_ERROR_RATE)
        bloomData.forEach { testee.add(it) }

        var (falsePositives, truePositives, falseNegatives, trueNegatives) = arrayOf(0, 0, 0, 0)
        for (element in testData) {
            val result = testee.contains(element)
            when {
                bloomData.contains(element) && !result -> falseNegatives++
                !bloomData.contains(element) && result -> falsePositives++
                !bloomData.contains(element) && !result -> trueNegatives++
                bloomData.contains(element) && result -> truePositives++
            }
        }

        val errorRate = falsePositives.toDouble() / testData.size.toDouble()
        assertEquals(0, falseNegatives)
        assertEquals(bloomData.size, truePositives)
        assertTrue(trueNegatives <= testData.size - bloomData.size)
        assertTrue(errorRate <= ACCEPTABLE_ERROR_RATE)
    }

    private fun createRandomStrings(items: Int): ArrayList<String> {
        val list = ArrayList<String>()
        repeat(items) { list.add(UUID.randomUUID().toString()) }
        return list
    }

    companion object {
        const val FILTER_ELEMENT_COUNT = 5000
        const val ADDITIONAL_TEST_DATA_ELEMENT_COUNT = 5000
        const val TARGET_ERROR_RATE = 0.001
        const val ACCEPTABLE_ERROR_RATE = TARGET_ERROR_RATE * 2
    }
}