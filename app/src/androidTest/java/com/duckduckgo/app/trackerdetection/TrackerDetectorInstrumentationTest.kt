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


import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.duckduckgo.app.main.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class TrackerDetectorInstrumentationTest {

    private val documentUrl = "http://example.com"
    private lateinit var testee: TrackerDetector

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getTargetContext()
        val easylistData = appContext.resources.openRawResource(R.raw.easylist).use { it.readBytes() }
        val easyprivacyData = appContext.resources.openRawResource(R.raw.easyprivacy).use { it.readBytes() }
        testee = TrackerDetector(easylistData, easyprivacyData)
    }

    @Test
    fun whenUrlIsInEasyListThenShouldBlockIsTrue() {
        val url = "http://imasdk.googleapis.com/js/sdkloader/ima3.js"
        assertTrue(testee.shouldBlock(url, documentUrl))
    }

    @Test
    fun whenUrlIsInEasyPrivacyListThenShouldBlockIsTrue() {
        val url = "http://cdn.tagcommander.com/1705/tc_catalog.js"
        assertTrue(testee.shouldBlock(url, documentUrl))
    }

    @Test
    fun whenUrlIsInDisconnectListThenShouldBlockIsTrue() {
        val url = "https://criteo.com/abcd.css"
        assertTrue(testee.shouldBlock(url, documentUrl))
    }

    @Test
    fun whenUrlIsNotInAnyTrackerListsThenShouldBlockIsFalse() {
        var url = "https://duckduckgo.com/index.html"
        assertFalse(testee.shouldBlock(url, documentUrl))
    }

}