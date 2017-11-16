/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection;

import com.duckduckgo.app.trackerdetection.TrackerDetectionClient.ClientName.EASYLIST
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class AdBlockInstrumentationTest {

    companion object {
        private val documentUrl = "http://example.com"
        private val trackerUrl = "http://imasdk.googleapis.com/js/sdkloader/ima3.js"
        private val nonTrackerUrl = "http://duckduckgo.com/index.html"
    }

    @Test
    fun whenBasicDataLoadedThenTrackerIsBlocked() {
        val testee = AdBlockPlus(EASYLIST)
        testee.loadBasicData(data())
        assertTrue(testee.matches(trackerUrl, documentUrl))
    }

    @Test
    fun whenBasicDataLoadedThenNonTrackerIsNotBlocked() {
        val testee = AdBlockPlus(EASYLIST)
        testee.loadBasicData(data())
        assertFalse(testee.matches(nonTrackerUrl, documentUrl))
    }

    @Test
    fun whenProcessedDataLoadedThenTrackerIsBlocked() {
        val original = AdBlockPlus(EASYLIST)
        original.loadBasicData(data())
        val testee = AdBlockPlus(EASYLIST)
        testee.loadProcessedData(original.getProcessedData())
        assertTrue(testee.matches(trackerUrl, documentUrl))
    }

    @Test
    fun whenProcessedDataLoadedThenNonTrackerIsNotBlocked() {
        val original = AdBlockPlus(EASYLIST)
        original.loadBasicData(data())
        val testee = AdBlockPlus(EASYLIST)
        testee.loadProcessedData(original.getProcessedData())
        assertFalse(testee.matches(nonTrackerUrl, documentUrl))
    }

    private fun data(): ByteArray {
        return javaClass.classLoader.getResource("easylist_sample").readBytes()
    }
}
