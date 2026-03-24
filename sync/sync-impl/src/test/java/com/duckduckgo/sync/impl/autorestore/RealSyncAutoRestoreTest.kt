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

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.duckduckgo.sync.impl.Result as SyncResult

@SuppressLint("DenyListedApi")
class RealSyncAutoRestoreTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val manager: SyncAutoRestoreManager = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()

    private lateinit var testee: RealSyncAutoRestore

    @Before
    fun setup() = runTest {
        configureAutoRestoreEnabled(true)
        testee = RealSyncAutoRestore(
            manager = manager,
            syncFeature = syncFeature,
            syncAccountRepository = syncAccountRepository,
            appScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenFeatureFlagDisabledThenCanRestoreReturnsFalse() = runTest {
        configureAutoRestoreEnabled(false)

        assertFalse(testee.canRestore())
    }

    @Test
    fun whenFeatureFlagEnabledButNoStoredKeyThenCanRestoreReturnsFalse() = runTest {
        configureAutoRestoreEnabled(true)
        configureRetrieveSuccess(payload = null)

        assertFalse(testee.canRestore())
    }

    @Test
    fun whenFeatureFlagEnabledAndKeyExistsThenCanRestoreReturnsTrue() = runTest {
        configureAutoRestoreEnabled(true)
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = "recovery_key", deviceId = null))

        assertTrue(testee.canRestore())
    }

    @Test
    fun whenStorageRetrievalReturnsNullThenCanRestoreReturnsFalse() = runTest {
        configureAutoRestoreEnabled(true)
        configureRetrieveSuccess(payload = null)

        assertFalse(testee.canRestore())
    }

    @Test
    fun whenFeatureFlagDisabledThenRestoreSyncAccountDoesNotCallProcessCode() = runTest {
        configureAutoRestoreEnabled(false)
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = "code", deviceId = null))

        testee.restoreSyncAccount()

        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test
    fun whenRestoreSyncAccountCalledThenRetrievesKeyAndCallsProcessCode() = runTest {
        configureAutoRestoreEnabled(true)
        val recoveryCodeString = "eyJyZWNvdmVyeSI6eyJwcmltYXJ5X2tleSI6ImFiYzEyMyIsInVzZXJfaWQiOiJ1c2VyMTIzIn19"
        val deviceId = "device-abc-123"
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = recoveryCodeString, deviceId = deviceId))
        configureProcessCodeResult(SyncResult.Success(true))

        testee.restoreSyncAccount()

        verify(syncAccountRepository).parseSyncAuthCode(recoveryCodeString)
        verify(syncAccountRepository).processCode(any(), eq(deviceId))
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
        configureAutoRestoreEnabled(true)
        val recoveryCodeString = "invalid_not_base64"
        configureRetrieveSuccess(payload = RestorePayload(recoveryCode = recoveryCodeString, deviceId = "device-123"))
        whenever(syncAccountRepository.parseSyncAuthCode(recoveryCodeString)).thenThrow(RuntimeException("Parse error"))

        testee.restoreSyncAccount()

        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test
    fun whenRestoreSyncAccountCalledButFFDisabledThenDoesNotAccessStorage() = runTest {
        configureAutoRestoreEnabled(false)

        testee.restoreSyncAccount()

        verify(manager, never()).retrieveRecoveryPayload()
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    private fun configureAutoRestoreEnabled(enabled: Boolean) {
        syncFeature.syncAutoRestore().setRawStoredState(State(enable = enabled))
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
