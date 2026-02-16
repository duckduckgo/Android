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

package com.duckduckgo.webtelemetry.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BucketCounterTest {

    private val buckets = listOf("0-1", "2-3", "4-5", "6-10", "11-20", "21-39", "40+")

    @Test
    fun `count 0 matches first bucket`() {
        assertEquals("0-1", BucketCounter.bucketCount(0, buckets))
    }

    @Test
    fun `count 1 matches first bucket`() {
        assertEquals("0-1", BucketCounter.bucketCount(1, buckets))
    }

    @Test
    fun `count 2 matches second bucket`() {
        assertEquals("2-3", BucketCounter.bucketCount(2, buckets))
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
    fun `exact bucket matches`() {
        val exactBuckets = listOf("0", "1", "2", "3+")
        assertEquals("0", BucketCounter.bucketCount(0, exactBuckets))
        assertEquals("1", BucketCounter.bucketCount(1, exactBuckets))
        assertEquals("2", BucketCounter.bucketCount(2, exactBuckets))
        assertEquals("3+", BucketCounter.bucketCount(3, exactBuckets))
        assertEquals("3+", BucketCounter.bucketCount(999, exactBuckets))
    }

    @Test
    fun `no matching bucket returns null`() {
        val restrictedBuckets = listOf("0-5", "10-20")
        assertNull(BucketCounter.bucketCount(7, restrictedBuckets))
    }

    @Test
    fun `empty buckets returns null`() {
        assertNull(BucketCounter.bucketCount(5, emptyList()))
    }

    @Test
    fun `first matching bucket wins`() {
        val overlapping = listOf("0-10", "5-15", "10+")
        assertEquals("0-10", BucketCounter.bucketCount(7, overlapping))
    }
}
