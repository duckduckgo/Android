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

package com.duckduckgo.app.trackerdetection

import com.duckduckgo.app.trackerdetection.Client.ClientName.EASYLIST
import com.duckduckgo.app.trackerdetection.model.ResourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class AdBlockClientTest {

    companion object {
        private const val documentUrl = "http://example.com"
        private const val trackerUrl = "http://imasdk.googleapis.com/js/sdkloader/ima3.js"
        private const val nonTrackerUrl = "http://duckduckgo.com/index.html"
        private val resourceType = ResourceType.UNKNOWN
    }

    @Test
    fun whenBasicDataLoadedThenTrackerIsBlocked() {
        val testee = AdBlockClient(EASYLIST)
        testee.loadBasicData(data())
        assertTrue(testee.matches(trackerUrl, documentUrl, resourceType))
    }

    @Test
    fun whenBasicDataLoadedThenNonTrackerIsNotBlocked() {
        val testee = AdBlockClient(EASYLIST)
        testee.loadBasicData(data())
        assertFalse(testee.matches(nonTrackerUrl, documentUrl, resourceType))
    }

    @Test
    fun whenProcessedDataLoadedThenTrackerIsBlocked() {
        val original = AdBlockClient(EASYLIST)
        original.loadBasicData(data())
        val processedData = original.getProcessedData()
        val testee = AdBlockClient(EASYLIST)
        testee.loadProcessedData(processedData)
        assertTrue(testee.matches(trackerUrl, documentUrl, resourceType))
    }

    @Test
    fun whenProcessedDataLoadedThenNonTrackerIsNotBlocked() {
        val original = AdBlockClient(EASYLIST)
        original.loadBasicData(data())
        val processedData = original.getProcessedData()
        val testee = AdBlockClient(EASYLIST)
        testee.loadProcessedData(processedData)
        assertFalse(testee.matches(nonTrackerUrl, documentUrl, resourceType))
    }

    private fun data(): ByteArray =
        javaClass.classLoader!!.getResource("binary/easylist_sample").readBytes()
}
