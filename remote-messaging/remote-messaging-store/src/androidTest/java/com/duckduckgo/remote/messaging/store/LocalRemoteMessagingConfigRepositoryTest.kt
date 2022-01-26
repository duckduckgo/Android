/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.remote.messaging.store

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jakewharton.threetenabp.AndroidThreeTen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.threeten.bp.LocalDateTime

class LocalRemoteMessagingConfigRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RemoteMessagingDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val remoteConfigDao = db.remoteMessagingConfigDao()

    private val testee = LocalRemoteMessagingConfigRepository(db)

    @Before
    fun setup() {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun whenRemoteConfigTimestampGreaterThan1DayThenReturnExpired() {
        remoteConfigDao.insert(
            RemoteMessagingConfig(
                version = 0,
                evaluationTimestamp = databaseTimestampFormatter().format(LocalDateTime.now().minusDays(2L))
            )
        )

        val expired = testee.expired()

        assertTrue(expired)
    }

    @Test
    fun whenRemoteConfigTimestampLessThan1DayThenReturnExpired() {
        remoteConfigDao.insert(
            RemoteMessagingConfig(
                version = 0,
                evaluationTimestamp = databaseTimestampFormatter().format(LocalDateTime.now().minusHours(15L))
            )
        )

        val expired = testee.expired()

        assertFalse(expired)
    }
}
