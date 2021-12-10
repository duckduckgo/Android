/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.trackers

import com.duckduckgo.app.FileUtilities.loadText
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert
import org.junit.Test

class AppTrackerJsonParserTest {

    private val moshi = Moshi.Builder().build()
    private val jsonAdapter: JsonAdapter<JsonAppBlockingList> = moshi.adapter(JsonAppBlockingList::class.java)

    @Test
    fun whenJsonIsValidThenBlocklistIsParsed() {
        val json = loadText("full_app_trackers_blocklist_v2.json")
        val blocklist = AppTrackerJsonParser.parseAppTrackerJson(moshi, json)

        Assert.assertEquals("1638913509906", blocklist.version)
        Assert.assertEquals(255, blocklist.trackers.count())
        Assert.assertEquals(376, blocklist.packages.count())
        Assert.assertEquals(127, blocklist.entities.count())
    }

}
