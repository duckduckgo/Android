/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.referral

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DurationBucketMapperTest {

    private lateinit var testee: DurationBucketMapper

    @Before
    fun setup() {
        testee = DurationBucketMapper()
    }

    @Test
    fun whenDurationOneBelowNextThresholdThenCurrentBucketReturned() {
        assertEquals("0", testee.mapDurationToBucket(99))
        assertEquals("1", testee.mapDurationToBucket(199))
        assertEquals("2", testee.mapDurationToBucket(499))
        assertEquals("3", testee.mapDurationToBucket(999))
        assertEquals("4", testee.mapDurationToBucket(1_499))
        assertEquals("5", testee.mapDurationToBucket(1_999))
        assertEquals("6", testee.mapDurationToBucket(2_499))
    }

    @Test
    fun whenDurationExactlyOnThresholdThenNextBucketReturned() {
        assertEquals("0", testee.mapDurationToBucket(0))
        assertEquals("1", testee.mapDurationToBucket(100))
        assertEquals("2", testee.mapDurationToBucket(200))
        assertEquals("3", testee.mapDurationToBucket(500))
        assertEquals("4", testee.mapDurationToBucket(1_000))
        assertEquals("5", testee.mapDurationToBucket(1_500))
        assertEquals("6", testee.mapDurationToBucket(2_000))
        assertEquals("7", testee.mapDurationToBucket(2_500))
    }

    @Test
    fun whenDurationOneAboveThresholdThenNextBucketReturned() {
        assertEquals("0", testee.mapDurationToBucket(1))
        assertEquals("1", testee.mapDurationToBucket(101))
        assertEquals("2", testee.mapDurationToBucket(201))
        assertEquals("3", testee.mapDurationToBucket(501))
        assertEquals("4", testee.mapDurationToBucket(1_001))
        assertEquals("5", testee.mapDurationToBucket(1_501))
        assertEquals("6", testee.mapDurationToBucket(2_001))
        assertEquals("7", testee.mapDurationToBucket(2_501))
    }

}