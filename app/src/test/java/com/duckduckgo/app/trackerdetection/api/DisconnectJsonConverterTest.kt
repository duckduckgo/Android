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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.trackerdetection.model.DisconnectTracker
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisconnectJsonConverterTest {

    private val moshi = Moshi.Builder().add(DisconnectJsonAdapter()).build()
    private val jsonAdapter = moshi.adapter(DisconnectList::class.java)

    @Test
    fun whenDisconnectFormatIsValidThenDataIsConverted() {
        val json = json("json/disconnect.json")
        val trackers = jsonAdapter.fromJson(json).trackers
        assertEquals(10, trackers.count())
    }

    @Test
    fun whenDisconnectFormatIsValidThenBasicElementsAreConvertedCorrectly() {
        val json = json("json/disconnect.json")
        val trackers = jsonAdapter.fromJson(json).trackers
        assertTrue(trackers.contains(DisconnectTracker("acontenturl.com", "Content", "Content Co", "http://acontenturl.com/")))
    }

    @Test
    fun whenDisconnectFormatIsValidThenElementsContainingDntEntryAreConvertedCorrectly() {
        val json = json("json/disconnect.json")
        val trackers = jsonAdapter.fromJson(json).trackers
        assertTrue(trackers.contains(DisconnectTracker("itisatracker.com", "Advertising", "ItsATracker", "https://itisatracker.com/")))
    }

    @Test
    fun whenDisconnectFormatIsValidThenNetworksWithMultipleTrackersAreConvertedCorrectly() {
        val json = json("json/disconnect.json")
        val trackers = jsonAdapter.fromJson(json).trackers
        assertTrue(trackers.contains(DisconnectTracker("anothersocialurl.com", "Social", "SocialNet", "http://www.anothersocialurl.com/")))
        assertTrue(trackers.contains(DisconnectTracker("55anothersocialurl.com", "Social", "SocialNet", "http://www.anothersocialurl.com/")))
        assertTrue(trackers.contains(DisconnectTracker("99anothersocialurl.com", "Social", "SocialNet", "http://www.anothersocialurl.com/")))
    }

    @Test(expected = JsonDataException::class)
    fun whenDisconnectFormatIsMismatchedThenExceptionIsThrown() {
        val json = json("json/disconnect_mismatched.json")
        jsonAdapter.fromJson(json).trackers
    }

    private fun json(resourceName: String): String =
            javaClass.classLoader.getResource(resourceName).openStream().bufferedReader().use { it.readText() }

}