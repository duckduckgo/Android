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

package com.duckduckgo.app.privacy.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.privacy.model.PrivacyProtectionCountsEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PrivacyProtectionCountDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PrivacyProtectionCountDao

    @Before
    fun before() {
        db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.privacyProtectionCountsDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenDbInitialisedThenCountsDefaultTo0() {
        assertEquals(0, dao.getTrackersBlockedCount())
        assertEquals(0, dao.getUpgradeCount())
    }

    @Test
    fun whenTrackerCountIncrementedFrom0ThenNewCountIs1() {
        dao.incrementBlockedTrackerCount()
        assertEquals(1, dao.getTrackersBlockedCount())
    }

    @Test
    fun whenTrackerCountIncrementedFrom1ThenNewCountIs2() {
        dao.incrementBlockedTrackerCount()
        dao.incrementBlockedTrackerCount()
        assertEquals(2, dao.getTrackersBlockedCount())
    }

    @Test
    fun whenUpgradeCountIncrementedFrom0ThenNewCountIs1() {
        dao.incrementUpgradeCount()
        assertEquals(1, dao.getUpgradeCount())
    }

    @Test
    fun whenUpgradeCountIncrementedFrom1ThenNewCountIs2() {
        dao.incrementUpgradeCount()
        dao.incrementUpgradeCount()
        assertEquals(2, dao.getUpgradeCount())
    }

    @Test
    fun whenCountsInitialisedToArbitraryValueThenThoseReturnedInCounts() {
        dao.initialiseCounts(
            PrivacyProtectionCountsEntity(blockedTrackerCount = 5, upgradeCount = 3))
        assertEquals(5, dao.getTrackersBlockedCount())
        assertEquals(3, dao.getUpgradeCount())
    }
}
