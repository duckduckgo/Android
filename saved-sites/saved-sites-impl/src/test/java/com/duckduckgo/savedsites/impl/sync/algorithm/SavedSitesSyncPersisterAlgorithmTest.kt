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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.sync.api.SyncCrypto
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class SavedSitesSyncPersisterAlgorithmTest {

    // move this to unit test
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val timestampStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val deduplicationStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val remoteStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val localStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val repository: SavedSitesRepository = mock()
    private val crypto: SyncCrypto = mock()

    private lateinit var algorithm: SavedSitesSyncPersisterAlgorithm

    @Before
    fun setup() {
        algorithm = RealSavedSitesSyncPersisterAlgorithm(crypto, repository, deduplicationStrategy, timestampStrategy, remoteStrategy, localStrategy)
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
