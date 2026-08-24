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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.store.AccountInfoPublicKey
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class DeviceInfoUpdaterTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val syncDeviceIds: SyncDeviceIds = mock()
    private val deviceInfoEncryptor: DeviceInfoEncryptor = mock()
    private val deviceFieldEncryptor: DeviceFieldEncryptor = mock()
    private val accountInfoKeyManager: AccountInfoKeyManager = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var updater: RealDeviceInfoUpdater

    private val updatedDevices = listOf(DeviceV2(deviceId = "device-1", deviceInfo = "device.info.jwe", credentialId = "ddg"))
    private val cachedPublicKey = AccountInfoPublicKey(keyId = "kid-1", modulus = "n", exponent = "AQAB")
    private val keyRegistered = AccountInfoKeyResult(kid = "kid-1", publicKey = RsaJwk(n = "n", e = "AQAB"), created = true, wrapsSent = 1)

    @Before
    fun before() {
        updater = RealDeviceInfoUpdater(
            syncStore = syncStore,
            syncApi = syncApi,
            syncDeviceIds = syncDeviceIds,
            deviceInfoEncryptor = deviceInfoEncryptor,
            deviceFieldEncryptor = deviceFieldEncryptor,
            accountInfoKeyManager = accountInfoKeyManager,
            syncFeature = syncFeature,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.accountInfoPublicKey).thenReturn(cachedPublicKey)
        whenever(syncDeviceIds.deviceType()).thenReturn(DeviceType("phone"))
    }

    @Test
    fun whenAllStepsSucceedThenSendBothEncryptedFormsAndReturnUpdatedDevices() = runTest {
        whenever(deviceInfoEncryptor.encrypt("My Phone", "phone")).thenReturn(Result.Success("device.info.jwe"))
        whenever(deviceFieldEncryptor.encrypt("My Phone", "phone"))
            .thenReturn(Result.Success(EncryptedDeviceFields(name = "encName", type = "encType")))
        whenever(syncApi.patchThisDevice(token, "encName", "encType", "device.info.jwe"))
            .thenReturn(Result.Success(PatchDevicesResponse(devicesV2 = updatedDevices)))

        val result = updater.setThisDeviceName("My Phone")

        assertEquals(Result.Success(updatedDevices), result)
    }

    @Test
    fun whenUpdateSucceedsThenStoreTheNewNameLocally() = runTest {
        givenEncryptionAndPatchSucceed()

        updater.setThisDeviceName("My Phone")

        verify(syncStore).deviceName = "My Phone"
    }

    @Test
    fun whenNotSignedInThenErrorWithoutEncrypting() = runTest {
        whenever(syncStore.token).thenReturn(null)

        assertTrue(updater.setThisDeviceName("My Phone") is Result.Error)
        verify(deviceInfoEncryptor, never()).encrypt(any(), any())
    }

    @Test
    fun whenAccountInfoPublicKeyIsCachedThenNoKeyRegistration() = runTest {
        givenEncryptionAndPatchSucceed()

        updater.setThisDeviceName("My Phone")

        verify(accountInfoKeyManager, never()).ensureKeyRegistered()
    }

    @Test
    fun whenAccountInfoPublicKeyIsMissingThenRegisterKeyBeforePatching() = runTest {
        whenever(syncStore.accountInfoPublicKey).thenReturn(null)
        whenever(accountInfoKeyManager.ensureKeyRegistered()).thenReturn(Result.Success(keyRegistered))
        givenEncryptionAndPatchSucceed()

        val result = updater.setThisDeviceName("My Phone")

        assertTrue(result is Result.Success)
        verify(accountInfoKeyManager).ensureKeyRegistered()
        verify(syncApi).patchThisDevice(any(), any(), any(), any())
    }

    @Test
    fun whenKeyRegistrationFailsThenErrorWithoutEncryptingOrPatching() = runTest {
        whenever(syncStore.accountInfoPublicKey).thenReturn(null)
        whenever(accountInfoKeyManager.ensureKeyRegistered()).thenReturn(Result.Error(reason = "no account secret key"))

        assertTrue(updater.setThisDeviceName("My Phone") is Result.Error)
        verify(deviceInfoEncryptor, never()).encrypt(any(), any())
        verify(syncApi, never()).patchThisDevice(any(), any(), any(), any())
    }

    @Test
    fun whenDeviceInfoEncryptionFailsThenErrorWithoutPatching() = runTest {
        whenever(deviceInfoEncryptor.encrypt(any(), any())).thenReturn(Result.Error(reason = "no cached account_info key"))

        assertTrue(updater.setThisDeviceName("My Phone") is Result.Error)
        verify(syncApi, never()).patchThisDevice(any(), any(), any(), any())
    }

    @Test
    fun whenLegacyFieldEncryptionFailsThenErrorWithoutPatching() = runTest {
        whenever(deviceInfoEncryptor.encrypt(any(), any())).thenReturn(Result.Success("device.info.jwe"))
        whenever(deviceFieldEncryptor.encrypt(any(), any())).thenReturn(Result.Error(reason = "primaryKey missing"))

        assertTrue(updater.setThisDeviceName("My Phone") is Result.Error)
        verify(syncApi, never()).patchThisDevice(any(), any(), any(), any())
    }

    @Test
    fun whenPatchFailsThenReturnItsErrorAndLeaveTheStoredNameAlone() = runTest {
        whenever(deviceInfoEncryptor.encrypt(any(), any())).thenReturn(Result.Success("device.info.jwe"))
        whenever(deviceFieldEncryptor.encrypt(any(), any()))
            .thenReturn(Result.Success(EncryptedDeviceFields(name = "encName", type = "encType")))
        whenever(syncApi.patchThisDevice(any(), any(), any(), any()))
            .thenReturn(Result.Error(code = 500, reason = "unexpected status code"))

        assertEquals(Result.Error(code = 500, reason = "unexpected status code"), updater.setThisDeviceName("My Phone"))
        verify(syncStore, never()).deviceName = any()
    }

    @Test
    fun whenWriteFeatureDisabledThenOmitDeviceInfoSoTheServerClearsItAndStoreTheNewNameLocally() = runTest {
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = false))
        givenEncryptionAndPatchSucceed()

        val result = updater.setThisDeviceName("My Phone")

        assertTrue(result is Result.Success)
        verify(syncApi).patchThisDevice(token, "encName", "encType", null)
        verify(syncStore).deviceName = "My Phone"
    }

    @Test
    fun whenWriteFeatureDisabledThenNoDeviceInfoEncryptionOrKeyRegistration() = runTest {
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = false))
        whenever(syncStore.accountInfoPublicKey).thenReturn(null)
        givenEncryptionAndPatchSucceed()

        updater.setThisDeviceName("My Phone")

        verify(accountInfoKeyManager, never()).ensureKeyRegistered()
        verify(deviceInfoEncryptor, never()).encrypt(any(), any())
    }

    @Test
    fun whenWriteFeatureDisabledAndPatchFailsThenReturnItsErrorAndLeaveTheStoredNameAlone() = runTest {
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = false))
        whenever(deviceFieldEncryptor.encrypt(any(), any()))
            .thenReturn(Result.Success(EncryptedDeviceFields(name = "encName", type = "encType")))
        whenever(syncApi.patchThisDevice(any(), any(), any(), anyOrNull()))
            .thenReturn(Result.Error(code = 500, reason = "unexpected status code"))

        assertTrue(updater.setThisDeviceName("My Phone") is Result.Error)
        verify(syncStore, never()).deviceName = any()
    }

    @Test
    fun whenV2ConnectFlowDisabledThenOmitDeviceInfoEvenThoughWritingIsEnabled() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(enable = false))
        whenever(syncStore.accountInfoPublicKey).thenReturn(null)
        givenEncryptionAndPatchSucceed()

        val result = updater.setThisDeviceName("My Phone")

        assertTrue(result is Result.Success)
        verify(syncApi).patchThisDevice(token, "encName", "encType", null)
        verify(accountInfoKeyManager, never()).ensureKeyRegistered()
    }

    @Test
    fun whenWriteFeatureEnabledThenSendDeviceInfoAlongsideTheLegacyFields() = runTest {
        givenEncryptionAndPatchSucceed()

        updater.setThisDeviceName("My Phone")

        verify(syncApi).patchThisDevice(token, "encName", "encType", "device.info.jwe")
    }

    @Test
    fun whenDeviceTypeIsTakenFromThisDeviceThenCallerCannotOverrideIt() = runTest {
        whenever(syncDeviceIds.deviceType()).thenReturn(DeviceType("desktop"))
        givenEncryptionAndPatchSucceed()

        updater.setThisDeviceName("My Laptop")

        verify(deviceInfoEncryptor).encrypt(eq("My Laptop"), eq("desktop"))
        verify(deviceFieldEncryptor).encrypt(eq("My Laptop"), eq("desktop"))
    }

    private fun givenEncryptionAndPatchSucceed() {
        whenever(deviceInfoEncryptor.encrypt(any(), any())).thenReturn(Result.Success("device.info.jwe"))
        whenever(deviceFieldEncryptor.encrypt(any(), any()))
            .thenReturn(Result.Success(EncryptedDeviceFields(name = "encName", type = "encType")))
        whenever(syncApi.patchThisDevice(any(), any(), any(), anyOrNull()))
            .thenReturn(Result.Success(PatchDevicesResponse(devicesV2 = updatedDevices)))
    }
}
