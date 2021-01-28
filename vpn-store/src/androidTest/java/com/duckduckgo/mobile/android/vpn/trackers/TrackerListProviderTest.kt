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

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TrackerListProviderTest {
    private lateinit var trackerListProvider: TrackerListProvider

    @Before
    fun setup() {
        trackerListProvider = TrackerListProvider()
    }

    @Test
    fun whenIncludeFacebookDomainsIsTrueThenReturnAllTrackers() {
        trackerListProvider.includeFacebookDomains = true

        val totalTrackers = TrackerListProvider.TRACKER_HOST_NAMES.size

        val trackers = trackerListProvider.trackerList()

        assertEquals(totalTrackers, trackers.size)
    }

    @Test
    fun whenIncludeFacebookDomainsIsFalseThenReturnNonFacebookTrackers() {
        trackerListProvider.includeFacebookDomains = false

        val facebookCompanyId = TrackerListProvider.TRACKER_GROUP_COMPANIES.firstOrNull { it.company.equals("facebook", true) }?.trackerCompanyId ?: -1
        val totalTrackers = TrackerListProvider.TRACKER_HOST_NAMES.size
        val facebookTrackers = TrackerListProvider.TRACKER_HOST_NAMES.filter { it.trackerCompanyId == facebookCompanyId }.size
        val nonFacebookTrackers = totalTrackers - facebookTrackers

        val trackers = trackerListProvider.trackerList()

        assertEquals(nonFacebookTrackers, trackers.size)
    }
}