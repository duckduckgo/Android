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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.FileUtilities.loadText
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import com.duckduckgo.app.trackerdetection.model.TdsTracker.Action
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdsTrackerJsonAdapterTest {

    private val moshi = Moshi.Builder().add(TdsJsonAdapter()).build()
    private val type = Types.newParameterizedType(List::class.java, TdsTracker::class.java)
    private val jsonAdapter: JsonAdapter<List<TdsTracker>> = moshi.adapter(type)

    @Test
    fun whenFormatIsValidThenTrackersAreCreated() {
        val json = loadText("json/tds.json")
        val trackers = jsonAdapter.fromJson(json)
        assertEquals(3, trackers.count())
    }

    @Test
    fun whenFormatIsValidThenBasicElementsAreConvertedCorrectly() {
        val json = loadText("json/tds.json")
        val trackers = jsonAdapter.fromJson(json)
        val tracker = trackers.find { it.domain == "1dmp.io" }
        assertEquals(TdsTracker("1dmp.io", Action.BLOCK, "CleverDATA"), tracker)
    }

    @Test
    fun whenTrackerHasInvalidDefaultActionThenTrackerNotCreated() {
        val json = loadText("json/tds_action_invalid.json")
        val trackers = jsonAdapter.fromJson(json)
        assertEquals(2, trackers.count())
        assertNull(trackers.find { it.domain == "1dmp.io" })
    }

    @Test
    fun whenTrackerIsMissingDefaultActionThenTrackerNotCreated() {
        val json = loadText("json/tds_action_missing.json")
        val trackers = jsonAdapter.fromJson(json)
        assertEquals(2, trackers.count())
        assertNull(trackers.find { it.domain == "1dmp.io" })
    }
}