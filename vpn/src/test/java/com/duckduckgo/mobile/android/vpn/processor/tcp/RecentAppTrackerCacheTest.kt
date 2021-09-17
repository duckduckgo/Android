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

package com.duckduckgo.mobile.android.vpn.processor.tcp

import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.*

class RecentAppTrackerCacheTest {

    private val socketWriter: TcpSocketWriter = mock()
    private val connectionCloser = TCBCloser(socketWriter)
    private val testee = RecentAppTrackerCache(connectionCloser)

    @Test
    fun whenAddingSingleEntryThenTotalCacheSizeIsOne() {
        testee.addTrackerForApp(anAppPackage, aTracker, aTcbId)
        val cachedTrackers = testee.recentTrackingAttempts
        assertEquals(1, cachedTrackers.size)
    }

    @Test
    fun whenAddingTwoEntriesUnderSamePackageThenTotalCacheSizeIsTwo() {
        testee.addTrackerForApp(anAppPackage, aTracker, aTcbId)
        testee.addTrackerForApp(anAppPackage, aTracker, aTcbId)
        val cachedTrackers = testee.recentTrackingAttempts
        assertEquals(1, cachedTrackers.size)
    }

    @Test
    fun whenAddingTwoEntriesUnderDifferentPackagesThenTotalCacheSizeIsTwo() {
        testee.addTrackerForApp("package1", aTracker, aTcbId)
        testee.addTrackerForApp("package2", aTracker, aTcbId)
        val cachedTrackers = testee.recentTrackingAttempts
        assertEquals(2, cachedTrackers.size)
    }

    @Test
    fun whenAddingTrackingEventThenStoredCorrectlyUnderPackageName() {
        testee.addTrackerForApp("package", aTracker, aTcbId)
        val trackersForApp = testee.recentTrackingAttempts["package"]
        assertEquals(1, trackersForApp!!.size)
    }

    @Test
    fun whenAddingTrackingEventForAppThenStoredCorrectlyUnderTrackerDomainName() {
        testee.addTrackerForApp("package", "tracker", aTcbId)
        val trackersForApp = testee.recentTrackingAttempts["package"]
        assertEquals(1, trackersForApp!!["tracker"]!!.size)
    }

    @Test
    fun whenClosingConnectionsThenNewConnectionsArePreserved() {
        addRecentTrackingAttempt()
        testee.cleanupStaleConnections()
        assertEquals(1, testee.recentTrackingAttempts.size)
    }

    @Test
    fun whenClosingConnectionsThenOldConnectionsAreRemoved() {
        addOldTrackingAttempt()
        testee.cleanupStaleConnections()
        testee.recentTrackingAttempts.assertEmpty()
    }

    @Test
    fun whenMultipleTrackersForSameAppThenOnlyOldConnectionsAreRemoved() {
        addOldTrackingAttempt()
        addRecentTrackingAttempt()
        testee.cleanupStaleConnections()
        assertEquals(1, testee.recentTrackingAttempts.size)
    }

    @Test
    fun whenNoTrackingEventsThenNullReturned() {
        assertNull(testee.getRecentTrackingAttempt(anAppPackage, aTracker))
    }

    @Test
    fun whenTrackingEventsForAppUnderADifferentTrackerThenNullReturned() {
        testee.addTrackerForApp(anAppPackage, "tracker1", aTcbId)
        assertNull(testee.getRecentTrackingAttempt(anAppPackage, "tracker2"))
    }

    @Test
    fun whenTrackingEventsForAppAndTrackerDomainThenTrackerReturned() {
        testee.addTrackerForApp(anAppPackage, aTracker, aTcbId)
        val trackerAttempt = testee.getRecentTrackingAttempt(anAppPackage, aTracker)!!
        assertEquals(anAppPackage, trackerAttempt.appPackage)
        assertEquals(aTracker, trackerAttempt.trackerDomain)
    }

    @Test
    fun whenMultipleTrackingEventsForAppAndTrackerDomainThenMostRecentTrackerReturned() {
        testee.addTrackerForApp(anAppPackage, aTracker, aTcbId, timestamp = 100)
        testee.addTrackerForApp(anAppPackage, aTracker, aTcbId, timestamp = 200)
        testee.addTrackerForApp(anAppPackage, aTracker, aTcbId, timestamp = 300)

        // different tracker - should not be returned
        testee.addTrackerForApp(anAppPackage, "tracker2", aTcbId, timestamp = 400)

        val trackerAttempt = testee.getRecentTrackingAttempt(anAppPackage, aTracker)!!
        assertEquals(300, trackerAttempt.timestamp)
    }

    private fun addRecentTrackingAttempt() {
        val timestampNow = System.currentTimeMillis()
        testee.addTrackerForApp(anAppPackage, aTracker, aTcbId, timestampNow)
    }

    private fun addOldTrackingAttempt() {
        val oldTimestamp = Calendar.getInstance().also { it.add(Calendar.HOUR, -1) }.timeInMillis
        testee.addTrackerForApp(anAppPackage, aTracker, aTcbId, oldTimestamp)
    }

    companion object {
        private const val aTracker = "tracker"
        private const val anAppPackage = "com.app"
        private const val aTcbId = "123"
    }
}

private fun <K, V> MutableMap<K, V>.assertEmpty() {
    assertEquals("Expected map to be empty", 0, size)
}
