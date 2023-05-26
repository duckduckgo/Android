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

package com.duckduckgo.savedsites.impl.sync.algorithm

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
import com.duckduckgo.savedsites.impl.sync.algorithm.RealSavedSitesSyncPersisterAlgorithm
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesSyncPersisterAlgorithm
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesSyncPersisterStrategy
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncMergeResult
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class SavedSitesSyncPersisterAlgorithmTest {

    //move this to unit test
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val timestampStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val deduplicationStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val remoteStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val localStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val crypto: SyncCrypto = mock()

    private lateinit var algorithm: SavedSitesSyncPersisterAlgorithm

    @Before
    fun setup() {
        algorithm = RealSavedSitesSyncPersisterAlgorithm(crypto, deduplicationStrategy, timestampStrategy, remoteStrategy, localStrategy)
    }

    @Test
    fun whenProcessingEntriesWithDeduplicationStrategyThenDeduplicationPersisterIsUsed() {
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
