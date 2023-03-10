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

package com.duckduckgo.sync.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedFailDupUser
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedFailInvalid
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedSuccess
import com.duckduckgo.sync.TestSyncFixtures.accountKeys
import com.duckduckgo.sync.TestSyncFixtures.connectBody
import com.duckduckgo.sync.TestSyncFixtures.connectDeviceErrorResponse
import com.duckduckgo.sync.TestSyncFixtures.connectDeviceKeysNotFoundError
import com.duckduckgo.sync.TestSyncFixtures.connectDeviceResponse
import com.duckduckgo.sync.TestSyncFixtures.connectDeviceSuccess
import com.duckduckgo.sync.TestSyncFixtures.connectError
import com.duckduckgo.sync.TestSyncFixtures.connectInvalid
import com.duckduckgo.sync.TestSyncFixtures.connectResponse
import com.duckduckgo.sync.TestSyncFixtures.connectSuccess
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountError
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountInvalid
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountResponse
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountSuccess
import com.duckduckgo.sync.TestSyncFixtures.deviceFactor
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.deviceLogoutBody
import com.duckduckgo.sync.TestSyncFixtures.deviceLogoutResponse
import com.duckduckgo.sync.TestSyncFixtures.deviceName
import com.duckduckgo.sync.TestSyncFixtures.encryptedRecoveryCode
import com.duckduckgo.sync.TestSyncFixtures.getDevicesBodyErrorResponse
import com.duckduckgo.sync.TestSyncFixtures.getDevicesBodySuccessResponse
import com.duckduckgo.sync.TestSyncFixtures.getDevicesError
import com.duckduckgo.sync.TestSyncFixtures.getDevicesSuccess
import com.duckduckgo.sync.TestSyncFixtures.hashedPassword
import com.duckduckgo.sync.TestSyncFixtures.loginError
import com.duckduckgo.sync.TestSyncFixtures.loginFailedInvalidResponse
import com.duckduckgo.sync.TestSyncFixtures.loginRequestBody
import com.duckduckgo.sync.TestSyncFixtures.loginSuccess
import com.duckduckgo.sync.TestSyncFixtures.loginSuccessResponse
import com.duckduckgo.sync.TestSyncFixtures.logoutError
import com.duckduckgo.sync.TestSyncFixtures.logoutSuccess
import com.duckduckgo.sync.TestSyncFixtures.signUpRequest
import com.duckduckgo.sync.TestSyncFixtures.signupFailDuplicatedUser
import com.duckduckgo.sync.TestSyncFixtures.signupFailInvalid
import com.duckduckgo.sync.TestSyncFixtures.signupSuccess
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.userId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Call

@RunWith(AndroidJUnit4::class)
class SyncServiceRemoteTest {

    private val syncService: SyncService = mock()

    @Test
    fun whenCreateAccountSucceedsThenReturnAccountCreatedSuccess() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<AccountCreatedResponse> = mock()
        whenever(syncService.signup(signUpRequest)).thenReturn(call)
        whenever(call.execute()).thenReturn(signupSuccess)

        val result = with(accountKeys) {
            syncRemote.createAccount(userId, passwordHash, protectedSecretKey, deviceId, deviceName, deviceFactor)
        }

        assertEquals(accountCreatedSuccess, result)
    }

    @Test
    fun whenCreateAccountIsInvalidThenReturnError() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<AccountCreatedResponse> = mock()
        whenever(syncService.signup(signUpRequest)).thenReturn(call)
        whenever(call.execute()).thenReturn(signupFailInvalid)

        val result = with(accountKeys) {
            syncRemote.createAccount(userId, passwordHash, protectedSecretKey, deviceId, deviceName, deviceFactor)
        }

        assertEquals(accountCreatedFailInvalid, result)
    }

    @Test
    fun whenCreateAccountDuplicateUserThenReturnError() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<AccountCreatedResponse> = mock()
        whenever(syncService.signup(signUpRequest)).thenReturn(call)
        whenever(call.execute()).thenReturn(signupFailDuplicatedUser)

        val result = with(accountKeys) {
            syncRemote.createAccount(userId, passwordHash, protectedSecretKey, deviceId, deviceName, deviceFactor)
        }

        assertEquals(accountCreatedFailDupUser, result)
    }

    @Test
    fun whenLogoutSucceedsThenReturnLogoutSuccess() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<Logout> = mock()
        whenever(syncService.logout(anyString(), eq(deviceLogoutBody))).thenReturn(call)
        whenever(call.execute()).thenReturn(deviceLogoutResponse)

        val result = syncRemote.logout(token, deviceId)

        assertEquals(logoutSuccess, result)
    }

    @Test
    fun whenLogoutIsInvalidThenReturnError() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<Logout> = mock()
        whenever(syncService.logout(anyString(), eq(deviceLogoutBody))).thenReturn(call)
        whenever(call.execute()).thenReturn(logoutError)

        val result = syncRemote.logout(token, deviceId)

        assertEquals(deleteAccountInvalid, result)
    }

    @Test
    fun whenDeleteAccountSucceedsThenReturnDeleteAccountSuccess() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<Void> = mock()
        whenever(syncService.deleteAccount(anyString())).thenReturn(call)
        whenever(call.execute()).thenReturn(deleteAccountResponse)

        val result = syncRemote.deleteAccount(token)

        assertEquals(deleteAccountSuccess, result)
    }

    @Test
    fun whenDeleteAccountIsInvalidThenReturnError() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<Void> = mock()
        whenever(syncService.deleteAccount(anyString())).thenReturn(call)
        whenever(call.execute()).thenReturn(deleteAccountError)

        val result = syncRemote.deleteAccount(token)

        assertEquals(deleteAccountInvalid, result)
    }

    @Test
    fun whenLoginSucceedsThenReturnLoginSuccess() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<LoginResponse> = mock()
        whenever(syncService.login(loginRequestBody)).thenReturn(call)
        whenever(call.execute()).thenReturn(loginSuccessResponse)

        val result = syncRemote.login(userId, hashedPassword, deviceId, deviceName, deviceFactor)

        assertEquals(loginSuccess, result)
    }

    @Test
    fun whenLoginIsInvalidThenReturnError() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<LoginResponse> = mock()
        whenever(syncService.login(loginRequestBody)).thenReturn(call)
        whenever(call.execute()).thenReturn(loginFailedInvalidResponse)

        val result = syncRemote.login(userId, hashedPassword, deviceId, deviceName, deviceFactor)

        assertEquals(loginError, result)
    }

    @Test
    fun whenGetDevicesSuccessThenResultSuccess() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<DeviceResponse> = mock()
        whenever(syncService.getDevices(anyString())).thenReturn(call)
        whenever(call.execute()).thenReturn(getDevicesBodySuccessResponse)

        val result = syncRemote.getDevices(token)

        assertEquals(getDevicesSuccess, result)
    }

    @Test
    fun whenGetDevicesSuccessFailsThenResultError() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<DeviceResponse> = mock()
        whenever(syncService.getDevices(anyString())).thenReturn(call)
        whenever(call.execute()).thenReturn(getDevicesBodyErrorResponse)

        val result = syncRemote.getDevices(token)

        assertEquals(getDevicesError, result)
    }

    @Test
    fun whenConnectSuccedsThenReturnSuccess() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<Void> = mock()
        whenever(syncService.connect(anyString(), eq(connectBody))).thenReturn(call)
        whenever(call.execute()).thenReturn(connectResponse)

        val result = syncRemote.connect(token, deviceId, encryptedRecoveryCode)

        assertEquals(connectSuccess, result)
    }

    @Test
    fun whenConnectFailsThenReturnError() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<Void> = mock()
        whenever(syncService.connect(anyString(), eq(connectBody))).thenReturn(call)
        whenever(call.execute()).thenReturn(connectInvalid)

        val result = syncRemote.connect(token, deviceId, encryptedRecoveryCode)

        assertEquals(connectError, result)
    }

    @Test
    fun whenConnectDeviceSuccedsThenReturnSuccess() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<ConnectKey> = mock()
        whenever(syncService.connectDevice(deviceId)).thenReturn(call)
        whenever(call.execute()).thenReturn(connectDeviceResponse)

        val result = syncRemote.connectDevice(deviceId)

        assertEquals(connectDeviceSuccess, result)
    }

    @Test
    fun whenConnectDeviceFailsThenReturnError() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<ConnectKey> = mock()
        whenever(syncService.connectDevice(deviceId)).thenReturn(call)
        whenever(call.execute()).thenReturn(connectDeviceErrorResponse)

        val result = syncRemote.connectDevice(deviceId)

        assertEquals(connectDeviceKeysNotFoundError, result)
    }
}
