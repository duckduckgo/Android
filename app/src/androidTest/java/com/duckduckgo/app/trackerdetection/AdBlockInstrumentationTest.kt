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

import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class AdBlockInstrumentationTest {

    private val documentUrl = "http://example.com"

    @Test
    fun whenEasylistDataInitiallyLoadedThenEasylistTrackerIsBlocked() {
        val testee = AdBlockPlus(easylistData(), false)
        assertTrue(testee.matches("http://imasdk.googleapis.com/js/sdkloader/ima3.js", documentUrl))
    }

    @Test
    fun whenEasylistDataInitiallyLoadedThenNonTrackerNotBlocked() {
        val testee = AdBlockPlus(easylistData(), false)
        assertFalse(testee.matches("http://duckduckgo.com", documentUrl))
    }


    @Test
    fun whenEasylistDataReloadedThenEasylistTrackerIsBlocked() {
        var testee = AdBlockPlus(easylistData(), false)
        testee = AdBlockPlus(testee.getProcessedData(), true)
        assertTrue(testee.matches("http://imasdk.googleapis.com/js/sdkloader/ima3.js", documentUrl))
    }

    @Test
    fun whenEasylistDataReloadedThenNonTrackerNotBlocked() {
        var testee = AdBlockPlus(easylistData(), false)
        testee = AdBlockPlus(testee.getProcessedData(), true)
        assertFalse(testee.matches("http://duckduckgo.com", documentUrl))
    }

    @Test
    fun whenEasyprivacyDataInitiallyLoadedThenEasyprivacyTrackerIsBlocked() {
        val testee = AdBlockPlus(easyprivacyData(), false)
        assertTrue(testee.matches("http://cdn.tagcommander.com/1705/tc_catalog.css", documentUrl))
    }

    @Test
    fun whenEasyprivacyDataInitiallyLoadedThenNonTrackerNotBlocked() {
        val testee = AdBlockPlus(easyprivacyData(), false)
        assertFalse(testee.matches("http://duckduckgo.com", documentUrl))
    }

    @Test
    fun whenEasyprivacyDataReloadedThenEasyprivacyTrackerIsBlocked() {
        var testee = AdBlockPlus(easyprivacyData(), false)
        testee = AdBlockPlus(testee.getProcessedData(), true)
        assertTrue(testee.matches("http://cdn.tagcommander.com/1705/tc_catalog.css", documentUrl))
    }

    @Test
    fun whenEasyprivacyDataReloadedThenNonTrackerNotBlocked() {
        var testee = AdBlockPlus(easyprivacyData(), false)
        testee = AdBlockPlus(testee.getProcessedData(), true)
        assertFalse(testee.matches("http://duckduckgo.com", documentUrl))
    }

    private fun easylistData(): ByteArray {
        val appContext = InstrumentationRegistry.getTargetContext()
        return appContext.resources.openRawResource(R.raw.easylist_small).use { it.readBytes() }
    }


    private fun easyprivacyData(): ByteArray {
        val appContext = InstrumentationRegistry.getTargetContext()
        return appContext.resources.openRawResource(R.raw.easyprivacy_small).use { it.readBytes() }
    }
}
