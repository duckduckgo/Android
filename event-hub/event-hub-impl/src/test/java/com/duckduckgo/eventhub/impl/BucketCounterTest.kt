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

package com.duckduckgo.eventhub.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BucketCounterTest {

    private val buckets = linkedMapOf(
        "0" to BucketConfig(gte = 0, lt = 1),
        "1-2" to BucketConfig(gte = 1, lt = 3),
        "3-5" to BucketConfig(gte = 3, lt = 6),
        "6-10" to BucketConfig(gte = 6, lt = 11),
        "11-20" to BucketConfig(gte = 11, lt = 21),
        "21-39" to BucketConfig(gte = 21, lt = 40),
        "40+" to BucketConfig(gte = 40, lt = null),
    )

    @Test
    fun `count 0 matches first bucket`() {
        assertEquals("0", BucketCounter.bucketCount(0, buckets))
    }

    @Test
    fun `count 1 matches 1-2 bucket`() {
        assertEquals("1-2", BucketCounter.bucketCount(1, buckets))
    }

    @Test
    fun `count 2 matches 1-2 bucket`() {
        assertEquals("1-2", BucketCounter.bucketCount(2, buckets))
    }

    @Test
    fun `count 3 matches 3-5 bucket`() {
        assertEquals("3-5", BucketCounter.bucketCount(3, buckets))
    }

    @Test
    fun `count 15 matches 11-20 bucket`() {
        assertEquals("11-20", BucketCounter.bucketCount(15, buckets))
    }

    @Test
    fun `count 40 matches open-ended bucket`() {
        assertEquals("40+", BucketCounter.bucketCount(40, buckets))
    }

    @Test
    fun `count 100 matches open-ended bucket`() {
        assertEquals("40+", BucketCounter.bucketCount(100, buckets))
    }

    @Test
    fun `no matching bucket returns null`() {
        val restricted = linkedMapOf("5-9" to BucketConfig(gte = 5, lt = 10))
        assertNull(BucketCounter.bucketCount(3, restricted))
    }

    @Test
    fun `empty buckets returns null`() {
        assertNull(BucketCounter.bucketCount(5, emptyMap()))
    }

    @Test
    fun `lt is exclusive`() {
        assertEquals("6-10", BucketCounter.bucketCount(10, buckets))
        assertEquals("11-20", BucketCounter.bucketCount(11, buckets))
    }

    @Test
    fun `shouldStopCounting returns true when at max bucket`() {
        assertTrue(BucketCounter.shouldStopCounting(40, buckets))
        assertTrue(BucketCounter.shouldStopCounting(100, buckets))
    }

    @Test
    fun `shouldStopCounting returns false when higher buckets exist`() {
        assertFalse(BucketCounter.shouldStopCounting(0, buckets))
        assertFalse(BucketCounter.shouldStopCounting(5, buckets))
        assertFalse(BucketCounter.shouldStopCounting(39, buckets))
    }
}
