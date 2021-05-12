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

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.moshi.Moshi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppTrackerRepositoryTest {

    private lateinit var appTrackerRepository : RealAppTrackerRepository
    private lateinit var vpnDatabase: VpnDatabase

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        vpnDatabase = Room.inMemoryDatabaseBuilder(
            context,
            VpnDatabase::class.java
        ).allowMainThreadQueries().build().apply {
            VpnDatabase.prepopulateAppTrackerBlockingList(context, this)
        }

        appTrackerRepository = RealAppTrackerRepository(context, Moshi.Builder().build(), vpnDatabase.vpnAppTrackerBlockingDao())
    }

    @Test
    fun whenMatchTrackerInFullListAndHostnameIsTrackerThenReturnTracker() {
        assertTrackerTypeFound(appTrackerRepository.matchTrackerInFullList("g.doubleclick.net", ""))
    }

    @Test
    fun whenMatchTrackerInLegacyListAndHostnameIsTrackerThenReturnTracker() {
        assertTrackerTypeFound(appTrackerRepository.matchTrackerInLegacyList("g.doubleclick.net"))
    }

    @Test
    fun whenMatchTrackerInFullListAndSubdomainIsTrackerThenReturnTracker() {
        assertTrackerTypeFound(appTrackerRepository.matchTrackerInFullList("foo.g.doubleclick.net", ""))
    }

    @Test
    fun whenMatchTrackerInLegacyListAndSubdomainIsTrackerThenReturnTracker() {
        assertTrackerTypeFound(appTrackerRepository.matchTrackerInLegacyList("foo.g.doubleclick.net"))
    }

    @Test
    fun whenMatchTrackerInFullListAndHostnameIsNotTrackerThenReturnNull() {
        assertNotTrackerType(appTrackerRepository.matchTrackerInFullList("not.tracker.net", ""))
    }

    @Test
    fun whenMatchTrackerInLegacyListAndHostnameIsNotTrackerThenReturnNull() {
        assertNotTrackerType(appTrackerRepository.matchTrackerInLegacyList("not.tracker.net"))
    }

    private fun assertTrackerTypeFound(tracker: AppTrackerType) {
        assertFalse(tracker is AppTrackerType.NotTracker)
    }

    private fun assertNotTrackerType(tracker: AppTrackerType) {
        assertTrue(tracker is AppTrackerType.NotTracker)
    }
}