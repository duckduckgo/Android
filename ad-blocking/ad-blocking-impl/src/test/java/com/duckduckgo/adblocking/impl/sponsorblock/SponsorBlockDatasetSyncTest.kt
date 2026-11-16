/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.sponsorblock

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SponsorBlockDatasetSyncTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SponsorBlockDatasetDatabase::class.java).build()
    private val dao = database.dao()
    private val service = FakeService()
    private val sync = SponsorBlockDatasetSync(service, dao)

    @After fun close() = database.close()

    @Test fun fullDiffAndFallbackAreApplied() = runTest {
        assertTrue(dao.hasSegments(REMOVED_PREFIX))
        service.latest = 1
        service.responses[0] = SponsorBlockDeltaResponse(setOf(REMOVED_PREFIX, RETAINED_PREFIX), emptySet(), true, 1)
        sync.sync()
        assertTrue(dao.hasSegments(REMOVED_PREFIX))
        assertFalse(dao.hasSegments(ADDED_PREFIX))
        service.latest = 2
        service.responses[1] = SponsorBlockDeltaResponse(setOf(ADDED_PREFIX), setOf(REMOVED_PREFIX), false, 2)
        sync.sync()
        assertFalse(dao.hasSegments(REMOVED_PREFIX))
        assertTrue(dao.hasSegments(ADDED_PREFIX))
        assertEquals(2, dao.revision())
        service.latest = 4
        service.responses[2] = SponsorBlockDeltaResponse(setOf(INTERMEDIATE_PREFIX), emptySet(), false, 3)
        service.responses[3] = SponsorBlockDeltaResponse(setOf(REPLACEMENT_PREFIX), emptySet(), true, 4)
        sync.sync()
        assertEquals(listOf(0, 1, 2, 3), service.requests)
        assertFalse(dao.hasSegments(ADDED_PREFIX))
        assertTrue(dao.hasSegments(REPLACEMENT_PREFIX))
        assertEquals(4, dao.revision())
        sync.sync()
        assertEquals(4, service.requests.size)
    }

    @Test fun invalidDeltaDoesNotChangeStoredData() = runTest {
        service.latest = 1
        service.responses[0] = SponsorBlockDeltaResponse(setOf("invalid"), emptySet(), true, 1)
        try { sync.sync() } catch (_: IllegalArgumentException) { }
        assertEquals(null, dao.revision())
        assertTrue(dao.hasSegments(REMOVED_PREFIX))
    }

    private companion object {
        const val REMOVED_PREFIX = "0649a8bb"
        const val RETAINED_PREFIX = "0a130329"
        const val ADDED_PREFIX = "cb1ad2a7"
        const val INTERMEDIATE_PREFIX = "2fee55bc"
        const val REPLACEMENT_PREFIX = "0a13c480"
    }

    private class FakeService : SponsorBlockDatasetService {
        var latest = 0
        val responses = mutableMapOf<Int, SponsorBlockDeltaResponse>()
        val requests = mutableListOf<Int>()
        override suspend fun revision() = SponsorBlockRevisionResponse(latest)
        override suspend fun hashPrefixes(revision: Int): SponsorBlockDeltaResponse {
            requests += revision
            return responses.getValue(revision)
        }
    }
}
