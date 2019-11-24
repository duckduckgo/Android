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
import com.duckduckgo.app.trackerdetection.model.Action.BLOCK
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.*
import org.junit.Test

class TdsTrackerJsonTest {

    private val actionConverter = ActionJsonAdapter()
    private val moshi = Moshi.Builder().add(actionConverter).build()
    private val type = Types.newParameterizedType(Map::class.java, String::class.java, TdsJsonTracker::class.java)
    private val jsonAdapter: JsonAdapter<Map<String, TdsJsonTracker>> = moshi.adapter(type)

    @Test
    fun whenFormatIsValidThenTrackersAreCreated() {
        val json = loadText("json/tds.json")
        val trackers = jsonAdapter.fromJson(json)!!.toTdsTrackers()
        assertEquals(3, trackers.count())
    }

    @Test
    fun whenFormatIsValidThenBasicElementsAreConvertedCorrectly() {
        val json = loadText("json/tds.json")
        val trackers = jsonAdapter.fromJson(json)!!.toTdsTrackers()
        val tracker = trackers["1dmp.io"]
        assertEquals(TdsTracker("1dmp.io", BLOCK, "CleverDATA LLC", arrayListOf()), tracker)
    }

    @Test
    fun whenTrackerHasInvalidDefaultActionThenTrackerNotCreated() {
        val json = loadText("json/tds_action_invalid.json")
        val jsonTrackers = jsonAdapter.fromJson(json)!!
        val trackers = jsonTrackers.toTdsTrackers()
        assertEquals(2, trackers.count())
        assertFalse(trackers.containsKey("1dmp.io"))
    }

    @Test
    fun whenTrackerIsMissingDefaultActionThenTrackerNotCreated() {
        val json = loadText("json/tds_action_missing.json")
        val trackers = jsonAdapter.fromJson(json)!!.toTdsTrackers()
        assertEquals(2, trackers.count())
        assertFalse(trackers.containsKey("1dmp.io"))
    }
}