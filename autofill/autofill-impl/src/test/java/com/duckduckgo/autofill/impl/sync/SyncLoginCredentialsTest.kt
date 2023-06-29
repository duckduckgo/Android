/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.autofill.store.AutofillDatabase
import com.duckduckgo.autofill.store.sync.LoginCredentialsSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
internal class SyncLoginCredentialsTest {

    @get:Rule @Suppress("unused") val coroutineRule = CoroutineTestRule()

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AutofillDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.syncLoginCredentialsDao()

    private val testee = SyncLoginCredentials(dao)

    @Test fun whenLoginIdNotFoundThenReturnNewSyncId() {
        val syncId = testee.getSyncId(123L)

        assertNotNull(syncId)
    }

    @Test fun whenLoginIdExistsThenReturnSyncId() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(LoginCredentialsSync(syncId = syncId, id = loginId))

        val result = testee.getSyncId(loginId)

        assertEquals(syncId, result)
    }

    @Test fun whenEntityRemovedThenDeleteAtUpdated() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(LoginCredentialsSync(syncId = syncId, id = loginId))

        testee.onEntityRemoved(loginId)

        val result = dao.getSyncId(loginId)!!
        assertNotNull(result.deleted_at)
    }
}
