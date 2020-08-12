/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.net.Uri
import com.duckduckgo.app.browser.DosDetector.Companion.MAX_REQUESTS_COUNT
import com.duckduckgo.app.browser.DosDetector.Companion.DOS_TIME_WINDOW_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosDetectorTest {

    val testee: DosDetector = DosDetector()

    @Test
    fun whenLessThanMaxRequestsCountCallsWithSameUrlThenReturnFalse() {
        for (i in 0 until MAX_REQUESTS_COUNT) {
            assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example.com")))
        }
    }

    @Test
    fun whenMoreThanMaxRequestsCountCallsWithSameUrlThenLastCallReturnsTrue() {
        for (i in 0..MAX_REQUESTS_COUNT) {
            assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example.com")))
        }
        assertTrue(testee.isUrlGeneratingDos(Uri.parse("http://example.com")))
    }

    @Test
    fun whenMoreThanMaxRequestsCountCallsWithSameUrlAndDelayGreaterThanLimitThenReturnFalse() {
        runBlocking {
            for (i in 0..MAX_REQUESTS_COUNT) {
                assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example.com")))
            }
            delay((DOS_TIME_WINDOW_MS + 100).toLong())
            assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example.com")))
        }
    }

    @Test
    fun whenMoreThanMaxRequestsCountCallsWithSameUrlAndDelayGreaterThanLimitThenCountIsResetSoNextAndSubsequentRequestsReturnFalse() {
        runBlocking {
            for (i in 0..MAX_REQUESTS_COUNT) {
                assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example.com")))
            }
            delay((DOS_TIME_WINDOW_MS + 100).toLong())
            assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example.com")))
            assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example.com")))
        }
    }

    @Test
    fun whenMultipleRequestsFromDifferentUrlsThenReturnFalse() {
        for (i in 0 until MAX_REQUESTS_COUNT * 2) {
            if (i % 2 == 0) {
                assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example.com")))
            } else {
                assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example2.com")))
            }
        }
    }

    @Test
    fun whenMaxRequestsReceivedConsecutivelyFromDifferentUrlsThenReturnFalse() {
        for (i in 0 until MAX_REQUESTS_COUNT) {
            assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example.com")))
        }
        for (i in 0 until MAX_REQUESTS_COUNT) {
            assertFalse(testee.isUrlGeneratingDos(Uri.parse("http://example2.com")))
        }
    }
}
