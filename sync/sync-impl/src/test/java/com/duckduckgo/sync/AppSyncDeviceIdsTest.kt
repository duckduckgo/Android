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

package com.duckduckgo.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.sync.api.DeviceSyncState.Type
import com.duckduckgo.sync.impl.AppSyncDeviceIds
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppSyncDeviceIdsTest {

    private var deviceInfo: DeviceInfo = mock()

    @Test
    fun whenUserIdExistsInStoreThenReturnsStoredValue() = runTest {
        val syncStore = getFakeSyncStore()
        val appSyncDeviceIds = AppSyncDeviceIds(syncStore, deviceInfo)
        assertEquals(syncStore.getUserId(), appSyncDeviceIds.userId())
    }

    @Test
    fun whenDeviceIdExistsInStoreThenReturnsStoredValue() = runTest {
        val syncStore = getFakeSyncStore()
        val appSyncDeviceIds = AppSyncDeviceIds(syncStore, deviceInfo)
        assertEquals(syncStore.getDeviceId(), appSyncDeviceIds.deviceId())
    }

    @Test
    fun whenDeviceNameExistsInStoreThenReturnsStoredValue() = runTest {
        val syncStore = getFakeSyncStore()
        val appSyncDeviceIds = AppSyncDeviceIds(syncStore, deviceInfo)
        assertEquals(syncStore.getDeviceName(), appSyncDeviceIds.deviceName())
    }

    @Test
    fun whenUserIdDoesNotExistInStoreThenNewIdIsReturned() = runTest {
        val emptySyncStore = getFakeEmptySyncStore()
        assertNull(emptySyncStore.getUserId())

        val appSyncDeviceIds = AppSyncDeviceIds(emptySyncStore, deviceInfo)

        val userId = appSyncDeviceIds.userId()
        assertTrue(userId.isNotEmpty())
    }

    @Test
    fun whenDeviceIdDoesNotExistInStoreThenNewIdIsReturned() = runTest {
        val emptySyncStore = getFakeEmptySyncStore()
        assertNull(emptySyncStore.getDeviceId())

        val appSyncDeviceIds = AppSyncDeviceIds(emptySyncStore, deviceInfo)

        val deviceId = appSyncDeviceIds.deviceId()
        assertTrue(deviceId.isNotEmpty())
    }

    @Test
    fun whenDeviceNameDoesNotExistInStoreThenNewIdIsReturned() = runTest {
        val emptySyncStore = getFakeEmptySyncStore()
        assertNull(emptySyncStore.getDeviceName())
        val appSyncDeviceIds = AppSyncDeviceIds(emptySyncStore, deviceInfo)

        val deviceName = appSyncDeviceIds.deviceName()
        assertTrue(deviceName.isNotEmpty())
    }

    @Test
    fun whenPlatformTypeIsAndroidPhoneThenDeviceTypeMobile() {
        val emptySyncStore = getFakeEmptySyncStore()
        whenever(deviceInfo.formFactor()).thenReturn(DeviceInfo.FormFactor.PHONE)
        val appSyncDeviceIds = AppSyncDeviceIds(emptySyncStore, deviceInfo)

        val deviceType = appSyncDeviceIds.deviceType()

        assertEquals(deviceType.type(), Type.MOBILE)
        assertEquals(deviceType.deviceFactor, "phone")
    }

    private fun getFakeSyncStore(): SyncStore {
        return object : SyncStore {
            override suspend fun getSyncingDataEnabled() = true
            override suspend fun setSyncingDataEnabled(enabled: Boolean) {
                TODO("Not yet implemented")
            }

            override suspend fun getUserId(): String? = "testUserId"
            override suspend fun setUserId(userId: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun getDeviceName(): String? = "testDeviceName"
            override suspend fun setDeviceName(deviceName: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun getDeviceId(): String? = "deviceId"
            override suspend fun setDeviceId(deviceId: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun getToken(): String? = "token"
            override suspend fun setToken(token: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun getPrimaryKey(): String? = "primaryKey"
            override suspend fun setPrimaryKey(primaryKey: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun getSecretKey(): String? = "secretKey"
            override suspend fun setSecretKey(secretKey: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun isEncryptionSupported() = true

            override fun isSignedInFlow() = emptyFlow<Boolean>()

            override suspend fun isSignedIn(): Boolean = getPrimaryKey() != null

            override suspend fun storeCredentials(
                userId: String,
                deviceId: String,
                deviceName: String,
                primaryKey: String,
                secretKey: String,
                token: String,
            ) {
                /* no-op */
            }

            override suspend fun clearAll() {
                /* no-op */
            }
        }
    }

    private fun getFakeEmptySyncStore(): SyncStore {
        return object : SyncStore {
            override suspend fun getSyncingDataEnabled() = true
            override suspend fun setSyncingDataEnabled(enabled: Boolean) {
                TODO("Not yet implemented")
            }

            override suspend fun getUserId(): String? = null
            override suspend fun setUserId(userId: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun getDeviceName(): String? = null
            override suspend fun setDeviceName(deviceName: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun getDeviceId(): String? = null
            override suspend fun setDeviceId(deviceId: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun getToken(): String? = null
            override suspend fun setToken(token: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun getPrimaryKey(): String? = null
            override suspend fun setPrimaryKey(primaryKey: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun getSecretKey(): String? = null
            override suspend fun setSecretKey(secretKey: String?) {
                TODO("Not yet implemented")
            }

            override suspend fun isEncryptionSupported() = true

            override fun isSignedInFlow() = emptyFlow<Boolean>()

            override suspend fun isSignedIn(): Boolean = false

            override suspend fun storeCredentials(
                userId: String,
                deviceId: String,
                deviceName: String,
                primaryKey: String,
                secretKey: String,
                token: String,
            ) {
                /* no-op */
            }

            override suspend fun clearAll() {
                /* no-op */
            }
        }
    }
}
