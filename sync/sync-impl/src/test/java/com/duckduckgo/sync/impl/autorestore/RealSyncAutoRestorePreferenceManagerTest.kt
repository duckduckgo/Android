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
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealSyncAutoRestorePreferenceManagerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val persistentStorage: PersistentStorage = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val dataStore: SyncAutoRestorePreferenceDataStore = mock()

    private val testee = RealSyncAutoRestorePreferenceManager(
        persistentStorage = persistentStorage,
        syncAccountRepository = syncAccountRepository,
        dataStore = dataStore,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenSetRestoreOnReinstallEnabledTrueThenWritesRecoveryCodeToBlockStore() = runTest {
        val authCode = AuthCode(qrCode = "qr_code", rawCode = "recovery_code")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCode))
        whenever(persistentStorage.store(any(), any())).thenReturn(kotlin.Result.success(Unit))
        whenever(dataStore.isRestoreOnReinstallEnabled()).thenReturn(true)

        testee.setRestoreOnReinstallEnabled(true)

        verify(dataStore).setRestoreOnReinstallEnabled(true)
        verify(persistentStorage).store(eq(SyncRecoveryPersistentStorageKey), eq("recovery_code".toByteArray()))
    }

    @Test
    fun whenSetRestoreOnReinstallEnabledFalseThenClearsBlockStore() = runTest {
        whenever(persistentStorage.clear(any())).thenReturn(kotlin.Result.success(Unit))

        testee.setRestoreOnReinstallEnabled(false)

        verify(dataStore).setRestoreOnReinstallEnabled(false)
        verify(persistentStorage).clear(eq(SyncRecoveryPersistentStorageKey))
    }

    @Test
    fun whenSaveRecoveryCodeAndNoRecoveryCodeAvailableThenNoOpNoCrash() = runTest {
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Error(reason = "no code"))

        testee.saveRecoveryCodeToBlockStore()

        verify(persistentStorage, never()).store(any(), any())
    }

    @Test
    fun whenSaveRecoveryCodeAndRecoveryCodeAvailableThenWritesToBlockStore() = runTest {
        val authCode = AuthCode(qrCode = "qr_code", rawCode = "recovery_code")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCode))
        whenever(persistentStorage.store(any(), any())).thenReturn(kotlin.Result.success(Unit))

        testee.saveRecoveryCodeToBlockStore()

        verify(persistentStorage).store(eq(SyncRecoveryPersistentStorageKey), eq("recovery_code".toByteArray()))
    }

    @Test
    fun whenIsRestoreOnReinstallEnabledThenDelegatesToDataStore() = runTest {
        whenever(dataStore.isRestoreOnReinstallEnabled()).thenReturn(true)
        assertTrue(testee.isRestoreOnReinstallEnabled())

        whenever(dataStore.isRestoreOnReinstallEnabled()).thenReturn(false)
        assertFalse(testee.isRestoreOnReinstallEnabled())
    }
}
