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

package com.duckduckgo.savedsites.impl.sync

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesSyncPersisterAlgorithm
import com.duckduckgo.sync.api.engine.FeatureSyncError.COLLECTION_LIMIT_REACHED
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncErrorResponse
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import junit.framework.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SavedSitesSyncPersisterTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val store: SavedSitesSyncStore = mock()
    private val persisterAlgorithm: SavedSitesSyncPersisterAlgorithm = mock()
    private val savedSitesFormFactorSyncMigration: SavedSitesFormFactorSyncMigration = mock()
    private val savedSitesSyncFeatureListener: SavedSitesSyncFeatureListener = mock()
    private val syncSavedSitesRepository: SyncSavedSitesRepository = mock()

    private lateinit var syncPersister: SavedSitesSyncPersister

    @Before
    fun setup() {
        syncPersister = SavedSitesSyncPersister(
            store,
            syncSavedSitesRepository,
            persisterAlgorithm,
            savedSitesFormFactorSyncMigration,
            savedSitesSyncFeatureListener,
        )
    }

    @Test
    fun whenValidatingCorruptedDataThenResultIsError() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_invalid_data.json")
        val corruptedChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)
        val result = syncPersister.process(corruptedChanges, TIMESTAMP)

        Assert.assertTrue(result is Error)
    }

    @Test
    fun whenValidatingNullEntriesThenResultIsError() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_null_entries.json")
        val corruptedChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)
        val result = syncPersister.process(corruptedChanges, TIMESTAMP)

        assertTrue(result is Error)
    }

    @Test
    fun whenProcessingDataInEmptyDBThenResultIsSuccess() {
        whenever(persisterAlgorithm.processEntries(any(), any(), any())).thenReturn(Success(true))
        whenever(store.serverModifiedSince).thenReturn(DatabaseDateFormatter.iso8601())
        whenever(store.startTimeStamp).thenReturn(DatabaseDateFormatter.iso8601())
        whenever(store.clientModifiedSince).thenReturn(DatabaseDateFormatter.iso8601())

        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_first_get.json")
        val validChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)
        val result = syncPersister.process(validChanges, TIMESTAMP)

        assertTrue(result is Success)
    }

    @Test
    fun whenMergingEmptyEntriesThenResultIsSuccess() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_empty_entries.json")
        val corruptedChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)
        val result = syncPersister.process(corruptedChanges, TIMESTAMP)

        assertTrue(result is Success)
    }

    @Test
    fun whenMergingWithDeletedDataThenResultIsSuccess() {
        whenever(store.serverModifiedSince).thenReturn(DatabaseDateFormatter.iso8601())
        whenever(store.startTimeStamp).thenReturn(DatabaseDateFormatter.iso8601())
        whenever(store.clientModifiedSince).thenReturn(DatabaseDateFormatter.iso8601())
        whenever(persisterAlgorithm.processEntries(any(), any(), any())).thenReturn(Success(true))
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_deleted_entries.json")
        val deletedChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)
        val result = syncPersister.process(deletedChanges, TIMESTAMP)

        Assert.assertTrue(result is Success)
    }

    @Test
    fun whenOnSuccessThenNotifyListener() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_first_get.json")
        val validChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)

        syncPersister.onSuccess(validChanges, TIMESTAMP)

        verify(savedSitesSyncFeatureListener).onSuccess(validChanges)
    }

    @Test
    fun whenOnErrorThenNotifyListener() {
        syncPersister.onError(SyncErrorResponse(BOOKMARKS, COLLECTION_LIMIT_REACHED))
        verify(savedSitesSyncFeatureListener).onError(COLLECTION_LIMIT_REACHED)
    }

    @Test
    fun whenOnSyncDisabledTheNotifyListener() {
        syncPersister.onSyncDisabled()
        verify(store).serverModifiedSince = "0"
        verify(store).clientModifiedSince = "0"
        verify(store).startTimeStamp = "0"
        verify(savedSitesFormFactorSyncMigration).onFormFactorFavouritesDisabled()
        verify(savedSitesSyncFeatureListener).onSyncDisabled()
        verify(syncSavedSitesRepository).removeMetadata()
    }
}
