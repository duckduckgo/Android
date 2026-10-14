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
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.userId
import com.duckduckgo.sync.store.SyncStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AccountInfoPrivateKeyProviderTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val protectedKeyUnwrapper: ProtectedKeyUnwrapper = mock()
    private val provider = RealAccountInfoPrivateKeyProvider(syncStore, syncApi, protectedKeyUnwrapper)

    private val ddgKey = ProtectedKeyEntry(
        kid = "kid-1",
        purpose = "account_info",
        encryptedWith = "ddg",
        encryptedPrivateKey = "AAAA",
        publicKey = RsaJwk(n = "n", e = "AQAB"),
    )

    @Test
    fun whenKeyIsWrappedForCurrentCredentialThenReturnItBase64UrlEncoded() {
        stubSignedIn()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Result.Success(listOf(ddgKey)))
        whenever(protectedKeyUnwrapper.unwrap(ddgKey)).thenReturn(Result.Success(byteArrayOf(-5, -16, 0)))

        val result = provider.privateKey()

        // base64url of {0xFB, 0xF0, 0x00}, unpadded — "+" and "/" would appear in standard base64.
        assertEquals(AccountInfoPrivateKeyResult.Available("-_AA"), result)
    }

    @Test
    fun whenNotSignedInThenErrorWithoutFetching() {
        whenever(syncStore.token).thenReturn(null)

        assertTrue(provider.privateKey() is AccountInfoPrivateKeyResult.Unavailable)
        verify(syncApi, never()).getProtectedKeys(any())
    }

    @Test
    fun whenAccountInfoKeyExistsForAnotherCredentialThenTypedOutcomeIsNoWrapForOurCredential() {
        stubSignedIn()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Result.Success(listOf(ddgKey.copy(encryptedWith = "3party"))))

        val result = provider.privateKey()

        assertEquals(
            AccountInfoPrivateKeyResult.Unavailable(AccountInfoKeyUnavailableReason.NO_WRAP_FOR_OUR_CREDENTIAL),
            result,
        )
    }

    @Test
    fun whenNoAccountInfoKeyExistsThenTypedOutcomeIsNoKeyOnServer() {
        stubSignedIn()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Result.Success(emptyList()))

        val result = provider.privateKey()

        assertEquals(
            AccountInfoPrivateKeyResult.Unavailable(AccountInfoKeyUnavailableReason.NO_KEY_ON_SERVER),
            result,
        )
    }

    @Test
    fun whenKeysFetchIsRateLimitedThenTypedOutcomeIsRateLimited() {
        stubSignedIn()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Result.Error(code = 429))

        val result = provider.privateKey()

        assertEquals(
            AccountInfoPrivateKeyResult.Unavailable(AccountInfoKeyUnavailableReason.RATE_LIMITED),
            result,
        )
    }

    @Test
    fun whenUnwrapFailsThenTypedOutcomeIsUnwrapFailed() {
        stubSignedIn()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Result.Success(listOf(ddgKey)))
        whenever(protectedKeyUnwrapper.unwrap(ddgKey)).thenReturn(Result.Error(reason = "no account secret key"))

        assertEquals(
            AccountInfoPrivateKeyResult.Unavailable(AccountInfoKeyUnavailableReason.UNWRAP_FAILED),
            provider.privateKey(),
        )
    }

    @Test
    fun whenCalledTwiceForSameAccountThenWrappedKeyFetchedOnceButUnwrappedEachCall() {
        stubSignedIn()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Result.Success(listOf(ddgKey)))
        whenever(protectedKeyUnwrapper.unwrap(ddgKey)).thenReturn(Result.Success(byteArrayOf(-5, -16, 0)))

        val first = provider.privateKey()
        val second = provider.privateKey()

        assertEquals(AccountInfoPrivateKeyResult.Available("-_AA"), first)
        assertEquals(AccountInfoPrivateKeyResult.Available("-_AA"), second)
        // the wrapped entry is cached (one network fetch), but we unwrap per call so the plaintext key is never cached
        verify(syncApi, times(1)).getProtectedKeys(token)
        verify(protectedKeyUnwrapper, times(2)).unwrap(ddgKey)
    }

    @Test
    fun whenFetchFailsThenNotCachedAndRetriedNextCall() {
        stubSignedIn()
        whenever(syncApi.getProtectedKeys(token))
            .thenReturn(Result.Error(code = 418, reason = ""))
            .thenReturn(Result.Success(listOf(ddgKey)))
        whenever(protectedKeyUnwrapper.unwrap(ddgKey)).thenReturn(Result.Success(byteArrayOf(-5, -16, 0)))

        val first = provider.privateKey()
        val second = provider.privateKey()

        assertTrue(first is AccountInfoPrivateKeyResult.Unavailable)
        assertEquals(AccountInfoPrivateKeyResult.Available("-_AA"), second)
        verify(syncApi, times(2)).getProtectedKeys(token)
    }

    @Test
    fun whenSignedOutAfterCachingThenCacheClearedAndErrorOnNextCall() {
        stubSignedIn()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Result.Success(listOf(ddgKey)))
        whenever(protectedKeyUnwrapper.unwrap(ddgKey)).thenReturn(Result.Success(byteArrayOf(-5, -16, 0)))
        provider.privateKey()

        whenever(syncStore.token).thenReturn(null)

        assertTrue(provider.privateKey() is AccountInfoPrivateKeyResult.Unavailable)
    }

    private fun stubSignedIn() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.credentialId).thenReturn("ddg")
    }
}
