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
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.store.SyncStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DeviceInfoUpdaterTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val syncDeviceIds: SyncDeviceIds = mock()
    private val deviceInfoEncryptor: DeviceInfoEncryptor = mock()
    private val deviceFieldEncryptor: DeviceFieldEncryptor = mock()
    private val updater = RealDeviceInfoUpdater(syncStore, syncApi, syncDeviceIds, deviceInfoEncryptor, deviceFieldEncryptor)

    private val updatedDevices = listOf(DeviceV2(deviceId = "device-1", deviceInfo = "device.info.jwe", credentialId = "ddg"))

    @Before
    fun before() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncDeviceIds.deviceType()).thenReturn(DeviceType("phone"))
    }

    @Test
    fun whenAllStepsSucceedThenSendBothEncryptedFormsAndReturnUpdatedDevices() {
        whenever(deviceInfoEncryptor.encrypt("My Phone", "phone")).thenReturn(Result.Success("device.info.jwe"))
        whenever(deviceFieldEncryptor.encrypt("My Phone", "phone"))
            .thenReturn(Result.Success(EncryptedDeviceFields(name = "encName", type = "encType")))
        whenever(syncApi.patchThisDevice(token, "encName", "encType", "device.info.jwe"))
            .thenReturn(Result.Success(PatchDevicesResponse(devicesV2 = updatedDevices)))

        val result = updater.updateThisDevice("My Phone")

        assertEquals(Result.Success(updatedDevices), result)
    }

    @Test
    fun whenNotSignedInThenErrorWithoutEncrypting() {
        whenever(syncStore.token).thenReturn(null)

        assertTrue(updater.updateThisDevice("My Phone") is Result.Error)
        verify(deviceInfoEncryptor, never()).encrypt(any(), any())
    }

    @Test
    fun whenDeviceInfoEncryptionFailsThenErrorWithoutPatching() {
        whenever(deviceInfoEncryptor.encrypt(any(), any())).thenReturn(Result.Error(reason = "no cached account_info key"))

        assertTrue(updater.updateThisDevice("My Phone") is Result.Error)
        verify(syncApi, never()).patchThisDevice(any(), any(), any(), any())
    }

    @Test
    fun whenLegacyFieldEncryptionFailsThenErrorWithoutPatching() {
        whenever(deviceInfoEncryptor.encrypt(any(), any())).thenReturn(Result.Success("device.info.jwe"))
        whenever(deviceFieldEncryptor.encrypt(any(), any())).thenReturn(Result.Error(reason = "primaryKey missing"))

        assertTrue(updater.updateThisDevice("My Phone") is Result.Error)
        verify(syncApi, never()).patchThisDevice(any(), any(), any(), any())
    }

    @Test
    fun whenPatchFailsThenReturnItsError() {
        whenever(deviceInfoEncryptor.encrypt(any(), any())).thenReturn(Result.Success("device.info.jwe"))
        whenever(deviceFieldEncryptor.encrypt(any(), any()))
            .thenReturn(Result.Success(EncryptedDeviceFields(name = "encName", type = "encType")))
        whenever(syncApi.patchThisDevice(any(), any(), any(), any()))
            .thenReturn(Result.Error(code = 500, reason = "unexpected status code"))

        assertEquals(Result.Error(code = 500, reason = "unexpected status code"), updater.updateThisDevice("My Phone"))
    }

    @Test
    fun whenDeviceTypeIsTakenFromThisDeviceThenCallerCannotOverrideIt() {
        whenever(syncDeviceIds.deviceType()).thenReturn(DeviceType("desktop"))
        whenever(deviceInfoEncryptor.encrypt(any(), any())).thenReturn(Result.Success("device.info.jwe"))
        whenever(deviceFieldEncryptor.encrypt(any(), any()))
            .thenReturn(Result.Success(EncryptedDeviceFields(name = "encName", type = "encType")))
        whenever(syncApi.patchThisDevice(any(), any(), any(), any()))
            .thenReturn(Result.Success(PatchDevicesResponse(devicesV2 = updatedDevices)))

        updater.updateThisDevice("My Laptop")

        verify(deviceInfoEncryptor).encrypt(eq("My Laptop"), eq("desktop"))
        verify(deviceFieldEncryptor).encrypt(eq("My Laptop"), eq("desktop"))
    }
}
