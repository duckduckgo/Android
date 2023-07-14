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

package com.duckduckgo.autofill.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.autofill.store.AutofillDatabase
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
internal class CredentialsSyncMetadataTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AutofillDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.credentialsSyncDao()
    private val testee = CredentialsSyncMetadata(dao)

    @Test
    fun whenAddNewEntityThenEntityInserted() {
        assertNull(dao.getLocalId("syncId"))

        testee.addOrUpdate(CredentialsSyncMetadataEntity("syncId", 123L))

        assertEquals(123L, dao.getLocalId("syncId"))
    }

    @Test
    fun whenUpdateSyncIdExistingEntityThenEntityUpdated() {
        dao.insert(CredentialsSyncMetadataEntity("syncId", 123L))

        testee.addOrUpdate(CredentialsSyncMetadataEntity("syncId", 456L))

        assertEquals(456L, dao.getLocalId("syncId"))
    }

    @Test
    fun whenUpdateExistingEntityByLocalIdThenEntityUpdated() {
        dao.insert(CredentialsSyncMetadataEntity("syncId", 123L))

        testee.addOrUpdate(CredentialsSyncMetadataEntity("syncId2", 123L))

        assertEquals("syncId2", dao.getSyncId(123L)?.syncId)
    }

    @Test
    fun whenLoginIdNotFoundThenReturnNull() {
        val syncId = testee.getSyncId(123L)

        assertNull(syncId)
    }

    @Test
    fun whenLoginIdExistsThenReturnSyncId() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, id = loginId))

        val result = testee.getSyncId(loginId)

        assertEquals(syncId, result)
    }

    @Test
    fun whenCreateSyncIdForNonExistingIdThenReturnNewSyncId() {
        val syncId = testee.createSyncId(123L)

        assertNotNull(syncId)
    }

    @Test
    fun whenCreateSyncIdForExistingIdThenReturnExistingSyncId() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, id = loginId))

        val result = testee.createSyncId(loginId)

        assertEquals(syncId, result)
        assertEquals(syncId, dao.getSyncId(loginId)?.syncId)
    }

    @Test
    fun whenLocalIdNotFoundThenReturnNull() {
        val localId = testee.getLocalId("syncId")

        assertNull(localId)
    }

    @Test
    fun whenLocalIdExistsThenReturnLocalId() {
        val localId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, id = localId))

        val result = testee.getLocalId(syncId)

        assertEquals(localId, result)
    }

    @Test
    fun whenGetRemovedEntitiesThenReturnEntitiesWithDeletedAt() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, id = loginId, deleted_at = "2022-08-30T00:00:00Z"))

        val result = testee.getRemovedEntitiesSince("2022-08-30T00:00:00Z")

        assertEquals(1, result.size)
        assertEquals(loginId, result[0].id)
        assertNotNull(result[0].deleted_at)
    }

    @Test
    fun whenGetRemovedEntitiesThenDoNotReturnEntitiesPreviousToSince() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, id = loginId, deleted_at = "2021-08-30T00:00:00Z"))

        val result = testee.getRemovedEntitiesSince("2022-08-30T00:00:00Z")

        assertEquals(0, result.size)
    }

    @Test
    fun whenEntityRemovedThenUpdateDeletedAtIfExists() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, id = loginId))

        testee.onEntityRemoved(loginId)

        val result = dao.getSyncId(loginId)!!
        assertNotNull(result.deleted_at)
    }

    @Test
    fun whenRemoveDeletedEntitiesThenDeleteEntitiesBeforeDate() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, id = loginId, deleted_at = "2022-08-30T00:00:00Z"))

        testee.removeDeletedEntities("2022-08-30T00:00:00Z")

        assertNull(dao.getSyncId(loginId))
    }

    @Test
    fun whenRemoveDeletedEntitiesThenKeepEntitiesAfterDate() {
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, id = 123L, deleted_at = "2022-08-30T00:00:00Z"))
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, id = 345L, deleted_at = "2022-09-30T00:00:00Z"))

        testee.removeDeletedEntities("2022-08-30T00:00:00Z")

        assertNull(dao.getSyncId(123L))
        assertNotNull(dao.getSyncId(345L))
    }

    @Test
    fun whenRemoveEntityWithLocalIdThenRemoveEntity() {
        val loginId = 123L
        dao.insert(CredentialsSyncMetadataEntity(syncId = "syncId", id = loginId))

        testee.removeEntityWithLocalId(loginId)

        assertNull(dao.getSyncId(loginId))
    }

    @Test
    fun whenRemoveEntityWithSyncIdThenRemoveEntity() {
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, id = 123L))

        testee.removeEntityWithSyncId(syncId)

        assertNull(dao.getSyncId(123L))
    }
}
