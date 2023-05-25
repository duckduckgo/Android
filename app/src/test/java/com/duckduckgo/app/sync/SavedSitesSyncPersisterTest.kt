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

package com.duckduckgo.app.sync

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.algorithm.RealSavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.SavedSitesSyncPersister
import com.duckduckgo.savedsites.impl.sync.SavedSitesSyncStore
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import junit.framework.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedSitesSyncPersisterTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: SavedSitesRepository
    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao
    private lateinit var duplicateFinder: SavedSitesDuplicateFinder
    private lateinit var store: FeatureSyncStore

    private lateinit var syncPersister: SavedSitesSyncPersister

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()

        repository = RealSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao)
        duplicateFinder = RealSavedSitesDuplicateFinder(repository)

        store = SavedSitesSyncStore(InstrumentationRegistry.getInstrumentation().context)
        syncPersister = SavedSitesSyncPersister(repository, store, duplicateFinder, FakeCrypto())
    }

    @Test
    fun whenMergingCorruptedDataThenResultIsError() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_invalid_data.json")
        val corruptedChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)
        val result = syncPersister.merge(corruptedChanges, TIMESTAMP)

        Assert.assertTrue(result is Error)
    }

    @Test
    fun whenMergingNullEntriesThenResultIsError() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_null_entries.json")
        val corruptedChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)
        val result = syncPersister.merge(corruptedChanges, TIMESTAMP)

        assertTrue(result is Error)
    }

    @Test
    fun whenMergingDataInEmptyDBThenResultIsSuccess() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_first_get.json")
        val validChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)
        val result = syncPersister.merge(validChanges, DEDUPLICATION)

        assertTrue(result is Success)

        val bookmarksRoot = repository.getFolderContentSync(SavedSitesNames.BOOKMARKS_ROOT)
        assertTrue(bookmarksRoot.first[0].id == "2e41e447-40e8-4f93-8e36-18267fa91210")
        assertTrue(bookmarksRoot.first[1].id == "4cde63c7-04ae-44ef-8d9c-ea699c0b679f")
        Assert.assertTrue(bookmarksRoot.second[0].id == "fefb0876-ac2d-41ee-ad5e-56604927d021")

        val subFolder = repository.getFolderContentSync("fefb0876-ac2d-41ee-ad5e-56604927d021")
        assertTrue(subFolder.first[0].id == "45cc093d-e821-445c-bf25-b2f8aaa3d276")
        Assert.assertTrue(subFolder.second.isEmpty())

        val favouritesRoot = repository.getFavoritesSync()
        assertTrue(favouritesRoot[0].id == "4cde63c7-04ae-44ef-8d9c-ea699c0b679f")
    }

    @Test
    fun whenMergingEmptyEntriesThenResultIsSuccess() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_empty_entries.json")
        val corruptedChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)
        val result = syncPersister.merge(corruptedChanges, TIMESTAMP)

        assertTrue(result is Error)
    }

    @Test
    fun whenMergingWithDeletedDataThenResultIsSuccess() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_deleted_entries.json")
        val deletedChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)
        val result = syncPersister.merge(deletedChanges, TIMESTAMP)

        Assert.assertTrue(result is Success)
    }
}

class FakeCrypto : SyncCrypto {
    override fun encrypt(text: String): String {
        return text
    }

    override fun decrypt(data: String): String {
        return data
    }
}
