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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.userId
import com.duckduckgo.sync.crypto.EncryptBytesResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel
import com.duckduckgo.sync.store.ScopedPassword
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class LoginDeviceInfoWriterTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val protectedKeyUnwrapper: ProtectedKeyUnwrapper = mock()
    private val nativeLib: SyncLib = mock()
    private val deviceInfoMigrator: DeviceInfoMigrator = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val syncPixels: SyncPixels = mock()

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var writer: RealLoginDeviceInfoWriter

    private val secretKey = "accountSecretKey"

    @Before
    fun before() {
        writer = RealLoginDeviceInfoWriter(
            syncStore = syncStore,
            syncApi = syncApi,
            syncFeature = syncFeature,
            protectedKeyUnwrapper = protectedKeyUnwrapper,
            nativeLib = nativeLib,
            deviceInfoMigrator = deviceInfoMigrator,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            syncPixels = syncPixels,
        )
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(enable = true))
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword("c2NvcGVk"))
        runBlocking { whenever(deviceInfoMigrator.ensureMigrated()).thenReturn(Result.Success(Unit)) }
    }

    @Test
    fun whenAccountInfoKeyAbsentThenNoReWrapButStillMigrates() = runTest {
        val result = writer.onLogin(loginResponseKeys = emptyList())

        assertTrue(result is Result.Success)
        verify(syncApi, never()).setKeysIfAbsent(any(), any(), any())
        verify(deviceInfoMigrator).ensureMigrated()
    }

    @Test
    fun whenAccountInfoKeyAlreadyDdgWrappedThenNoReWrap() = runTest {
        writer.onLogin(loginResponseKeys = listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_DDG)))

        verify(protectedKeyUnwrapper, never()).unwrap(any())
        verify(syncApi, never()).setKeysIfAbsent(any(), any(), any())
        verify(deviceInfoMigrator).ensureMigrated()
    }

    @Test
    fun whenAccountInfo3partyOnlyAndScopedPasswordHeldThenReWrapsForDdgThenMigrates() = runTest {
        whenever(protectedKeyUnwrapper.unwrap(any())).thenReturn(Result.Success("rawKey".toByteArray()))
        whenever(nativeLib.encryptData(any<ByteArray>(), eq(secretKey)))
            .thenReturn(EncryptBytesResult(0, "ddgWrapped".toByteArray()))
        whenever(syncApi.setKeysIfAbsent(eq(token), eq(SYNC_PURPOSE_ACCOUNT_INFO), any()))
            .thenReturn(Result.Success(SetKeysIfAbsentResult.Created))

        writer.onLogin(loginResponseKeys = listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY)))

        verify(syncApi).setKeysIfAbsent(
            eq(token),
            eq(SYNC_PURPOSE_ACCOUNT_INFO),
            check { keys ->
                assertTrue(keys.size == 1)
                assertTrue(keys.first().encryptedWith == CREDENTIAL_ID_DDG)
                assertTrue(keys.first().kid == "kid-1")
            },
        )
        verify(deviceInfoMigrator).ensureMigrated()
        verify(syncPixels).fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.AccountInfoKeyWrapSuccess)
    }

    @Test
    fun whenAccountInfo3partyOnlyButNoScopedPasswordThenSkipsReWrapButStillMigrates() = runTest {
        whenever(syncStore.scopedPassword).thenReturn(null)

        writer.onLogin(loginResponseKeys = listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY)))

        verify(protectedKeyUnwrapper, never()).unwrap(any())
        verify(syncApi, never()).setKeysIfAbsent(any(), any(), any())
        verify(deviceInfoMigrator).ensureMigrated()
    }

    @Test
    fun whenUnwrapFailsThenSkipsReWrapButStillMigrates() = runTest {
        whenever(protectedKeyUnwrapper.unwrap(any())).thenReturn(Result.Error(reason = "cannot unwrap"))

        writer.onLogin(loginResponseKeys = listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY)))

        verify(syncApi, never()).setKeysIfAbsent(any(), any(), any())
        verify(deviceInfoMigrator).ensureMigrated()
        verify(syncPixels).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.UNWRAP_FAILED),
        )
    }

    @Test
    fun whenSetKeysIfAbsentFailsThenStillMigrates() = runTest {
        whenever(protectedKeyUnwrapper.unwrap(any())).thenReturn(Result.Success("rawKey".toByteArray()))
        whenever(nativeLib.encryptData(any<ByteArray>(), eq(secretKey)))
            .thenReturn(EncryptBytesResult(0, "ddgWrapped".toByteArray()))
        whenever(syncApi.setKeysIfAbsent(any(), any(), any())).thenReturn(Result.Error(reason = "server error"))

        val result = writer.onLogin(loginResponseKeys = listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY)))

        assertTrue(result is Result.Success)
        verify(deviceInfoMigrator).ensureMigrated()
        verify(syncPixels).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.REQUEST_FAILED),
        )
    }

    @Test
    fun whenSetKeysIfAbsentReportsExistingThenNoWrapPixelFires() = runTest {
        whenever(protectedKeyUnwrapper.unwrap(any())).thenReturn(Result.Success("rawKey".toByteArray()))
        whenever(nativeLib.encryptData(any<ByteArray>(), eq(secretKey)))
            .thenReturn(EncryptBytesResult(0, "ddgWrapped".toByteArray()))
        whenever(syncApi.setKeysIfAbsent(any(), any(), any())).thenReturn(
            Result.Success(SetKeysIfAbsentResult.Existing("kid-1", RsaJwk(n = "n", e = "AQAB"))),
        )

        writer.onLogin(loginResponseKeys = listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY)))

        verifyNoInteractions(syncPixels)
        verify(deviceInfoMigrator).ensureMigrated()
    }

    @Test
    fun whenSetKeysIfAbsentReportsConflictThenWrapFailedPixelFires() = runTest {
        whenever(protectedKeyUnwrapper.unwrap(any())).thenReturn(Result.Success("rawKey".toByteArray()))
        whenever(nativeLib.encryptData(any<ByteArray>(), eq(secretKey)))
            .thenReturn(EncryptBytesResult(0, "ddgWrapped".toByteArray()))
        whenever(syncApi.setKeysIfAbsent(any(), any(), any())).thenReturn(
            Result.Success(SetKeysIfAbsentResult.ExistsFetchRequired),
        )

        writer.onLogin(loginResponseKeys = listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY)))

        verify(syncPixels).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.REQUEST_FAILED),
        )
        verify(deviceInfoMigrator).ensureMigrated()
    }

    @Test
    fun whenWriteFeatureDisabledThenNothingRuns() = runTest {
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = false))

        val result = writer.onLogin(listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY)))

        assertTrue(result is Result.Success)
        verify(syncApi, never()).setKeysIfAbsent(any(), any(), any())
        verify(deviceInfoMigrator, never()).ensureMigrated()
    }

    @Test
    fun whenV2ConnectFlowDisabledThenNothingRuns() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(enable = false))

        writer.onLogin(listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY)))

        verify(syncApi, never()).setKeysIfAbsent(any(), any(), any())
        verify(deviceInfoMigrator, never()).ensureMigrated()
        verifyNoInteractions(syncPixels)
    }

    private fun accountInfoEntry(encryptedWith: String) = ProtectedKeyEntry(
        kid = "kid-1",
        purpose = SYNC_PURPOSE_ACCOUNT_INFO,
        encryptedWith = encryptedWith,
        encryptedPrivateKey = "wrapped-private-key",
        publicKey = RsaJwk(n = "n", e = "AQAB"),
    )
}
