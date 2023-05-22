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

package com.duckduckgo.sync.impl.engine

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_CREATION
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.APP_OPEN
import com.duckduckgo.sync.api.engine.SyncableDataPersister
import com.duckduckgo.sync.api.engine.SyncableDataProvider
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.store.model.SyncState.FAIL
import com.duckduckgo.sync.store.model.SyncState.SUCCESS
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class SyncEngineTest {

    private val syncApiClient: SyncApiClient = mock()
    private val syncScheduler: SyncScheduler = mock()
    private val syncStateRepository: SyncStateRepository = mock()
    private val providerPlugins: PluginPoint<SyncableDataProvider> = mock()
    private val persisterPlugins: PluginPoint<SyncableDataPersister> = mock()
    private lateinit var syncEngine: RealSyncEngine

    private val firstSyncWithBookmarksAndFavorites = "{\"bookmarks\":{\"updates\":[{\"client_last_modified\":\"timestamp\",\"folder\"" +
        ":{\"children\":[\"bookmark1\"]},\"id\":\"favorites_root\",\"title\"" +
        ":\"Favorites\"},{\"client_last_modified\":\"timestamp\",\"id\"" +
        ":\"bookmark3\",\"page\":{\"url\":\"https://bookmark3.com\"}," +
        "\"title\":\"Bookmark 3\"},{\"client_last_modified\":\"timestam" +
        "p\",\"id\":\"bookmark4\",\"page\":{\"url\":\"https://bookmark4.com\"},\"title\":\"Bookmark 4\"}," +
        "{\"client_last_modified\":\"timestamp\",\"folder\":{\"children\":[\"bookmark3\",\"bookmark4\"]}" +
        ",\"id\":\"bookmarks_root\",\"title\":\"Bookmarks\"}]}}"

    @Before
    fun before() {
        syncEngine = RealSyncEngine(syncApiClient, syncScheduler, syncStateRepository, providerPlugins, persisterPlugins)
    }

    @Test
    fun whenFirstSyncAndNoLocalChangesThenNothingIsSent() {
        syncEngine.syncNow(ACCOUNT_CREATION)
        verifyNoInteractions(syncStateRepository)
        verifyNoInteractions(syncApiClient)
    }

    @Test
    fun whenFirstSyncLocalChangesThenDataIsSentAndStateUpdatedWithSuccess() {
        val localChanges = SyncChangesRequest(BOOKMARKS, firstSyncWithBookmarksAndFavorites, "0")
        val fakeSyncablePlugin = FakeSyncableDataPersister()
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(fakeSyncablePlugin))
        whenever(syncApiClient.patch(listOf(localChanges))).thenReturn(
            Success(
                listOf(SyncChangesResponse.empty()),
            ),
        )

        syncEngine.syncNow(ACCOUNT_CREATION)

        verify(syncStateRepository).store(any())
        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenFirstSyncLocalChangesThenDataIsSentAndStateUpdatedWithError() {
        val localChanges = SyncChangesRequest(BOOKMARKS, firstSyncWithBookmarksAndFavorites, "0")
        val fakeSyncablePlugin = FakeSyncableDataPersister()
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(fakeSyncablePlugin))
        whenever(syncApiClient.patch(listOf(localChanges))).thenReturn(
            Result.Error(400, "patch failed"),
        )

        syncEngine.syncNow(ACCOUNT_CREATION)

        verify(syncStateRepository).store(any())
        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenSyncAndNoLocalChangesThenGetRemoteChanges() {
        val fakeSyncablePlugin = FakeSyncableDataPersister()
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(fakeSyncablePlugin))

        syncEngine.syncNow(APP_OPEN)

        verify(syncApiClient).get(any())
    }
}
