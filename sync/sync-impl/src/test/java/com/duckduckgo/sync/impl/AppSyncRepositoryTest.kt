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
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedSuccess
import com.duckduckgo.sync.TestSyncFixtures.accountKeys
import com.duckduckgo.sync.TestSyncFixtures.accountKeysFailed
import com.duckduckgo.sync.TestSyncFixtures.decryptedSecretKey
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountInvalid
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountSuccess
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.deviceName
import com.duckduckgo.sync.TestSyncFixtures.failedLoginKeys
import com.duckduckgo.sync.TestSyncFixtures.hashedPassword
import com.duckduckgo.sync.TestSyncFixtures.invalidDecryptedSecretKey
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKey
import com.duckduckgo.sync.TestSyncFixtures.loginFailed
import com.duckduckgo.sync.TestSyncFixtures.loginSuccess
import com.duckduckgo.sync.TestSyncFixtures.logoutInvalid
import com.duckduckgo.sync.TestSyncFixtures.logoutSuccess
import com.duckduckgo.sync.TestSyncFixtures.patchAllError
import com.duckduckgo.sync.TestSyncFixtures.patchAllSuccess
import com.duckduckgo.sync.TestSyncFixtures.primaryKey
import com.duckduckgo.sync.TestSyncFixtures.protectedEncryptionKey
import com.duckduckgo.sync.TestSyncFixtures.secretKey
import com.duckduckgo.sync.TestSyncFixtures.stretchedPrimaryKey
import com.duckduckgo.sync.TestSyncFixtures.syncData
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.userId
import com.duckduckgo.sync.TestSyncFixtures.validLoginKeys
import com.duckduckgo.sync.api.parser.SyncCrypter
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class AppSyncRepositoryTest {

    private var nativeLib: SyncLib = mock()
    private var syncDeviceIds: SyncDeviceIds = mock()
    private var syncApi: SyncApi = mock()
    private var syncStore: SyncStore = mock()
    private var syncCrypter: SyncCrypter = mock()

    private lateinit var syncRepo: SyncRepository

    @Before
    fun before() {
        syncRepo = AppSyncRepository(syncDeviceIds, nativeLib, syncApi, syncStore, syncCrypter)
    }

    @Test
    fun whenCreateAccountSucceedsThenAccountPersisted() {
        whenever(syncDeviceIds.userId()).thenReturn(userId)
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(nativeLib.generateAccountKeys(userId = anyString(), password = anyString())).thenReturn(accountKeys)
        whenever(
            syncApi.createAccount(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
            ),
        ).thenReturn(accountCreatedSuccess)
        val result = syncRepo.createAccount()

        assertEquals(Result.Success(true), result)
        verify(syncStore).userId = userId
        verify(syncStore).deviceId = deviceId
        verify(syncStore).deviceName = deviceName
        verify(syncStore).token = token
        verify(syncStore).primaryKey = primaryKey
        verify(syncStore).secretKey = secretKey
    }

    @Test
    fun whenCreateAccountFailsThenReturnError() {
        whenever(syncDeviceIds.userId()).thenReturn(userId)
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(nativeLib.generateAccountKeys(userId = anyString(), password = anyString())).thenReturn(accountKeys)
        whenever(
            syncApi.createAccount(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
            ),
        ).thenReturn(accountCreatedFailDupUser)

        val result = syncRepo.createAccount()

        assertEquals(accountCreatedFailDupUser, result)
        verifyNoInteractions(syncStore)
    }

    @Test
    fun whenCreateAccountGenerateKeysFailsThenReturnError() {
        whenever(syncDeviceIds.userId()).thenReturn(userId)
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(nativeLib.generateAccountKeys(userId = anyString(), password = anyString())).thenReturn(accountKeysFailed)
        whenever(
            syncApi.createAccount(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
            ),
        ).thenReturn(accountCreatedSuccess)

        val result = syncRepo.createAccount()

        assertTrue(result is Result.Error)
        verifyNoInteractions(syncApi)
        verifyNoInteractions(syncStore)
    }

    @Test
    fun whenAccountExistsThenGetAccountInfoReturnData() {
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.deviceName).thenReturn(deviceName)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)

        val result = syncRepo.getAccountInfo()

        assertEquals(userId, result.userId)
        assertEquals(deviceId, result.deviceId)
        assertEquals(deviceName, result.deviceName)
        assertTrue(result.isSignedIn)
    }

    @Test
    fun whenAccountNotCreatedThenAccountInfoEmpty() {
        whenever(syncStore.primaryKey).thenReturn("")

        val result = syncRepo.getAccountInfo()

        assertEquals("", result.userId)
        assertEquals("", result.deviceId)
        assertEquals("", result.deviceName)
        assertFalse(result.isSignedIn)
    }

    @Test
    fun whenLogoutSucceedsThenReturnSuccessAndRemoveData() {
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.logout(token, deviceId)).thenReturn(logoutSuccess)

        val result = syncRepo.logout()

        assertTrue(result is Result.Success)
        verify(syncStore).clearAll()
    }

    @Test
    fun whenLogoutFailsThenReturnError() {
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.logout(token, deviceId)).thenReturn(logoutInvalid)

        val result = syncRepo.logout()

        assertTrue(result is Result.Error)
        verify(syncStore, times(0)).clearAll()
    }

    @Test
    fun whenDeleteAccountSucceedsThenReturnSuccessAndRemoveData() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.deleteAccount(token)).thenReturn(deleteAccountSuccess)

        val result = syncRepo.deleteAccount()

        assertTrue(result is Result.Success)
        verify(syncStore).clearAll()
    }

    @Test
    fun whenDeleteAccountFailsThenReturnError() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.deleteAccount(token)).thenReturn(deleteAccountInvalid)

        val result = syncRepo.logout()

        assertTrue(result is Result.Error)
        verify(syncStore, times(0)).clearAll()
    }

    @Test
    fun whenLoginSucceedsThenAccountPersisted() {
        whenever(syncStore.recoveryCode).thenReturn(jsonRecoveryKey)
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(nativeLib.decrypt(encryptedData = protectedEncryptionKey, secretKey = stretchedPrimaryKey)).thenReturn(decryptedSecretKey)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName)).thenReturn(loginSuccess)

        val result = syncRepo.login()

        assertEquals(Result.Success(true), result)
        verify(syncStore).userId = userId
        verify(syncStore).deviceId = deviceId
        verify(syncStore).deviceName = deviceName
        verify(syncStore).token = token
        verify(syncStore).primaryKey = primaryKey
        verify(syncStore).secretKey = secretKey
    }

    @Test
    fun whenRecoveryCodeNotFoudnThenReturnError() {
        whenever(syncStore.recoveryCode).thenReturn(null)

        val result = syncRepo.login()

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenGenerateKeysFromRecoveryCodeFailsThenReturnError() {
        whenever(syncStore.recoveryCode).thenReturn(jsonRecoveryKey)
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(failedLoginKeys)

        val result = syncRepo.login()

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenLoginFailsThenReturnError() {
        whenever(syncStore.recoveryCode).thenReturn(jsonRecoveryKey)
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(nativeLib.decrypt(encryptedData = protectedEncryptionKey, secretKey = stretchedPrimaryKey)).thenReturn(decryptedSecretKey)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName)).thenReturn(loginFailed)

        val result = syncRepo.login()

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenDecryptSecretKeyFailsThenReturnError() {
        whenever(syncStore.recoveryCode).thenReturn(jsonRecoveryKey)
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(nativeLib.decrypt(encryptedData = protectedEncryptionKey, secretKey = stretchedPrimaryKey)).thenReturn(invalidDecryptedSecretKey)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName)).thenReturn(loginSuccess)

        val result = syncRepo.login()

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenInitialPatchSucceedsThenReturnSuccess() = runTest {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncCrypter.generateAllData()).thenReturn(syncData)
        whenever(syncApi.patchAll(token, syncData)).thenReturn(patchAllSuccess)

        val result = syncRepo.initialPatch()

        assertTrue(result is Result.Success)
    }

    @Test
    fun whenInitialPatchFailsThenReturnError() = runTest {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncCrypter.generateAllData()).thenReturn(syncData)
        whenever(syncApi.patchAll(token, syncData)).thenReturn(patchAllError)

        val result = syncRepo.initialPatch()

        assertTrue(result is Result.Error)
    }
}
