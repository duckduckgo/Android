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


import android.support.test.runner.AndroidJUnit4
import com.duckduckgo.app.privacymonitor.store.PrivacySettingsStore
import com.duckduckgo.app.trackerdetection.Client.ClientName.*
import com.duckduckgo.app.trackerdetection.model.ResourceType
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class TrackerDetectorListTest {

    companion object {
        private val documentUrl = "http://example.com"
        private val resourceType = ResourceType.UNKNOWN
    }

    private lateinit var blockingOnlyTestee: TrackerDetector
    private lateinit var testeeWithWhitelist: TrackerDetector
    private lateinit var settingStore: PrivacySettingsStore


    @Before
    fun before() {
        val easylistAdblock = adblockClient(EASYLIST, "binary/easylist_sample")
        val easyprivacyAdblock = adblockClient(EASYPRIVACY, "binary/easyprivacy_sample")

        // re-using blocking sample as a whitelist to hammer the point home
        val trackersWhitelistAdblocks = adblockClient(TRACKERSWHITELIST, "binary/easylist_sample")

        settingStore = mock()
        whenever(settingStore.privacyOn).thenReturn(true)

        blockingOnlyTestee = TrackerDetectorImpl(TrackerNetworks(), settingStore)
        blockingOnlyTestee.addClient(easyprivacyAdblock)
        blockingOnlyTestee.addClient(easylistAdblock)

        testeeWithWhitelist = TrackerDetectorImpl(TrackerNetworks(), settingStore)
        testeeWithWhitelist.addClient(trackersWhitelistAdblocks)
        testeeWithWhitelist.addClient(easyprivacyAdblock)
        testeeWithWhitelist.addClient(easylistAdblock)
    }

    @Test
    fun whenUrlIsInWhitelistThenEvaluateReturnsNull() {
        val url = "http://imasdk.googleapis.com/js/sdkloader/ima3.js"
        assertNull(testeeWithWhitelist.evaluate(url, documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsInEasyListThenEvaluateReturnsTrackingEvent() {
        val url = "http://imasdk.googleapis.com/js/sdkloader/ima3.js"
        val expected = TrackingEvent(documentUrl, url, null, true)
        assertEquals(expected, blockingOnlyTestee.evaluate(url, documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsInEasyPrivacyListThenEvaluateReturnsTrackingEvent() {
        val url = "http://cdn.tagcommander.com/1705/tc_catalog.css"
        val expected = TrackingEvent(documentUrl, url, null, true)
        assertEquals(expected, blockingOnlyTestee.evaluate(url, documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsNotInAnyTrackerListsThenEvaluateReturnsNull() {
        val url = "https://duckduckgo.com/index.html"
        assertNull(blockingOnlyTestee.evaluate(url, documentUrl, resourceType))
    }

    private fun adblockClient(name: Client.ClientName, dataFile: String): Client {
        val data = javaClass.classLoader.getResource(dataFile).readBytes()
        val initialAdBlock = AdBlockClient(name)
        initialAdBlock.loadBasicData(data)
        val adblockWithProcessedData = AdBlockClient(name)
        adblockWithProcessedData.loadProcessedData(initialAdBlock.getProcessedData())
        return adblockWithProcessedData
    }

}