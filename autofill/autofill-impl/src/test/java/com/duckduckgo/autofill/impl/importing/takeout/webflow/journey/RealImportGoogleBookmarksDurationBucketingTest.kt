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

package com.duckduckgo.autofill.impl.importing.takeout.webflow.journey

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

class RealImportGoogleBookmarksDurationBucketingTest {

    private val testee = RealImportGoogleBookmarksDurationBucketing()

    @Test
    fun whenInputIsNegativeThenBucketIsNegative() {
        assertEquals("negative", testee.bucket(-1))
    }

    @Test
    fun whenInputIsZeroThenBucketIs20() {
        assertEquals("20", testee.bucket(0))
    }

    @Test
    fun whenInputIs19SecondsThenBucketIs20() {
        assertEquals("20", testee.bucket(SECONDS.toMillis(19)))
    }

    @Test
    fun whenInputIs20SecondsThenBucketIs40() {
        assertEquals("40", testee.bucket(SECONDS.toMillis(20)))
    }

    @Test
    fun whenInputIs39SecondsThenBucketIs40() {
        assertEquals("40", testee.bucket(SECONDS.toMillis(39)))
    }

    @Test
    fun whenInputIs40SecondsThenBucketIs60() {
        assertEquals("60", testee.bucket(SECONDS.toMillis(40)))
    }

    @Test
    fun whenInputIs59SecondsThenBucketIs60() {
        assertEquals("60", testee.bucket(SECONDS.toMillis(59)))
    }

    @Test
    fun whenInputIs60SecondsThenBucketIs90() {
        assertEquals("90", testee.bucket(SECONDS.toMillis(60)))
    }

    @Test
    fun whenInputIs89SecondsThenBucketIs90() {
        assertEquals("90", testee.bucket(SECONDS.toMillis(89)))
    }

    @Test
    fun whenInputIs90SecondsThenBucketIs120() {
        assertEquals("120", testee.bucket(SECONDS.toMillis(90)))
    }

    @Test
    fun whenInputIs119SecondsThenBucketIs120() {
        assertEquals("120", testee.bucket(SECONDS.toMillis(119)))
    }

    @Test
    fun whenInputIs120SecondsThenBucketIs150() {
        assertEquals("150", testee.bucket(SECONDS.toMillis(120)))
    }

    @Test
    fun whenInputIs149SecondsThenBucketIs150() {
        assertEquals("150", testee.bucket(SECONDS.toMillis(149)))
    }

    @Test
    fun whenInputIs150SecondsThenBucketIs180() {
        assertEquals("180", testee.bucket(SECONDS.toMillis(150)))
    }

    @Test
    fun whenInputIs179SecondsThenBucketIs180() {
        assertEquals("180", testee.bucket(SECONDS.toMillis(179)))
    }

    @Test
    fun whenInputIs180SecondsThenBucketIs240() {
        assertEquals("240", testee.bucket(SECONDS.toMillis(180)))
    }

    @Test
    fun whenInputIs239SecondsThenBucketIs240() {
        assertEquals("240", testee.bucket(SECONDS.toMillis(239)))
    }

    @Test
    fun whenInputIs240SecondsThenBucketIs300() {
        assertEquals("300", testee.bucket(SECONDS.toMillis(240)))
    }

    @Test
    fun whenInputIs299SecondsThenBucketIs300() {
        assertEquals("300", testee.bucket(SECONDS.toMillis(299)))
    }

    @Test
    fun whenInputIs300SecondsThenBucketIsLonger() {
        assertEquals("longer", testee.bucket(SECONDS.toMillis(300)))
    }

    @Test
    fun whenInputIsLargeThenBucketIsLonger() {
        assertEquals("longer", testee.bucket(MINUTES.toMillis(10)))
    }
}
