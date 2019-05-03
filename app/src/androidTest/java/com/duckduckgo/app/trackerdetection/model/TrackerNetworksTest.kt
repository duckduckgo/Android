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

package com.duckduckgo.app.trackerdetection.model

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.entities.EntityMapping
import com.duckduckgo.app.entities.db.EntityListDao
import com.duckduckgo.app.entities.db.EntityListEntity
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.trackerdetection.db.TrackerDataDao
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TrackerNetworksTest {

    companion object {
        private const val category = "Social"
        private const val otherCategory = "Advertising"
        private const val networkName = "NetworkName"
        private const val networkUrl = "http://www.network.com/"
        private const val majorNetworkName = "google"
        private const val majorNetworkUrl = "google.com"
    }

    private var mockPrevalenceStore: PrevalenceStore = mock()
    private lateinit var trackerDataDao: TrackerDataDao
    private lateinit var entityListDao: EntityListDao

    private lateinit var entityMapping: EntityMapping
    private lateinit var db: AppDatabase


    private val testee: TrackerNetworks by lazy {
        TrackerNetworksLookup(mockPrevalenceStore, entityMapping, trackerDataDao)
    }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        entityListDao = db.networkEntityDao()
        trackerDataDao = db.trackerDataDao()

        entityMapping = EntityMapping(entityListDao)
    }

    @Test
    fun whenUrlMatchesTrackerUrlThenCategoryForTrackerUrlIsReturned() {

        val trackers = listOf(
            DisconnectTracker("tracker.com", category, networkName, networkUrl),
            DisconnectTracker("network.com", otherCategory, networkName, networkUrl)
        )
        trackerDataDao.insertAll(trackers)

        val entities = listOf(EntityListEntity("tracker.com", networkName), EntityListEntity("network.com", networkName))
        entityListDao.insertAll(entities)

        assertEquals(category, testee.network("http://tracker.com/script.js")?.category)
        assertEquals(otherCategory, testee.network("http://network.com/script.js")?.category)

    }

    @Test
    fun whenUrlMatchesTrackerUrlThenNetworkIsReturned() {
        val trackers = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        trackerDataDao.insertAll(trackers)
        val entities = listOf(EntityListEntity("tracker.com", networkName), EntityListEntity("network.com", networkName))
        entityListDao.insertAll(entities)
        val expected = TrackerNetwork(networkName, category)
        assertEquals(expected, testee.network("http://tracker.com/script.js"))
    }

    @Test
    fun whenUrlMatchesNetworkUrlThenNetworkIsReturned() {
        val trackers = listOf(
            DisconnectTracker("tracker.com", category, networkName, networkUrl),
            DisconnectTracker("network.com", category, networkName, networkUrl)
        )
        trackerDataDao.insertAll(trackers)
        val entities = listOf(EntityListEntity("tracker.com", networkName), EntityListEntity("network.com", networkName))
        entityListDao.insertAll(entities)
        val expected = TrackerNetwork(networkName, category)
        assertEquals(expected, testee.network("http://www.network.com/index.html"))
    }

    @Test
    fun whenUrlDoesNotMatchEitherTrackerOrNetworkUrlThenNullIsReturned() {
        val trackers = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        trackerDataDao.insertAll(trackers)
        assertNull(testee.network("http://example.com/index.html"))
    }

    @Test
    fun whenUrlSubdomainMatchesTrackerUrlThenNetworkIsReturned() {
        val trackers = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        trackerDataDao.insertAll(trackers)
        val entities = listOf(EntityListEntity("tracker.com", networkName), EntityListEntity("network.com", networkName))
        entityListDao.insertAll(entities)
        val expected = TrackerNetwork(networkName, category)
        assertEquals(expected, testee.network("http://subdomain.tracker.com/script.js"))
    }

    @Test
    fun whenUrlSubdomainMatchesNetworkUrlThenNetworkIsReturned() {
        val trackers = listOf(
            DisconnectTracker("tracker.com", category, networkName, networkUrl),
            DisconnectTracker("network.com", category, networkName, networkUrl)
        )
        trackerDataDao.insertAll(trackers)
        val entities = listOf(EntityListEntity("tracker.com", networkName), EntityListEntity("network.com", networkName))
        entityListDao.insertAll(entities)
        val expected = TrackerNetwork(networkName, category)
        assertEquals(expected, testee.network("http://www.subdomain.network.com/index.html"))
    }

    @Test
    fun whenUrlContainsButIsNotSubdomainOfTrackerUrlThenNullIsReturned() {
        val trackers = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        trackerDataDao.insertAll(trackers)
        assertNull(testee.network("http://notsubdomainoftracker.com/script.js"))
    }

    @Test
    fun whenUrlContainsButIsNotSubdomainOfNetworkUrlThenNullIsReturned() {
        val trackers = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        trackerDataDao.insertAll(trackers)
        assertNull(testee.network("http://notsubdomainofnetwork.com/index.html"))
    }

    @Test
    fun whenUrlMatchesTrackerInMajorNetworkThenMajorNetworkIsReturned() {
        whenever(mockPrevalenceStore.findPrevalenceOf(majorNetworkName)).thenReturn(100.0)

        val trackers = listOf(DisconnectTracker("tracker.com", category, majorNetworkName, majorNetworkUrl))
        trackerDataDao.insertAll(trackers)

        val entities = listOf(
            EntityListEntity("tracker.com", majorNetworkName),
            EntityListEntity("network.com", majorNetworkName)
        )
        entityListDao.insertAll(entities)

        val expected = TrackerNetwork(majorNetworkName, category, true)
        assertEquals(expected, testee.network("http://tracker.com/script.js"))
    }

}