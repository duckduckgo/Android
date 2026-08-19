/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.userId
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DeviceInfoMigratorTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val syncDeviceIds: SyncDeviceIds = mock()
    private val deviceInfoUpdater: DeviceInfoUpdater = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var migrator: DeviceInfoMigrator

    @Before
    fun before() {
        migrator = RealDeviceInfoMigrator(
            syncStore = syncStore,
            syncApi = syncApi,
            syncFeature = syncFeature,
            syncDeviceIds = syncDeviceIds,
            deviceInfoUpdater = deviceInfoUpdater,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.deviceId).thenReturn("deviceId")
        whenever(syncDeviceIds.deviceName()).thenReturn("deviceName")
    }

    @Test
    fun whenAlreadyMigratedForUserThenNoOpWithoutNetwork() = runTest {
        whenever(syncStore.unifiedDeviceListMigratedForUserId).thenReturn(userId)

        val result = migrator.ensureMigrated()

        assertTrue(result is Result.Success)
        verify(syncApi, never()).getDevices(any())
    }

    @Test
    fun whenFeatureDisabledThenNoOpAndNotMarked() = runTest {
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = false))

        val result = migrator.ensureMigrated()

        assertTrue(result is Result.Success)
        verify(syncApi, never()).getDevices(any())
        verify(syncStore, never()).unifiedDeviceListMigratedForUserId = any()
    }

    @Test
    fun whenV2ConnectFlowDisabledThenNoOpAndNotMarked() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(enable = false))

        val result = migrator.ensureMigrated()

        assertTrue(result is Result.Success)
        verify(syncApi, never()).getDevices(any())
        verify(syncStore, never()).unifiedDeviceListMigratedForUserId = any()
    }

    @Test
    fun whenNotSignedInThenErrorWithoutNetwork() = runTest {
        whenever(syncStore.userId).thenReturn(null)

        assertTrue(migrator.ensureMigrated() is Result.Error)
        verify(syncApi, never()).getDevices(any())
    }

    @Test
    fun whenServerAlreadyHasDeviceInfoThenMarkDoneWithoutWriting() = runTest {
        whenever(syncApi.getDevices(token)).thenReturn(
            Result.Success(deviceEntries(deviceInfo = "existing.device.info.jwe")),
        )

        val result = migrator.ensureMigrated()

        assertTrue(result is Result.Success)
        verify(syncStore).unifiedDeviceListMigratedForUserId = userId
        verify(deviceInfoUpdater, never()).setThisDeviceName(any())
    }

    @Test
    fun whenDeviceInfoWrittenThenMarkDone() = runTest {
        whenever(syncApi.getDevices(token)).thenReturn(Result.Success(deviceEntries(deviceInfo = null)))
        whenever(deviceInfoUpdater.setThisDeviceName("deviceName")).thenReturn(Result.Success(emptyList()))

        val result = migrator.ensureMigrated()

        assertTrue(result is Result.Success)
        verify(deviceInfoUpdater).setThisDeviceName("deviceName")
        verify(syncStore).unifiedDeviceListMigratedForUserId = userId
    }

    @Test
    fun whenGetDevicesFailsThenErrorAndNotMarked() = runTest {
        whenever(syncApi.getDevices(token)).thenReturn(Result.Error(reason = "network"))

        assertTrue(migrator.ensureMigrated() is Result.Error)
        verify(deviceInfoUpdater, never()).setThisDeviceName(any())
        verify(syncStore, never()).unifiedDeviceListMigratedForUserId = any()
    }

    @Test
    fun whenWriteFailsThenErrorAndNotMarked() = runTest {
        whenever(syncApi.getDevices(token)).thenReturn(Result.Success(deviceEntries(deviceInfo = null)))
        whenever(deviceInfoUpdater.setThisDeviceName("deviceName")).thenReturn(Result.Error(reason = "patch failed"))

        assertTrue(migrator.ensureMigrated() is Result.Error)
        verify(syncStore, never()).unifiedDeviceListMigratedForUserId = any()
    }

    private fun deviceEntries(deviceInfo: String?) = DeviceEntries(
        entries = emptyList(),
        entriesV2 = listOf(DeviceV2(deviceId = "deviceId", deviceName = "enc", deviceType = "enc", deviceInfo = deviceInfo)),
    )
}
