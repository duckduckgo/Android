/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.sync.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.sync.TestSyncFixtures.secretKey
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.crypto.EncryptBytesResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel
import com.duckduckgo.sync.store.ScopedPassword
import com.duckduckgo.sync.store.SyncStore
import org.junit.Assert.assertTrue
import org.junit.Before
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
class AccountInfoDdgWrapRepairerTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val protectedKeyUnwrapper: ProtectedKeyUnwrapper = mock()
    private val nativeLib: SyncLib = mock()
    private val syncPixels: SyncPixels = mock()

    private lateinit var repairer: AccountInfoDdgWrapRepairer

    @Before
    fun before() {
        repairer = RealAccountInfoDdgWrapRepairer(
            syncStore = syncStore,
            syncApi = syncApi,
            protectedKeyUnwrapper = protectedKeyUnwrapper,
            nativeLib = nativeLib,
            syncPixels = syncPixels,
        )
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword("scoped-password"))
    }

    @Test
    fun whenWinningKeyIsThirdPartyOnlyThenAddsDdgWrapForThatKid() {
        val thirdPartyEntry = givenRewrappableThirdPartyEntry()
        givenSetKeysIfAbsent(Result.Success(SetKeysIfAbsentResult.Created))

        val result = repairer.repair("winning-kid", listOf(thirdPartyEntry))

        assertTrue(result is Result.Success<*>)
        verify(syncApi).setKeysIfAbsent(
            eq(token),
            eq(SYNC_PURPOSE_ACCOUNT_INFO),
            check { entries ->
                assertTrue(entries.size == 1)
                assertTrue(entries.single().kid == "winning-kid")
                assertTrue(entries.single().encryptedWith == CREDENTIAL_ID_DDG)
            },
        )
        verify(syncPixels).fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.AccountInfoKeyWrapSuccess)
    }

    @Test
    fun whenWinningKeyAlreadyHasDdgWrapThenDoesNothing() {
        val entries = listOf(
            accountInfoEntry(kid = "winning-kid", encryptedWith = CREDENTIAL_ID_DDG),
            accountInfoEntry(kid = "winning-kid", encryptedWith = CREDENTIAL_ID_3PARTY),
        )

        val result = repairer.repair("winning-kid", entries)

        assertTrue(result is Result.Success<*>)
        verifyNoInteractions(protectedKeyUnwrapper, nativeLib, syncPixels)
        verify(syncApi, never()).setKeysIfAbsent(any(), any(), any())
    }

    @Test
    fun whenThirdPartyWrapBelongsToDifferentKidThenDoesNotAddDdgWrap() {
        val thirdPartyEntry = accountInfoEntry(kid = "losing-kid", encryptedWith = CREDENTIAL_ID_3PARTY)

        val result = repairer.repair("winning-kid", listOf(thirdPartyEntry))

        assertTrue(result is Result.Error)
        verifyNoInteractions(protectedKeyUnwrapper, nativeLib, syncPixels)
        verify(syncApi, never()).setKeysIfAbsent(any(), any(), any())
    }

    @Test
    fun whenEntriesAreNotProvidedThenFetchesBeforeRepairing() {
        val thirdPartyEntry = givenRewrappableThirdPartyEntry()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Result.Success(listOf(thirdPartyEntry)))
        givenSetKeysIfAbsent(Result.Success(SetKeysIfAbsentResult.Created))

        val result = repairer.repair("winning-kid")

        assertTrue(result is Result.Success<*>)
        verify(syncApi).getProtectedKeys(token)
        verify(syncApi).setKeysIfAbsent(eq(token), eq(SYNC_PURPOSE_ACCOUNT_INFO), any())
    }

    @Test
    fun whenAddingDdgWrapFailsThenReturnsError() {
        val thirdPartyEntry = givenRewrappableThirdPartyEntry()
        givenSetKeysIfAbsent(Result.Error(reason = "server error"))

        val result = repairer.repair("winning-kid", listOf(thirdPartyEntry))

        assertTrue(result is Result.Error)
        verify(syncPixels).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.REQUEST_FAILED),
        )
    }

    @Test
    fun whenServerReturnsDifferentExistingKidThenReturnsError() {
        val thirdPartyEntry = givenRewrappableThirdPartyEntry()
        givenSetKeysIfAbsent(Result.Success(SetKeysIfAbsentResult.Existing("different-kid", RsaJwk(n = "other", e = "AQAB"))))

        val result = repairer.repair("winning-kid", listOf(thirdPartyEntry))

        assertTrue(result is Result.Error)
        verify(syncPixels).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.REQUEST_FAILED),
        )
    }

    @Test
    fun whenServerReturnsSameExistingKidWithoutDdgWrapThenReturnsError() {
        val thirdPartyEntry = givenRewrappableThirdPartyEntry()
        givenSetKeysIfAbsent(Result.Success(SetKeysIfAbsentResult.Existing("winning-kid", RsaJwk(n = "n", e = "AQAB"))))
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Result.Success(listOf(thirdPartyEntry)))

        val result = repairer.repair("winning-kid", listOf(thirdPartyEntry))

        assertTrue(result is Result.Error)
        verify(syncPixels).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.REQUEST_FAILED),
        )
    }

    @Test
    fun whenServerReturnsSameExistingKidWithDdgWrapThenReturnsSuccess() {
        val thirdPartyEntry = givenRewrappableThirdPartyEntry()
        val ddgEntry = accountInfoEntry(kid = "winning-kid", encryptedWith = CREDENTIAL_ID_DDG)
        givenSetKeysIfAbsent(Result.Success(SetKeysIfAbsentResult.Existing("winning-kid", RsaJwk(n = "n", e = "AQAB"))))
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Result.Success(listOf(thirdPartyEntry, ddgEntry)))

        val result = repairer.repair("winning-kid", listOf(thirdPartyEntry))

        assertTrue(result is Result.Success<*>)
    }

    @Test
    fun whenScopedPasswordIsMissingThenReturnsErrorWithoutFetchingKeys() {
        whenever(syncStore.scopedPassword).thenReturn(null)

        val result = repairer.repair("winning-kid")

        assertTrue(result is Result.Error)
        verify(syncApi, never()).getProtectedKeys(any())
        verify(syncApi, never()).setKeysIfAbsent(any(), any(), any())
    }

    private fun givenRewrappableThirdPartyEntry(
        kid: String = "winning-kid",
    ): ProtectedKeyEntry {
        val entry = accountInfoEntry(kid = kid, encryptedWith = CREDENTIAL_ID_3PARTY)
        whenever(protectedKeyUnwrapper.unwrap(entry)).thenReturn(Result.Success("raw-key".toByteArray()))
        whenever(nativeLib.encryptData(any<ByteArray>(), eq(secretKey)))
            .thenReturn(EncryptBytesResult(0, "ddg-wrapped".toByteArray()))
        return entry
    }

    private fun givenSetKeysIfAbsent(result: Result<SetKeysIfAbsentResult>) {
        whenever(syncApi.setKeysIfAbsent(eq(token), eq(SYNC_PURPOSE_ACCOUNT_INFO), any())).thenReturn(result)
    }

    private fun accountInfoEntry(kid: String, encryptedWith: String) = ProtectedKeyEntry(
        kid = kid,
        purpose = SYNC_PURPOSE_ACCOUNT_INFO,
        encryptedWith = encryptedWith,
        encryptedPrivateKey = "wrapped-private-key",
        publicKey = RsaJwk(n = "n", e = "AQAB"),
    )
}
