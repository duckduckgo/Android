/*
 * Copyright (c) 2024 DuckDuckGo
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteMessagingCohortStoreImplTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RemoteMessagingDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cohortDao = db.remoteMessagingCohortDao()
    private val testee = RemoteMessagingCohortStoreImpl(db, coroutineRule.testDispatcherProvider)

    @Test
    fun whenPercentileIsSetForMessageThenReturnSameValue() = runTest {
        cohortDao.insert(RemoteMessagingCohort(messageId = "message1", percentile = 0.5f))
        assertEquals(0.5f, testee.getPercentile("message1"))
    }

    @Test
    fun whenPercentileIsNotSetForMessageThenItIsCalculated() = runTest {
        val percentile = testee.getPercentile("message1")
        assertTrue(percentile in 0.0f..1.0f)
    }

    @Test
    fun whenMoreThanOneMessageWithCohortThenReturnExpectedCohort() = runTest {
        cohortDao.insert(RemoteMessagingCohort(messageId = "message1", percentile = 0.5f))
        cohortDao.insert(RemoteMessagingCohort(messageId = "message2", percentile = 0.6f))
        assertEquals(0.5f, testee.getPercentile("message1"))
    }
}
