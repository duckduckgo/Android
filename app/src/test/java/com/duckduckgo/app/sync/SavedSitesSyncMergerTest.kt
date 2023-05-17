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
import com.duckduckgo.savedsites.impl.sync.RealSavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.SavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.SavedSitesSyncMerger
import com.duckduckgo.savedsites.impl.sync.SavedSitesSyncStore
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncablePlugin.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.crypto.SyncNativeLib
import com.duckduckgo.sync.impl.RealSyncCrypto
import com.duckduckgo.sync.store.SyncSharedPrefsStore
import junit.framework.Assert
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedSitesSyncMergerTest {

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
    private lateinit var crypto: SyncCrypto
    private lateinit var syncStore: SyncSharedPrefsStore
    private val sharedPrefsProvider =
        TestSharedPrefsProvider(InstrumentationRegistry.getInstrumentation().context)

    private lateinit var syncMerger: SavedSitesSyncMerger
    @Before
    fun setup(){
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()

        repository = RealSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao)
        duplicateFinder = RealSavedSitesDuplicateFinder(repository)

        store = SavedSitesSyncStore(InstrumentationRegistry.getInstrumentation().context)
        syncStore = SyncSharedPrefsStore(sharedPrefsProvider, TestScope())
        // syncStore.primaryKey = "UqffgOkjsRW2OAXSFMBF9YIQZr7XHPs3BUe0mYT381M="
        // syncStore.secretKey = "ql3DT59/yE7qe7bxKk3/3BSHI8I0QmUuYLKBmGr/umI="
        // crypto = RealSyncCrypto(SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext), syncStore)
        syncMerger = SavedSitesSyncMerger(repository, store, duplicateFinder, FakeCrypto())
    }

    @Test
    fun whenMergingCorruptedDataThenResultIsError(){
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_invalid_data.json")
        val corruptedChanges = SyncChanges(BOOKMARKS, updatesJSON)
        val result = syncMerger.merge(corruptedChanges)

        Assert.assertTrue(result is Error)
    }

    @Test
    fun whenMergingNullEntriesThenResultIsError() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_null_entries.json")
        val corruptedChanges = SyncChanges(BOOKMARKS, updatesJSON)
        val result = syncMerger.merge(corruptedChanges)

        assertTrue(result is Error)
    }

    @Test
    fun whenMergingDataInEmptyDBThenResultIsSuccess() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_first_get.json")
        val validChanges = SyncChanges(BOOKMARKS, updatesJSON)
        val result = syncMerger.merge(validChanges, DEDUPLICATION)

        assertTrue(result is Success)

        val bookmarksRoot = repository.getFolderContentSync(SavedSitesNames.BOOKMARKS_ROOT)
        assertTrue(bookmarksRoot.first[0].id == "2e41e447-40e8-4f93-8e36-18267fa91210")
        assertTrue(bookmarksRoot.first[1].id == "4cde63c7-04ae-44ef-8d9c-ea699c0b679f")
        Assert.assertTrue(bookmarksRoot.second[0].id == "fefb0876-ac2d-41ee-ad5e-56604927d021")

        val subFolder = repository.getFolderContentSync("fefb0876-ac2d-41ee-ad5e-56604927d021")
        assertTrue(subFolder.first[0].id == "45cc093d-e821-445c-bf25-b2f8aaa3d276")
        Assert.assertTrue(subFolder.second.isEmpty())

        val favourites_root = repository.getFavoritesSync()
        assertTrue(favourites_root[0].id == "4cde63c7-04ae-44ef-8d9c-ea699c0b679f")
    }
}

class FakeCrypto: SyncCrypto {
    override fun encrypt(text: String): String {
        return text
    }

    override fun decrypt(data: String): String {
        return data
    }
}
