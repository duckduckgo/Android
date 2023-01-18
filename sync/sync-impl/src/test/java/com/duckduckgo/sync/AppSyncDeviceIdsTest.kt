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
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.sync.impl.AppSyncDeviceIds
import com.duckduckgo.sync.store.SyncStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AppSyncDeviceIdsTest {

    @Test
    fun whenUserIdExistsInStoreThenReturnsStoredValue() {
        val syncStore = getFakeSyncStore()
        val appSyncDeviceIds = AppSyncDeviceIds(mock(), syncStore)
        assertEquals(syncStore.userId, appSyncDeviceIds.userId())
    }

    @Test
    fun whenDeviceIdExistsInStoreThenReturnsStoredValue() {
        val syncStore = getFakeSyncStore()
        val appSyncDeviceIds = AppSyncDeviceIds(mock(), syncStore)
        assertEquals(syncStore.deviceId, appSyncDeviceIds.deviceId())
    }

    @Test
    fun whenDeviceNameExistsInStoreThenReturnsStoredValue() {
        val syncStore = getFakeSyncStore()
        val appSyncDeviceIds = AppSyncDeviceIds(mock(), syncStore)
        assertEquals(syncStore.deviceName, appSyncDeviceIds.deviceName())
    }

    @Test
    fun whenUserIdDoesNotExistInStoreThenNewIdIsReturned() {
        val emptySyncStore = getFakeEmptySyncStore()
        assertNull(emptySyncStore.userId)
        val appSyncDeviceIds =
            AppSyncDeviceIds(InstrumentationRegistry.getInstrumentation().context, emptySyncStore)

        val userId = appSyncDeviceIds.userId()
        assertEquals(emptySyncStore.userId, userId)
    }

    @Test
    fun whenDeviceIdDoesNotExistInStoreThenNewIdIsReturned() {
        val emptySyncStore = getFakeEmptySyncStore()
        assertNull(emptySyncStore.deviceId)
        val appSyncDeviceIds =
            AppSyncDeviceIds(InstrumentationRegistry.getInstrumentation().context, emptySyncStore)

        val deviceId = appSyncDeviceIds.deviceId()
        assertEquals(emptySyncStore.deviceId, deviceId)
    }

    @Test
    fun whenDeviceNameDoesNotExistInStoreThenNewIdIsReturned() {
        val emptySyncStore = getFakeEmptySyncStore()
        assertNull(emptySyncStore.deviceName)
        val appSyncDeviceIds =
            AppSyncDeviceIds(InstrumentationRegistry.getInstrumentation().context, emptySyncStore)

        val deviceName = appSyncDeviceIds.deviceName()
        assertEquals(emptySyncStore.deviceName, deviceName)
    }

    private fun getFakeSyncStore(): SyncStore {
        return object : SyncStore {
            override var userId: String? = "testUserId"
            override var deviceName: String? = "testDeviceName"
            override var deviceId: String? = "deviceId"
        }
    }

    private fun getFakeEmptySyncStore(): SyncStore {
        return object : SyncStore {
            override var userId: String? = null
            override var deviceName: String? = null
            override var deviceId: String? = null
        }
    }
}
