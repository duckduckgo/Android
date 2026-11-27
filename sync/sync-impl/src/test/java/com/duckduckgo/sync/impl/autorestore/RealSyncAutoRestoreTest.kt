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

package com.duckduckgo.sync.impl.autorestore

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.AccountInfo
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.pixels.SyncPixels
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.duckduckgo.sync.impl.Result as SyncResult

class RealSyncAutoRestoreTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val manager: SyncAutoRestoreManager = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val syncPixels: SyncPixels = mock()

    private lateinit var testee: RealSyncAutoRestore

    @Before
    fun setup() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(AccountInfo())
        testee = RealSyncAutoRestore(
            manager = manager,
            syncAccountRepository = syncAccountRepository,
            syncPixels = syncPixels,
            appScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenKeyExistsThenCanRestoreReturnsTrue() = runTest {
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = "recovery_key", deviceId = null))

        assertTrue(testee.canRestore())
    }

    @Test
    fun whenStorageRetrievalReturnsNullThenCanRestoreReturnsFalse() = runTest {
        configureRetrieveSuccess(payload = null)

        assertFalse(testee.canRestore())
    }

    @Test
    fun whenRestoreSyncAccountCalledThenRetrievesKeyAndCallsProcessCode() = runTest {
        val recoveryCodeString = "eyJyZWNvdmVyeSI6eyJwcmltYXJ5X2tleSI6ImFiYzEyMyIsInVzZXJfaWQiOiJ1c2VyMTIzIn19"
        val deviceId = "device-abc-123"
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = recoveryCodeString, deviceId = deviceId))
        configureProcessCodeResult(SyncResult.Success(true))

        testee.restoreSyncAccount()

        verify(syncAccountRepository).parseSyncAuthCode(recoveryCodeString)
        verify(syncAccountRepository).processCode(any(), eq(deviceId))
    }

    @Test
    fun whenRestoreSucceedsThenSavesAutoRestoreDataToReaffirmPreferenceAndPayload() = runTest {
        val recoveryCodeString = "eyJyZWNvdmVyeSI6eyJwcmltYXJ5X2tleSI6ImFiYzEyMyIsInVzZXJfaWQiOiJ1c2VyMTIzIn19"
        val deviceId = "device-abc-123"
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = recoveryCodeString, deviceId = deviceId))
        configureProcessCodeResult(SyncResult.Success(true))

        testee.restoreSyncAccount()

        verify(manager).saveAutoRestoreData(recoveryCodeString, deviceId)
    }

    @Test
    fun whenRestoredWithoutADeviceIdThenSavesTheDeviceIdItSignedInWith() = runTest {
        val recoveryCodeString = "eyJyZWNvdmVyeSI6eyJwcmltYXJ5X2tleSI6ImFiYzEyMyIsInVzZXJfaWQiOiJ1c2VyMTIzIn19"
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = recoveryCodeString, deviceId = null))
        configureProcessCodeResult(SyncResult.Success(true))
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(AccountInfo(deviceId = "new-device-id", isSignedIn = true))

        testee.restoreSyncAccount()

        verify(syncAccountRepository).processCode(any(), isNull())
        verify(manager).saveAutoRestoreData(recoveryCodeString, "new-device-id")
    }

    @Test
    fun whenRestoreFailsThenDoesNotSaveAutoRestoreData() = runTest {
        val recoveryCodeString = "eyJyZWNvdmVyeSI6eyJwcmltYXJ5X2tleSI6ImFiYzEyMyIsInVzZXJfaWQiOiJ1c2VyMTIzIn19"
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = recoveryCodeString, deviceId = "device-123"))
        configureProcessCodeResult(SyncResult.Error(code = 52, reason = "Login failed"))

        testee.restoreSyncAccount()

        verify(manager, never()).saveAutoRestoreData(any(), anyOrNull())
    }

    @Test
    fun whenRestoreSyncAccountCalledButNoStoredKeyThenDoesNotCallProcessCode() = runTest {
        configureRetrieveSuccess(payload = null)

        testee.restoreSyncAccount()

        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test
    fun whenRestoreSyncAccountCalledButStorageReturnsNullThenDoesNotCallProcessCode() = runTest {
        configureRetrieveSuccess(payload = null)

        testee.restoreSyncAccount()

        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test
    fun whenRestoreSyncAccountCalledButRetrieveThrowsThenDoesNotCallProcessCode() = runTest {
        whenever(manager.retrieveRecoveryPayload()).thenThrow(RuntimeException("Storage failure"))

        testee.restoreSyncAccount()

        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test
    fun whenProcessCodeFailsThenRestoreSyncAccountDoesNotThrow() = runTest {
        val recoveryCode = "eyJyZWNvdmVyeSI6eyJwcmltYXJ5X2tleSI6ImFiYzEyMyIsInVzZXJfaWQiOiJ1c2VyMTIzIn19"
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = recoveryCode, deviceId = "device-123"))
        configureProcessCodeResult(SyncResult.Error(code = 52, reason = "Login failed"))

        testee.restoreSyncAccount()

        verify(syncAccountRepository).processCode(any(), anyOrNull())
    }

    @Test
    fun whenParseSyncAuthCodeThrowsThenRestoreSyncAccountDoesNotCrash() = runTest {
        val recoveryCodeString = "invalid_not_base64"
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = recoveryCodeString, deviceId = "device-123"))
        whenever(syncAccountRepository.parseSyncAuthCode(recoveryCodeString)).thenThrow(RuntimeException("Parse error"))

        testee.restoreSyncAccount()

        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    private suspend fun configureRetrieveSuccess(payload: RestorePayload?) {
        whenever(manager.retrieveRecoveryPayload()).thenReturn(payload)
    }

    private fun configureProcessCodeResult(result: SyncResult<Boolean>) {
        val mockParsedCode = mock<SyncAuthCode.Recovery>()
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(mockParsedCode)
        whenever(syncAccountRepository.processCode(eq(mockParsedCode), anyOrNull())).thenReturn(result)
    }
}
