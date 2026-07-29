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
import com.duckduckgo.sync.TestSyncFixtures.secretKey
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.userId
import com.duckduckgo.sync.crypto.EncryptBytesResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.RsaKeyPair
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.ScopedPassword
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AccountInfoKeyManagerTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val syncJweCrypto: SyncJweCrypto = mock()
    private val nativeLib: SyncLib = mock()

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var manager: AccountInfoKeyManager

    @Before
    fun before() {
        manager = RealAccountInfoKeyManager(
            syncStore = syncStore,
            syncApi = syncApi,
            syncJweCrypto = syncJweCrypto,
            nativeLib = nativeLib,
            thirdPartyKeyWrapper = RealThirdPartyKeyWrapper(syncJweCrypto),
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
        configureForSuccessfulKeypairMint()
    }

    private fun configureForSuccessfulKeypairMint() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncJweCrypto.generateRsaKeyPair(any())).thenReturn(RsaKeyPair("pubKey", "cHJpdktleQ"))
        whenever(syncJweCrypto.extractJwkComponents(anyString())).thenReturn("modulus" to "AQAB")
        whenever(nativeLib.encryptData(any<ByteArray>(), eq(secretKey)))
            .thenReturn(EncryptBytesResult(0, "ddg_wrapped".toByteArray()))
    }

    private fun configureForNotSignedIn() {
        whenever(syncStore.token).thenReturn(null)
    }

    private fun configureForMissingAccountSecretKey() {
        whenever(syncStore.secretKey).thenReturn(null)
    }

    private fun configureForKeypairMintFailure() {
        whenever(syncJweCrypto.generateRsaKeyPair(any())).thenThrow(RuntimeException("boom"))
    }

    private fun configureForSetIfAbsentFailure() {
        whenever(syncApi.setKeysIfAbsent(eq(token), eq("account_info"), any())).thenReturn(Error(code = 500, reason = "server error"))
    }

    @Test
    fun whenNotSignedInThenReturnsError() = runTest {
        configureForNotSignedIn()

        val result = manager.ensureKeyRegistered()

        assertTrue(result is Error)
    }

    @Test
    fun whenNoSecretKeyThenReturnsError() = runTest {
        configureForMissingAccountSecretKey()

        val result = manager.ensureKeyRegistered()

        assertTrue(result is Error)
    }

    @Test
    fun whenNoScopedPasswordThenOnlyDdgWrapIsSent() = runTest {
        whenever(syncStore.scopedPassword).thenReturn(null)
        whenever(syncApi.setKeysIfAbsent(eq(token), eq("account_info"), any())).thenReturn(Success(SetKeysIfAbsentResult.Created))

        val result = manager.ensureKeyRegistered() as Success

        assertEquals(RsaJwk(n = "modulus", e = "AQAB"), result.data.publicKey)
        assertTrue(result.data.created)
        assertEquals(1, result.data.wrapsSent)
        verify(syncJweCrypto).generateRsaKeyPair(3072)
        verify(syncApi).setKeysIfAbsent(
            eq(token),
            eq("account_info"),
            check { keys ->
                assertEquals(1, keys.size)
                assertEquals("ddg", keys.first().encryptedWith)
                assertEquals("account_info", keys.first().purpose)
            },
        )
    }

    @Test
    fun whenScopedPasswordPresentThenBothWrapsAreSentSharingOneKid() = runTest {
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword("c3BSYXc="))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweEncryptSymmetric(any(), any(), anyOrNull())).thenReturn("3party_wrapped")
        whenever(syncApi.setKeysIfAbsent(eq(token), eq("account_info"), any())).thenReturn(Success(SetKeysIfAbsentResult.Created))

        val result = manager.ensureKeyRegistered()

        assertTrue(result is Success)
        assertEquals(2, (result as Success).data.wrapsSent)
        verify(syncApi).setKeysIfAbsent(
            eq(token),
            eq("account_info"),
            check { keys ->
                assertEquals(2, keys.size)
                assertEquals(1, keys.map { it.kid }.toSet().size)
                assertEquals(setOf("ddg", "3party"), keys.map { it.encryptedWith }.toSet())
            },
        )
    }

    @Test
    fun whenSetIfAbsentCreatedThenReturnsCreatedTrueWithOwnPublicKey() = runTest {
        whenever(syncStore.scopedPassword).thenReturn(null)
        whenever(syncApi.setKeysIfAbsent(eq(token), eq("account_info"), any())).thenReturn(Success(SetKeysIfAbsentResult.Created))

        val result = manager.ensureKeyRegistered() as Success

        assertTrue(result.data.created)
        assertEquals(1, result.data.wrapsSent)
        assertEquals(RsaJwk(n = "modulus", e = "AQAB"), result.data.publicKey)
    }

    @Test
    fun whenSetIfAbsentExistingThenAdoptsReturnedKeyAndReturnsCreatedFalse() = runTest {
        whenever(syncStore.scopedPassword).thenReturn(null)
        whenever(syncApi.setKeysIfAbsent(eq(token), eq("account_info"), any()))
            .thenReturn(Success(SetKeysIfAbsentResult.Existing(kid = "other-kid", publicKey = RsaJwk(n = "other-mod", e = "AQAB"))))

        val result = manager.ensureKeyRegistered() as Success

        assertEquals("other-kid", result.data.kid)
        assertEquals(RsaJwk(n = "other-mod", e = "AQAB"), result.data.publicKey)
        assertTrue(!result.data.created)
    }

    @Test
    fun whenSetIfAbsentFailsThenReturnsError() = runTest {
        configureForSetIfAbsentFailure()

        val result = manager.ensureKeyRegistered()

        assertTrue(result is Error)
    }

    @Test
    fun whenGenerateRsaKeyPairFailsThenReturnsErrorBeforeCallingServer() = runTest {
        configureForKeypairMintFailure()

        val result = manager.ensureKeyRegistered()

        assertTrue(result is Error)
        verify(syncApi, never()).setKeysIfAbsent(anyString(), anyString(), any())
    }

    @Test
    fun whenNoUserIdThenScopedPasswordIsIgnoredAndOnlyDdgWrapSent() = runTest {
        // userId is required to derive the 3party MEK; if it's missing, skip the 3party wrap rather than fail the whole registration.
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword("c3BSYXc="))
        whenever(syncStore.userId).thenReturn(null)
        whenever(syncApi.setKeysIfAbsent(eq(token), eq("account_info"), any())).thenReturn(Success(SetKeysIfAbsentResult.Created))

        val result = manager.ensureKeyRegistered() as Success

        assertEquals(1, result.data.wrapsSent)
    }
}
