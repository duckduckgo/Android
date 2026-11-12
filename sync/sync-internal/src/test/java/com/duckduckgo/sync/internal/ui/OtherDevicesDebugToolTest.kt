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

package com.duckduckgo.sync.internal.ui

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.sync.crypto.LoginKeys
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.AccountInfoKeyManager
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.DeviceFieldEncryptor
import com.duckduckgo.sync.impl.DeviceInfoEncryptor
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.DeviceUpdate
import com.duckduckgo.sync.impl.EncryptedDeviceFields
import com.duckduckgo.sync.impl.LoginResponse
import com.duckduckgo.sync.impl.RemoteDeviceLogout
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.store.AccountInfoPublicKey
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.ZoneId

class OtherDevicesDebugToolTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val syncLib: SyncLib = mock()
    private val deviceFieldEncryptor: DeviceFieldEncryptor = mock()
    private val deviceInfoEncryptor: DeviceInfoEncryptor = mock()
    private val accountInfoKeyManager: AccountInfoKeyManager = mock()
    private val patcher = FakePatcher()
    private val connectedDevicesRepository: ConnectedDevicesRepository = mock()
    private val remoteDeviceLogout: RemoteDeviceLogout = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock { on { currentTimeMillis() } doReturn NOW_MILLIS }

    private val tool = OtherDevicesDebugTool(
        syncStore,
        syncApi,
        syncLib,
        deviceFieldEncryptor,
        deviceInfoEncryptor,
        accountInfoKeyManager,
        patcher,
        connectedDevicesRepository,
        remoteDeviceLogout,
        currentTimeProvider,
    )

    @Before
    fun setUp() {
        whenever(syncStore.isSignedIn()).thenReturn(true)
        whenever(syncStore.token).thenReturn(OUR_TOKEN)
        whenever(syncStore.userId).thenReturn(USER_ID)
        whenever(syncStore.primaryKey).thenReturn(PRIMARY_KEY)
        whenever(syncStore.accountInfoPublicKey).thenReturn(AccountInfoPublicKey("kid", "n", "e"))
        whenever(deviceFieldEncryptor.encrypt(any(), any())).thenReturn(Success(EncryptedDeviceFields("enc-name", "enc-type")))
        whenever(deviceInfoEncryptor.encrypt(any(), any())).thenReturn(Success("device-info-jwe"))
        whenever(syncLib.prepareForLogin(PRIMARY_KEY)).thenReturn(LoginKeys(0, "password-hash", "stretched", PRIMARY_KEY))
        whenever(syncApi.login(eq(USER_ID), eq("password-hash"), any(), eq("enc-name"), eq("enc-type"), eq(null)))
            .thenReturn(Success(LoginResponse(token = FAKE_TOKEN, protected_encryption_key = "pek", devices = emptyList())))
    }

    @Test
    fun whenSimulateLoginThenLogsInANewDeviceAndSetsItsNameWithItsOwnToken() = runTest {
        val text = tool.simulateLogin()

        assertEquals(FAKE_TOKEN, patcher.token)
        assertEquals("enc-name", patcher.update?.name)
        assertEquals("enc-type", patcher.update?.type)
        assertEquals("device-info-jwe", patcher.update?.info)
        verify(deviceFieldEncryptor).encrypt(EXPECTED_NAME, "desktop")
        verify(deviceInfoEncryptor).encrypt(EXPECTED_NAME, "desktop")
        assertTrue(text, text.startsWith("Logged in \"$EXPECTED_NAME\" (${patcher.update?.id}) and set its name."))
    }

    @Test
    fun whenSimulateLoginTwiceThenEachDeviceGetsItsOwnId() = runTest {
        tool.simulateLogin()
        val firstId = patcher.update?.id
        tool.simulateLogin()

        assertTrue(firstId != patcher.update?.id)
    }

    @Test
    fun whenSettingTheNameFailsThenSaysTheNameIsMissing() = runTest {
        patcher.result = Error(code = 400, reason = "bad info")

        val text = tool.simulateLogin()

        assertTrue(text, text.contains("setting its name failed: bad info"))
    }

    @Test
    fun whenLoginFailsThenDoesNotSetAName() = runTest {
        whenever(syncApi.login(any(), any(), any(), any(), any(), eq(null))).thenReturn(Error(code = 429, reason = "rate limited"))

        val text = tool.simulateLogin()

        assertEquals(null, patcher.update)
        assertTrue(text, text.startsWith("Login failed: rate limited"))
    }

    @Test
    fun whenNoCachedAccountInfoKeyThenRegistersOneFirst() = runTest {
        whenever(syncStore.accountInfoPublicKey).thenReturn(null)
        whenever(accountInfoKeyManager.ensureKeyRegistered()).thenReturn(Error(reason = "keys fetch failed"))

        val text = tool.simulateLogin()

        assertTrue(text, text.startsWith("No account_info key: keys fetch failed"))
        verify(syncApi, never()).login(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun whenSignedOutThenSimulateLoginDoesNothing() = runTest {
        whenever(syncStore.isSignedIn()).thenReturn(false)

        assertEquals("Not signed in", tool.simulateLogin())
        verify(syncApi, never()).login(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun whenThirdPartyAccountThenSimulateLoginDoesNothing() = runTest {
        whenever(syncStore.credentialId).thenReturn("3party")

        val text = tool.simulateLogin()

        assertTrue(text, text.startsWith("Only works on a DuckDuckGo Sync account"))
        verify(syncApi, never()).login(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun whenListingThenShowsEveryDeviceExceptThisOne() = runTest {
        whenever(connectedDevicesRepository.getConnectedDevices(false)).thenReturn(Success(listOf(THIS_DEVICE, PHONE, FAKE)))

        assertEquals(OtherDevices(devices = listOf(PHONE, FAKE)), tool.otherDevices())
    }

    @Test
    fun whenListFailsThenSaysWhy() = runTest {
        whenever(connectedDevicesRepository.getConnectedDevices(false)).thenReturn(Error(reason = "network"))

        assertEquals(OtherDevices(devices = emptyList(), error = "Could not get the device list: network"), tool.otherDevices())
    }

    @Test
    fun whenSignedOutThenListsNoDevices() = runTest {
        whenever(syncStore.isSignedIn()).thenReturn(false)

        assertEquals(OtherDevices(devices = emptyList()), tool.otherDevices())
        verifyNoInteractions(connectedDevicesRepository)
    }

    @Test
    fun whenLogOutThenLogsTheDeviceOutWithoutAccountErrorPixel() = runTest {
        whenever(remoteDeviceLogout.logout(PHONE.deviceId, false)).thenReturn(Success(true))

        assertEquals("Logged out \"${PHONE.deviceName}\"", tool.logOut(PHONE))
    }

    @Test
    fun whenLogOutFailsThenSaysWhy() = runTest {
        whenever(remoteDeviceLogout.logout(PHONE.deviceId, false)).thenReturn(Error(reason = "network"))

        assertEquals("Log out failed: network", tool.logOut(PHONE))
    }

    private class FakePatcher : FakeDevicePatcher {
        var token: String? = null
        var update: DeviceUpdate? = null
        var result: Result<Unit> = Success(Unit)

        override fun patch(token: String, update: DeviceUpdate): Result<Unit> {
            this.token = token
            this.update = update
            return result
        }
    }

    private companion object {
        const val USER_ID = "user"
        const val OUR_TOKEN = "our-token"
        const val FAKE_TOKEN = "fake-token"
        const val PRIMARY_KEY = "primary-key"
        val NOW_MILLIS: Long = LocalDateTime.of(2026, 11, 12, 14, 32, 5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        const val EXPECTED_NAME = "Fake device 14:32:05"
        val THIS_DEVICE = ConnectedDevice(thisDevice = true, deviceName = "This phone", deviceId = "this", deviceType = DeviceType("phone"))
        val PHONE = ConnectedDevice(deviceName = "Pixel 9", deviceId = "phone-1", deviceType = DeviceType("phone"))
        val FAKE = ConnectedDevice(deviceName = "Fake device 10:00:00", deviceId = "fake-1", deviceType = DeviceType("desktop"))
    }
}
