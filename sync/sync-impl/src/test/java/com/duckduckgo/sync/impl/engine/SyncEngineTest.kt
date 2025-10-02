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

import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.ModifiedSince.FirstSync
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_CREATION
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_LOGIN
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.APP_OPEN
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.BACKGROUND_SYNC
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.DATA_CHANGE
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.API_CODE.TOO_MANY_REQUESTS_1
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.engine.SyncOperation.DISCARD
import com.duckduckgo.sync.impl.engine.SyncOperation.EXECUTE
import com.duckduckgo.sync.impl.error.SyncOperationErrorRecorder
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.SyncStore
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncAttemptState.FAIL
import com.duckduckgo.sync.store.model.SyncAttemptState.IN_PROGRESS
import com.duckduckgo.sync.store.model.SyncAttemptState.SUCCESS
import com.duckduckgo.sync.store.model.SyncOperationErrorType.ORPHANS_PRESENT
import com.duckduckgo.sync.store.model.SyncOperationErrorType.TIMESTAMP_CONFLICT
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class SyncEngineTest {

    private val syncApiClient: SyncApiClient = mock()
    private val syncScheduler: SyncScheduler = mock()
    private val syncStateRepository: SyncStateRepository = mock()
    private val syncPixels: SyncPixels = mock()
    private val syncStore: SyncStore = mock()
    private val syncOperationErrorRecorder: SyncOperationErrorRecorder = mock()
    private val providerPlugins: PluginPoint<SyncableDataProvider> = mock()
    private val persisterPlugins: PluginPoint<SyncableDataPersister> = mock()
    private val lifecyclePlugins: PluginPoint<SyncEngineLifecycle> = mock()
    private lateinit var syncEngine: RealSyncEngine

    @Before
    fun before() {
        syncEngine = RealSyncEngine(
            syncApiClient,
            syncScheduler,
            syncStateRepository,
            syncPixels,
            syncStore,
            syncOperationErrorRecorder,
            providerPlugins,
            persisterPlugins,
            lifecyclePlugins,
        )
        whenever(syncStore.isSignedIn()).thenReturn(true)
        whenever(syncStore.syncingDataEnabled).thenReturn(true)
    }

    @Test
    fun whenFeatureReadTriggeredAndSyncIsDisabledNoSyncOperationIsTriggered() {
        whenever(syncStore.isSignedIn()).thenReturn(false)
        syncEngine.triggerSync(FEATURE_READ)
        verifyNoInteractions(syncApiClient)
        verifyNoInteractions(syncScheduler)
        verifyNoInteractions(syncStateRepository)
    }

    @Test
    fun whenAppOpensAndSyncIsDisabledNoSyncOperationIsTriggered() {
        whenever(syncStore.isSignedIn()).thenReturn(false)
        syncEngine.triggerSync(APP_OPEN)
        verifyNoInteractions(syncApiClient)
        verifyNoInteractions(syncScheduler)
        verifyNoInteractions(syncStateRepository)
    }

    @Test
    fun whenBackgroundSyncOperationTriggeredAndSyncIsDisabledNoSyncOperationIsTriggered() {
        whenever(syncStore.isSignedIn()).thenReturn(false)
        syncEngine.triggerSync(BACKGROUND_SYNC)
        verifyNoInteractions(syncApiClient)
        verifyNoInteractions(syncScheduler)
        verifyNoInteractions(syncStateRepository)
    }

    @Test
    fun whenDataChangesAndSyncIsDisabledNoSyncOperationIsTriggered() {
        whenever(syncStore.isSignedIn()).thenReturn(false)
        syncEngine.triggerSync(DATA_CHANGE)
        verifyNoInteractions(syncApiClient)
        verifyNoInteractions(syncScheduler)
        verifyNoInteractions(syncStateRepository)
    }

    @Test
    fun whenCreatingSyncAccountAndNoLocalChangesThenNothingIsSent() {
        syncEngine.triggerSync(ACCOUNT_CREATION)

        verifyNoInteractions(syncApiClient)
    }

    @Test
    fun whenCreatingSyncAccountThenDataIsSentAndStateUpdatedWithSuccess() {
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.triggerSync(ACCOUNT_CREATION)

        verify(syncStateRepository).store(any())
        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenSyncingDataIsDisabledThenNoSyncOperationIsTriggered() {
        whenever(syncStore.isSignedIn()).thenReturn(true)
        whenever(syncStore.syncingDataEnabled).thenReturn(false)

        SyncTrigger.values().forEach { syncTrigger ->
            syncEngine.triggerSync(syncTrigger)
        }

        verifyNoInteractions(syncApiClient)
        verifyNoInteractions(syncScheduler)
        verifyNoInteractions(syncStateRepository)
    }

    // https://app.asana.com/0/1204842202586359/1205158805627400/f
    @Test
    @Ignore
    fun whenCreatingSyncAccountThenDataIsSentAndStateUpdatedWithError() {
        givenLocalChanges()
        givenPatchError()

        syncEngine.triggerSync(ACCOUNT_CREATION)

        verify(syncStateRepository).store(any())
        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenAppOpenWithoutChangesAndGetRemoteSucceedsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetSuccess()

        syncEngine.triggerSync(APP_OPEN)

        verify(syncApiClient).get(any(), any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    // https://app.asana.com/0/1204842202586359/1205158805627400/f
    @Test
    @Ignore
    fun whenAppOpenWithoutChangesAndGetRemoteFailsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetError()

        syncEngine.triggerSync(APP_OPEN)

        verify(syncApiClient).get(any(), any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenAppOpenWithChangesAndPatchRemoteSucceedsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.triggerSync(APP_OPEN)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenAppOpenWithChangesAndFeatureFirstSyncThenPerformGetAndPatch() {
        givenFirstSyncLocalChanges()
        givenGetSuccess()
        givenPatchSuccess()

        syncEngine.triggerSync(APP_OPEN)

        verify(syncApiClient).get(any(), any())
        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    // https://app.asana.com/0/1204842202586359/1205158805627400/f
    @Test
    @Ignore
    fun whenAppOpenWithChangesAndPatchRemoteFailsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchError()

        syncEngine.triggerSync(APP_OPEN)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenFeatureReadWithoutChangesAndGetRemoteSucceedsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetSuccess()

        syncEngine.triggerSync(FEATURE_READ)

        verify(syncApiClient).get(any(), any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    // https://app.asana.com/0/1204842202586359/1205158805627400/f
    @Test
    @Ignore
    fun whenFeatureReadWithoutChangesAndGetRemoteFailsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetError()

        syncEngine.triggerSync(FEATURE_READ)

        verify(syncApiClient).get(any(), any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenFeatureReadWithChangesAndPatchRemoteSucceedsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.triggerSync(FEATURE_READ)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    // https://app.asana.com/0/1204842202586359/1205158805627400/f
    @Test
    @Ignore
    fun whenFeatureReadWithChangesAndPatchRemoteFailsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchError()

        syncEngine.triggerSync(FEATURE_READ)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenDataChangeWithoutChangesAndGetRemoteSucceedsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetSuccess()

        syncEngine.triggerSync(DATA_CHANGE)

        verify(syncApiClient).get(any(), any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    // https://app.asana.com/0/1204842202586359/1205158805627400/f
    @Test
    @Ignore
    fun whenDataChangeWithoutChangesAndGetRemoteFailsThenStateIsUpdated() {
        givenNoLocalChanges()
        givenGetError()

        syncEngine.triggerSync(DATA_CHANGE)

        verify(syncApiClient).get(any(), any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenDataChangeWithChangesAndPatchRemoteSucceedsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.triggerSync(DATA_CHANGE)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenDataChangeWithChangesForFirstSyncThenStateIsUpdated() {
        givenFirstSyncLocalChanges()
        givenPatchSuccess()
        givenGetSuccess()

        syncEngine.triggerSync(DATA_CHANGE)

        verify(syncApiClient).get(any(), any())
        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    // https://app.asana.com/0/1204842202586359/1205158805627400/f
    @Test
    @Ignore
    fun whenDataChangeWithChangesAndPatchRemoteFailsThenStateIsUpdated() {
        givenLocalChanges()
        givenPatchError()

        syncEngine.triggerSync(DATA_CHANGE)

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

        syncEngine.triggerSync(BACKGROUND_SYNC)

        verify(syncApiClient).get(any(), any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    // https://app.asana.com/0/1204842202586359/1205158805627400/f
    @Test
    @Ignore
    fun whenBackgroundSyncWithoutChangesAndGetRemoteFailsThenStateIsUpdated() {
        whenever(syncScheduler.scheduleOperation()).thenReturn(EXECUTE)
        givenNoLocalChanges()
        givenGetError()

        syncEngine.triggerSync(BACKGROUND_SYNC)

        verify(syncApiClient).get(any(), any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenBackgroundSyncWithChangesAndPatchRemoteSucceedsThenStateIsUpdated() {
        whenever(syncScheduler.scheduleOperation()).thenReturn(EXECUTE)
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.triggerSync(BACKGROUND_SYNC)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenFirstSyncBackgroundSyncWithChangesAndPatchRemoteSucceedsThenStateIsUpdated() {
        whenever(syncScheduler.scheduleOperation()).thenReturn(EXECUTE)
        givenFirstSyncLocalChanges()
        givenPatchSuccess()
        givenGetSuccess()

        syncEngine.triggerSync(BACKGROUND_SYNC)

        verify(syncApiClient).get(any(), any())
        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    // https://app.asana.com/0/1204842202586359/1205158805627400/f
    @Test
    @Ignore
    fun whenBackgroundSyncWithChangesAndPatchRemoteFailsThenStateIsUpdated() {
        whenever(syncScheduler.scheduleOperation()).thenReturn(EXECUTE)
        givenLocalChanges()
        givenPatchError()

        syncEngine.triggerSync(BACKGROUND_SYNC)

        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    // https://app.asana.com/0/1204842202586359/1205158805627400/f
    @Test
    @Ignore
    fun whenAccountLoginGetRemoteFailsThenStateIsUpdated() {
        givenLocalChanges()
        givenGetError()

        syncEngine.triggerSync(ACCOUNT_LOGIN)

        verify(syncApiClient).get(any(), any())
        verify(syncStateRepository).updateSyncState(FAIL)
    }

    @Test
    fun whenAccountLoginSucceedsThenStateIsUpdated() {
        givenFirstSyncLocalChanges()
        givenPatchSuccess()
        givenGetSuccess()

        syncEngine.triggerSync(ACCOUNT_LOGIN)

        verify(syncApiClient).get(any(), any())
        verify(syncApiClient).patch(any())
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenTriggeringSyncAndSyncAlreadyInProgressThenSyncIsDismissed() {
        whenever(syncStateRepository.current()).thenReturn(SyncAttempt(state = IN_PROGRESS))
        syncEngine.triggerSync(DATA_CHANGE)
        verifyNoInteractions(syncApiClient)
    }

    @Test
    fun whenPatchNewDataFailsBecauseCountLimitThenNotifyFeature() {
        givenLocalChanges()
        givenPatchLimitError()
        val persisterPluginMock = mock<SyncableDataPersister>()
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(persisterPluginMock))

        syncEngine.triggerSync(DATA_CHANGE)

        verify(persisterPluginMock).onError(SyncErrorResponse(BOOKMARKS, FeatureSyncError.COLLECTION_LIMIT_REACHED))
    }

    @Test
    fun whenPatchNewDataFailsBecauseContentTooLargeThenNotifyFeature() {
        givenLocalChanges()
        givenPatchContentTooLargeError()
        val persisterPluginMock = mock<SyncableDataPersister>()
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(persisterPluginMock))

        syncEngine.triggerSync(DATA_CHANGE)

        verify(persisterPluginMock).onError(SyncErrorResponse(BOOKMARKS, FeatureSyncError.COLLECTION_LIMIT_REACHED))
    }

    @Test
    fun whenPatchNewDataFailsBecauseNonFeatureErrorThenDoNotNotifyFeature() {
        givenLocalChanges()
        givenPatchError()
        val persisterPluginMock = mock<SyncableDataPersister>()
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(persisterPluginMock))

        syncEngine.triggerSync(DATA_CHANGE)

        verify(persisterPluginMock, times(0)).onError(any())
    }

    @Test
    fun whenSyncTriggeredDailyPixelIsSent() {
        givenLocalChanges()
        givenPatchSuccess()

        syncEngine.triggerSync(APP_OPEN)

        verify(syncApiClient).patch(any())
        verify(syncPixels).fireDailySuccessRatePixel()
        verify(syncPixels).fireDailyPixel()
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenSyncTriggeredWithChangesAndPatchRemoteSucceedsWithTimestampConflictThenStateIsUpdatedAndPixelIsFired() {
        givenLocalChangesWithTimestampConflict()
        givenPatchSuccess()

        syncEngine.triggerSync(APP_OPEN)

        verify(syncApiClient).patch(any())
        verify(syncPixels).fireDailySuccessRatePixel()
        verify(syncPixels).fireDailyPixel()
        verify(syncOperationErrorRecorder).record(BOOKMARKS.field, TIMESTAMP_CONFLICT)
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    @Test
    fun whenSyncTriggeredWithChangesAndPatchRemoteSucceedsWithOrphansThenStateIsUpdatedAndPixelIsFired() {
        givenLocalChangesWithOrphansPresent()
        givenPatchSuccess()

        syncEngine.triggerSync(APP_OPEN)

        verify(syncApiClient).patch(any())
        verify(syncPixels).fireDailySuccessRatePixel()
        verify(syncPixels).fireDailyPixel()
        verify(syncOperationErrorRecorder).record(BOOKMARKS.field, ORPHANS_PRESENT)
        verify(syncStateRepository).updateSyncState(SUCCESS)
    }

    private fun givenNoLocalChanges() {
        val fakePersisterPlugin = FakeSyncableDataPersister()
        val fakeProviderPlugin = FakeSyncableDataProvider(fakeChanges = SyncChangesRequest.empty())
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(fakePersisterPlugin))
        whenever(providerPlugins.getPlugins()).thenReturn(listOf(fakeProviderPlugin))
    }

    private fun givenLocalChanges() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "data_sync_sent_bookmarks.json")
        val localChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, ModifiedSince.Timestamp("2021-01-01T00:00:00.000Z"))
        val fakePersisterPlugin = FakeSyncableDataPersister()
        val fakeProviderPlugin = FakeSyncableDataProvider(fakeChanges = localChanges)
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(fakePersisterPlugin)).thenReturn(listOf(FakeSyncableDataPersister()))
        whenever(providerPlugins.getPlugins()).thenReturn(listOf(fakeProviderPlugin))
            .thenReturn(listOf(FakeSyncableDataProvider(fakeChanges = SyncChangesRequest.empty())))
    }

    private fun givenLocalChangesWithTimestampConflict() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "data_sync_sent_bookmarks.json")
        val localChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, ModifiedSince.Timestamp("2021-01-01T00:00:00.000Z"))
        val fakeProviderPlugin = FakeSyncableDataProvider(fakeChanges = localChanges)
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(FakeSyncableDataPersister(timestampConflict = true)))
            .thenReturn(listOf(FakeSyncableDataPersister(timestampConflict = true)))
        whenever(providerPlugins.getPlugins()).thenReturn(listOf(fakeProviderPlugin))
            .thenReturn(listOf(FakeSyncableDataProvider(fakeChanges = SyncChangesRequest.empty())))
    }

    private fun givenLocalChangesWithOrphansPresent() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "data_sync_sent_bookmarks.json")
        val localChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, ModifiedSince.Timestamp("2021-01-01T00:00:00.000Z"))
        val fakeProviderPlugin = FakeSyncableDataProvider(fakeChanges = localChanges)
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(FakeSyncableDataPersister(orphans = true)))
            .thenReturn(listOf(FakeSyncableDataPersister(orphans = true)))
        whenever(providerPlugins.getPlugins()).thenReturn(listOf(fakeProviderPlugin))
            .thenReturn(listOf(FakeSyncableDataProvider(fakeChanges = SyncChangesRequest.empty())))
    }

    private fun givenFirstSyncLocalChanges() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "data_sync_sent_bookmarks.json")
        val firstSyncLocalChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, FirstSync)
        val localChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, ModifiedSince.Timestamp("2021-01-01T00:00:00.000Z"))
        val fakePersisterPlugin = FakeSyncableDataPersister()
        whenever(persisterPlugins.getPlugins()).thenReturn(listOf(fakePersisterPlugin)).thenReturn(listOf(FakeSyncableDataPersister()))
        whenever(providerPlugins.getPlugins())
            .thenReturn(listOf(FakeSyncableDataProvider(fakeChanges = firstSyncLocalChanges)))
            .thenReturn(listOf(FakeSyncableDataProvider(fakeChanges = localChanges)))
            .thenReturn(listOf(FakeSyncableDataProvider(fakeChanges = SyncChangesRequest.empty())))
    }

    private fun givenGetError() {
        whenever(syncApiClient.get(any(), any())).thenReturn(
            Result.Error(400, "get failed"),
        )
    }

    private fun givenGetSuccess() {
        whenever(syncApiClient.get(any(), any())).thenReturn(
            Success(
                SyncChangesResponse.empty(BOOKMARKS),
            ),
        )
    }

    private fun givenPatchError() {
        whenever(syncApiClient.patch(any())).thenReturn(
            Result.Error(TOO_MANY_REQUESTS_1.code, "patch failed"),
        )
    }

    private fun givenPatchLimitError() {
        whenever(syncApiClient.patch(any())).thenReturn(
            Result.Error(API_CODE.COUNT_LIMIT.code, "patch failed"),
        )
    }

    private fun givenPatchContentTooLargeError() {
        whenever(syncApiClient.patch(any())).thenReturn(
            Result.Error(API_CODE.CONTENT_TOO_LARGE.code, "patch failed"),
        )
    }

    private fun givenPatchSuccess() {
        whenever(syncApiClient.patch(any())).thenReturn(
            Success(
                SyncChangesResponse.empty(BOOKMARKS),
            ),
        )
    }
}
