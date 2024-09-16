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
import com.duckduckgo.autofill.store.AutofillDatabase
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.common.test.CoroutineTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenAddNewEntityThenEntityInserted() {
        assertNull(dao.getLocalId("syncId"))

        testee.addOrUpdate(CredentialsSyncMetadataEntity("syncId", 123L, null, null))

        assertEquals(123L, dao.getLocalId("syncId"))
    }

    @Test
    fun whenUpdateSyncIdExistingEntityThenEntityUpdated() {
        dao.insert(CredentialsSyncMetadataEntity("syncId", 123L, null, null))

        testee.addOrUpdate(CredentialsSyncMetadataEntity("syncId", 456L, null, null))

        assertEquals(456L, dao.getLocalId("syncId"))
    }

    @Test
    fun whenUpdateExistingEntityByLocalIdThenEntityUpdated() {
        dao.insert(CredentialsSyncMetadataEntity("syncId", 123L, null, null))

        testee.addOrUpdate(CredentialsSyncMetadataEntity("syncId2", 123L, null, null))

        assertEquals("syncId2", dao.getSyncMetadata(123L)?.syncId)
    }

    @Test
    fun whenAutofillIdNotFoundThenReturnNull() {
        val syncId = testee.getSyncMetadata(123L)

        assertNull(syncId)
    }

    @Test
    fun whenLoginIdExistsThenReturnSyncMetadata() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, null, null))

        val result = testee.getSyncMetadata(loginId)?.syncId

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
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, null, null))

        val result = testee.createSyncId(loginId)

        assertEquals(syncId, result)
        assertEquals(syncId, dao.getSyncMetadata(loginId)?.syncId)
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
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = localId, null, null))

        val result = testee.getLocalId(syncId)

        assertEquals(localId, result)
    }

    @Test
    fun whenGetRemovedEntitiesThenReturnEntitiesWithDeletedAt() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, deleted_at = "2023-07-21T20:21:40.552Z", null))

        val result = testee.getRemovedEntitiesSince("2023-07-21T20:21:39.000Z")

        assertEquals(1, result.size)
        assertEquals(loginId, result[0].localId)
        assertNotNull(result[0].deleted_at)
    }

    @Test
    fun whenGetRemovedEntitiesThenDoNotReturnEntitiesPreviousToSince() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, deleted_at = "2021-08-30T00:39:00Z", null))

        val result = testee.getRemovedEntitiesSince("2022-08-30T00:40:00Z")

        assertEquals(0, result.size)
    }

    @Test
    fun whenEntityRemovedThenUpdateDeletedAtIfExists() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, null, null))

        testee.onEntityRemoved(loginId)

        val result = dao.getSyncMetadata(loginId)!!
        assertNotNull(result.deleted_at)
    }

    @Test
    fun whenEntitiesRemovedThenUpdateDeletedAtIfExists() {
        val loginId1 = 1L
        val syncId1 = "syncId_1"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId1, localId = loginId1, null, null))

        val loginId2 = 2L
        val syncId2 = "syncId_2"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId2, localId = loginId2, null, null))

        testee.onEntitiesRemoved(listOf(loginId1, loginId2))

        assertNotNull(dao.getSyncMetadata(loginId1)!!.deleted_at)
        assertNotNull(dao.getSyncMetadata(loginId2)!!.deleted_at)
    }

    @Test
    fun whenRemoveDeletedEntitiesThenDeleteEntitiesBeforeDate() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, deleted_at = "2022-08-30T00:00:00Z", null))

        testee.removeDeletedEntities("2022-08-30T00:00:00Z")

        assertNull(dao.getSyncMetadata(loginId))
    }

    @Test
    fun whenRemoveDeletedEntitiesThenKeepEntitiesAfterDate() {
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = 123L, deleted_at = "2022-08-30T00:00:00Z", null))
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = 345L, deleted_at = "2022-09-30T00:00:00Z", null))

        testee.removeDeletedEntities("2022-08-30T00:00:00Z")

        assertNull(dao.getSyncMetadata(123L))
        assertNotNull(dao.getSyncMetadata(345L))
    }

    @Test
    fun whenRemoveEntityWithLocalIdThenRemoveEntity() {
        val loginId = 123L
        dao.insert(CredentialsSyncMetadataEntity(syncId = "syncId", localId = loginId, null, null))

        testee.removeEntityWith(loginId)

        assertNull(dao.getSyncMetadata(loginId))
    }

    @Test
    fun whenRemoveEntityWithSyncIdThenRemoveEntity() {
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = 123L, null, null))

        testee.removeEntityWith(syncId)

        assertNull(dao.getSyncMetadata(123L))
    }

    @Test
    fun whenEntityChangedThenUpdateModifiedAt() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, null, null))

        testee.onEntityChanged(loginId)

        val result = dao.getSyncMetadata(loginId)!!
        assertNotNull(result.modified_at)
    }

    @Test
    fun whenEntityChangedInAListThenUpdateModifiedAt() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, null, null))

        testee.onEntitiesChanged(listOf(loginId))

        val result = dao.getSyncMetadata(loginId)!!
        assertNotNull(result.modified_at)
    }

    @Test
    fun whenEntityChangedDoesNotExistThenInsertedWithModifiedAt() {
        val loginId = 123L

        testee.onEntityChanged(loginId)

        val result = dao.getSyncMetadata(loginId)!!
        assertNotNull(result.modified_at)
    }

    @Test
    fun whenEntityChangedInAListDoesNotExistThenInsertedWithModifiedAt() {
        val loginId = 123L

        testee.onEntitiesChanged(listOf(loginId))

        val result = dao.getSyncMetadata(loginId)!!
        assertNotNull(result.modified_at)
    }

    @Test
    fun whenGetChangesSinceThenReturnChanges() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, null, "2022-08-30T00:40:00Z"))

        val result = testee.getChangesSince("2022-08-30T00:30:00Z")

        assertEquals(1, result.size)
        assertEquals(loginId, result[0].localId)
    }

    @Test
    fun whenGetAllThenReturnAll() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, null, null))

        val result = testee.getAllCredentials()

        assertEquals(1, result.size)
        assertEquals(loginId, result[0].localId)
    }

    @Test
    fun whenClearAllThenRemoveAll() {
        val loginId = 123L
        val syncId = "syncId"
        dao.insert(CredentialsSyncMetadataEntity(syncId = syncId, localId = loginId, null, null))

        testee.clearAll()

        assertEquals(0, dao.getAll().size)
    }
}
