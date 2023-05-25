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

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_CREATION
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_LOGIN
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.APP_OPEN
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.BACKGROUND_SYNC
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.DATA_CHANGE
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.api.engine.SyncableDataPersister
import com.duckduckgo.sync.api.engine.SyncableDataProvider
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.engine.SyncOperation.DISCARD
import com.duckduckgo.sync.impl.engine.SyncOperation.EXECUTE
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

    @Before
    fun before() {
        syncEngine = RealSyncEngine(syncApiClient, syncScheduler, syncStateRepository, providerPlugins, persisterPlugins)
    }

    @Test
    fun whenCreatingSyncAccountAndNoLocalChangesThenNothingIsSent() {
        syncEngine.syncNow(ACCOUNT_CREATION)
        verifyNoInteractions(syncStateRepository)
        verifyNoInteractions(syncApiClient)
    }

    @Test
    fun whenCreatingSyncAccountThenDataIsSentAndStateUpdatedWithSuccess() {
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.syncNow(ACCOUNT_CREATION)

        verify(syncStateRepository).store(any())
        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenCreatingSyncAccountThenDataIsSentAndStateUpdatedWithError() {
        givenLocalChanges()
        givenPatchError()

        syncEngine.syncNow(ACCOUNT_CREATION)

        verify(syncStateRepository).store(any())
        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenAppOpenWithoutChangesAndGetRemoteSucceedsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetSuccess()

        syncEngine.syncNow(APP_OPEN)

        verify(syncApiClient).get(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenAppOpenWithoutChangesAndGetRemoteFailsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetError()

        syncEngine.syncNow(APP_OPEN)

        verify(syncApiClient).get(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenAppOpenWithChangesAndPatchRemoteSucceedsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.syncNow(APP_OPEN)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenAppOpenWithChangesAndPatchRemoteFailsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchError()

        syncEngine.syncNow(APP_OPEN)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenFeatureReadWithoutChangesAndGetRemoteSucceedsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetSuccess()

        syncEngine.syncNow(FEATURE_READ)

        verify(syncApiClient).get(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenFeatureReadWithoutChangesAndGetRemoteFailsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetError()

        syncEngine.syncNow(FEATURE_READ)

        verify(syncApiClient).get(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenFeatureReadWithChangesAndPatchRemoteSucceedsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.syncNow(FEATURE_READ)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenFeatureReadWithChangesAndPatchRemoteFailsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchError()

        syncEngine.syncNow(FEATURE_READ)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenDataChangeWithoutChangesAndGetRemoteSucceedsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetSuccess()

        syncEngine.syncNow(DATA_CHANGE)

        verify(syncApiClient).get(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenDataChangeWithoutChangesAndGetRemoteFailsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetError()

        syncEngine.syncNow(DATA_CHANGE)

        verify(syncApiClient).get(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenDataChangeWithChangesAndPatchRemoteSucceedsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.syncNow(DATA_CHANGE)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenDataChangeWithChangesAndPatchRemoteFailsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchError()

        syncEngine.syncNow(DATA_CHANGE)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenBackgroundSyncCantBeScheduledThenNothingHappens() {
        whenever(syncScheduler.scheduleOperation()).thenReturn(DISCARD)

        verifyNoInteractions(syncApiClient)
        verifyNoInteractions(syncStateRepository)
    }

    @Test
    fun whenBackgroundSyncWithoutChangesAndGetRemoteSucceedsThenStateIsUpdated() {
        whenever(syncScheduler.scheduleOperation()).thenReturn(EXECUTE)
        givenNoLocalChanges()
        givenGetSuccess()

        syncEngine.syncNow(BACKGROUND_SYNC)

        verify(syncApiClient).get(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenBackgroundSyncWithoutChangesAndGetRemoteFailsThenStateIsUpdated() {
        whenever(syncScheduler.scheduleOperation()).thenReturn(EXECUTE)
        givenNoLocalChanges()
        givenGetError()

        syncEngine.syncNow(BACKGROUND_SYNC)

        verify(syncApiClient).get(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenBackgroundSyncWithChangesAndPatchRemoteSucceedsThenStateIsUpdated() {
        whenever(syncScheduler.scheduleOperation()).thenReturn(EXECUTE)
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.syncNow(BACKGROUND_SYNC)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenBackgroundSyncWithChangesAndPatchRemoteFailsThenStateIsUpdated() {
        whenever(syncScheduler.scheduleOperation()).thenReturn(EXECUTE)
        givenLocalChanges()
        givenPatchError()

        syncEngine.syncNow(BACKGROUND_SYNC)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenAccountLoginGetRemoteFailsThenStateIsUpdated() {
        givenLocalChanges()
        givenGetError()

        syncEngine.syncNow(ACCOUNT_LOGIN)

        verify(syncApiClient).get(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    private fun givenNoLocalChanges() {
        val fakePersisterPlugin = FakeSyncableDataPersister()
        val fakeProviderPlugin = FakeSyncableDataProvider(SyncChangesRequest.empty())
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(fakePersisterPlugin))
        whenever(providerPlugins.getPlugins()).thenReturn(listOf(fakeProviderPlugin))
    }

    private fun givenLocalChanges() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "data_sync_sent_bookmarks.json")
        val localChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, "0")
        val fakePersisterPlugin = FakeSyncableDataPersister()
        val fakeProviderPlugin = FakeSyncableDataProvider(localChanges)
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(fakePersisterPlugin))
        whenever(providerPlugins.getPlugins()).thenReturn(listOf(fakeProviderPlugin))
    }

    private fun givenGetError() {
        whenever(syncApiClient.get(any())).thenReturn(
            Result.Error(400, "get failed"),
        )
    }

    private fun givenGetSuccess() {
        whenever(syncApiClient.get(any())).thenReturn(
            Success(
                listOf(SyncChangesResponse.empty()),
            ),
        )
    }

    private fun givenPatchError() {
        whenever(syncApiClient.patch(any())).thenReturn(
            Result.Error(400, "patch failed"),
        )
    }

    private fun givenPatchSuccess() {
        whenever(syncApiClient.patch(any())).thenReturn(
            Success(
                listOf(SyncChangesResponse.empty()),
            ),
        )
    }
}
