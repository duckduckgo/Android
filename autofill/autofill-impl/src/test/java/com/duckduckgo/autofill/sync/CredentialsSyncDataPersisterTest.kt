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
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.autofill.store.AutofillDatabase
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.autofill.sync.persister.CredentialsMergeStrategy
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.api.engine.SyncableType.CREDENTIALS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
internal class CredentialsSyncDataPersisterTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AutofillDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.credentialsSyncDao()
    private val credentialsSyncMetadata = CredentialsSyncMetadata(dao)
    private val autofillStore = FakeCredentialsSyncStore()
    private val strategies = createFakeStrategies()
    private val appBuildConfig = mock<AppBuildConfig>().apply {
        whenever(this.flavor).thenReturn(BuildFlavor.PLAY)
    }

    private val syncPersister = CredentialsSyncDataPersister(
        credentialsSyncMetadata = credentialsSyncMetadata,
        credentialsSyncStore = autofillStore,
        strategies = strategies,
        appBuildConfig = appBuildConfig,
    )

    @After
    fun after() = runBlocking {
        db.close()
    }

    @Test
    fun whenValidatingCorruptedDataThenResultIsError() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/sync/merger_invalid_data.json")
        val corruptedChanges = SyncChangesResponse(CREDENTIALS, updatesJSON)
        val result = syncPersister.persist(corruptedChanges, TIMESTAMP)

        assertTrue(result is Error)
    }

    @Test
    fun whenValidatingNullEntriesThenResultIsError() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/sync/merger_null_entries.json")
        val corruptedChanges = SyncChangesResponse(CREDENTIALS, updatesJSON)
        val result = syncPersister.persist(corruptedChanges, TIMESTAMP)

        assertTrue(result is Error)
    }

    @Test
    fun whenProcessingDataInEmptyDBThenResultIsSuccess() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/sync/merger_first_get.json")
        val validChanges = SyncChangesResponse(CREDENTIALS, updatesJSON)
        val result = syncPersister.persist(validChanges, DEDUPLICATION)

        assertTrue(result is Success)
    }

    @Test
    fun whenMergingEmptyEntriesThenResultIsSuccess() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/sync/merger_empty_entries.json")
        val corruptedChanges = SyncChangesResponse(CREDENTIALS, updatesJSON)
        val result = syncPersister.persist(corruptedChanges, TIMESTAMP)

        assertTrue(result is Success)
    }

    @Test
    fun whenMergingWithDeletedDataThenResultIsSuccess() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/sync/merger_deleted_entries.json")
        val deletedChanges = SyncChangesResponse(CREDENTIALS, updatesJSON)
        val result = syncPersister.persist(deletedChanges, TIMESTAMP)

        assertTrue(result is Success)
    }

    @Test
    fun whenPersistWithAnotherTypeThenReturnFalse() {
        val result = syncPersister.persist(
            SyncChangesResponse(BOOKMARKS, ""),
            DEDUPLICATION,
        )

        assertEquals(Success(false), result)
    }

    @Test
    fun whenPersistFinishesThenPruneLocalDeletedEntities() {
        dao.insert(CredentialsSyncMetadataEntity("123", 1L, "2022-08-30T00:00:00Z"))

        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/sync/merger_first_get.json")
        val validChanges = SyncChangesResponse(CREDENTIALS, updatesJSON)
        val result = syncPersister.persist(validChanges, DEDUPLICATION)

        assertTrue(result is Success)
        assertNull(dao.getSyncId(1L))
    }

    private fun createFakeStrategies(): Map<SyncConflictResolution, CredentialsMergeStrategy> {
        val strategies = mutableMapOf<SyncConflictResolution, CredentialsMergeStrategy>()
        SyncConflictResolution.values().forEach {
            strategies[it] = FakeCredentialsMergeStrategy()
        }

        return strategies
    }
}

private class FakeCredentialsMergeStrategy : CredentialsMergeStrategy {
    var result: SyncMergeResult<Boolean> = Success(true)

    override fun processEntries(credentials: CrendentialsSyncEntries): SyncMergeResult<Boolean> {
        return result
    }
}
