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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerDetectorTest {

    var testee: TrackerDetector = TrackerDetector()

    @Test
    fun whenUrlIsInEasyListThenShouldBlockIsTrue() {
        val url = "http://imasdk.googleapis.com/js/sdkloader/ima3.js"
        assertTrue(testee.shouldBlock(url))
    }

    @Test
    fun whenUrlIsInEasyPrivacyListThenShouldBlockIsTrue() {

        val url = "http://imasdk.googleapis.com/js/sdkloader/ima3.js"
        assertTrue(testee.shouldBlock(url))
    }

    @Test
    fun whenUrlIsInDisconnectListThenShouldBlockIsTrue() {
        val url = "https://criteo.com/abcd.css"
        assertTrue(testee.shouldBlock(url))
    }

    @Test
    fun whenUrlIsNotInAnyTrackerListsThenShouldBlockIsFalse() {
        var url = "https://duckduckgo.com"
        assertFalse(testee.shouldBlock(url))
    }

}