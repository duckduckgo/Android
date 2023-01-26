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

import com.duckduckgo.sync.TestSyncFixtures.accountCreatedFailDupUser
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedFailInvalid
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedSuccess
import com.duckduckgo.sync.TestSyncFixtures.accountKeys
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountError
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountInvalid
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountResponse
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountSuccess
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.deviceLogoutBody
import com.duckduckgo.sync.TestSyncFixtures.deviceLogoutResponse
import com.duckduckgo.sync.TestSyncFixtures.deviceName
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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Call

class SyncServiceRemoteTest {

    private val syncService: SyncService = mock()

    @Test
    fun whenCreateAccountSucceedsThenReturnAccountCreatedSuccess() {
        val syncRemote = SyncServiceRemote(syncService)
        val call: Call<AccountCreatedResponse> = mock()
        whenever(syncService.signup(signUpRequest)).thenReturn(call)
        whenever(call.execute()).thenReturn(signupSuccess)

        val result = with(accountKeys) {
            syncRemote.createAccount(userId, passwordHash, protectedSecretKey, deviceId, deviceName)
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
            syncRemote.createAccount(userId, passwordHash, protectedSecretKey, deviceId, deviceName)
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
            syncRemote.createAccount(userId, passwordHash, protectedSecretKey, deviceId, deviceName)
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
}
