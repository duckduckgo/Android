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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.TestSyncFixtures.primaryKey
import com.duckduckgo.sync.TestSyncFixtures.secretKey
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.validLoginKeys
import com.duckduckgo.sync.crypto.EncryptBytesResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.RsaKeyPair
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.SyncStore
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class ProtectedKeyManagerTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val syncJweCrypto: SyncJweCrypto = mock()
    private val nativeLib: SyncLib = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    private lateinit var manager: ProtectedKeyManager

    @Before
    fun before() {
        manager = RealProtectedKeyManager(syncStore, syncApi, syncJweCrypto, nativeLib, syncFeature)
    }

    @Test
    fun whenFlagOffThenCreateReturnsError() {
        val result = manager.create("ai_chats")
        assertTrue(result is Error)
    }

    @Test
    fun whenNotSignedInThenCreateReturnsError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(null)

        val result = manager.create("ai_chats")
        assertTrue(result is Error)
    }

    @Test
    fun whenCreateSucceedsThenPostsKeyToServer() {
        // ddg-side `encrypted_private_key` is libsodium-secretbox(privateKey, secretKey) per
        // Encryption Algorithms TD §"How do we encrypt KEKs using MEKs" (Asana 1214802412121967).
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(nativeLib.prepareForLogin(primaryKey)).thenReturn(validLoginKeys)
        whenever(syncJweCrypto.generateRsaKeyPair()).thenReturn(RsaKeyPair("pubKey", "cHJpdktleQ"))
        whenever(syncJweCrypto.extractJwkComponents(anyString())).thenReturn("modulus" to "AQAB")
        whenever(nativeLib.encryptData(any<ByteArray>(), eq(secretKey))).thenReturn(EncryptBytesResult(0, "encrypted".toByteArray()))
        // Happy path: server's response contains our entry (we wrote it).
        val serverKey = ProtectedKeyEntry(
            kid = "server-kid",
            purpose = "ai_chats",
            encryptedWith = "ddg",
            encryptedPrivateKey = "encrypted",
            publicKey = RsaJwk(n = "modulus", e = "AQAB"),
        )
        whenever(syncApi.setProtectedKeyIfAbsent(eq(token), eq("ai_chats"), any())).thenReturn(Success(listOf(serverKey)))

        val result = manager.create("ai_chats")

        assertTrue(result is Success)
        // create() returns the server's authoritative entry, not the local one.
        assertEquals(serverKey, (result as Success).data)
        verify(syncApi).setProtectedKeyIfAbsent(
            eq(token),
            eq("ai_chats"),
            check { sent ->
                assertEquals(1, sent.size)
                val first = sent.first()
                assertEquals("ai_chats", first.purpose)
                assertEquals("ddg", first.encryptedWith)
                assertEquals(RsaJwk(n = "modulus", e = "AQAB"), first.publicKey)
            },
        )
    }

    @Test
    fun whenSetProtectedKeyIfAbsentReturnsExistingServerEntryThenCreateReturnsServerEntry() {
        // Race-loser: server returns a different kid for the same (purpose, encrypted_with).
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(nativeLib.prepareForLogin(primaryKey)).thenReturn(validLoginKeys)
        whenever(syncJweCrypto.generateRsaKeyPair()).thenReturn(RsaKeyPair("pubKey", "cHJpdktleQ"))
        whenever(syncJweCrypto.extractJwkComponents(anyString())).thenReturn("modulus" to "AQAB")
        whenever(nativeLib.encryptData(any<ByteArray>(), eq(secretKey))).thenReturn(EncryptBytesResult(0, "encrypted".toByteArray()))
        val serverKey = ProtectedKeyEntry(
            kid = "kid-from-server",
            purpose = "ai_chats",
            encryptedWith = "ddg",
            encryptedPrivateKey = "server-jwe",
            publicKey = RsaJwk(n = "server-modulus", e = "AQAB"),
        )
        whenever(syncApi.setProtectedKeyIfAbsent(eq(token), eq("ai_chats"), any())).thenReturn(Success(listOf(serverKey)))

        val result = manager.create("ai_chats")

        assertTrue(result is Success)
        assertEquals(serverKey, (result as Success).data)
    }

    @Test
    fun whenServerResponseMissingPurposeEntryThenError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(nativeLib.prepareForLogin(primaryKey)).thenReturn(validLoginKeys)
        whenever(syncJweCrypto.generateRsaKeyPair()).thenReturn(RsaKeyPair("pubKey", "cHJpdktleQ"))
        whenever(syncJweCrypto.extractJwkComponents(anyString())).thenReturn("modulus" to "AQAB")
        whenever(nativeLib.encryptData(any<ByteArray>(), eq(secretKey))).thenReturn(EncryptBytesResult(0, "encrypted".toByteArray()))
        // Server returns a list that doesn't include an entry for our (purpose, ddg) slot.
        whenever(syncApi.setProtectedKeyIfAbsent(eq(token), eq("ai_chats"), any())).thenReturn(Success(emptyList()))

        val result = manager.create("ai_chats")

        assertTrue(result is Error)
    }

    @Test
    fun whenSetProtectedKeyIfAbsentFailsThenError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(nativeLib.prepareForLogin(primaryKey)).thenReturn(validLoginKeys)
        whenever(syncJweCrypto.generateRsaKeyPair()).thenReturn(RsaKeyPair("pubKey", "cHJpdktleQ"))
        whenever(syncJweCrypto.extractJwkComponents(anyString())).thenReturn("modulus" to "AQAB")
        whenever(nativeLib.encryptData(any<ByteArray>(), eq(secretKey))).thenReturn(EncryptBytesResult(0, "encrypted".toByteArray()))
        whenever(syncApi.setProtectedKeyIfAbsent(eq(token), eq("ai_chats"), any())).thenReturn(
            Error(code = 500, reason = "server error"),
        )

        val result = manager.create("ai_chats")

        assertTrue(result is Error)
    }

    // ---- Wire-shape pinning ----

    @Test
    fun whenProtectedKeyEntrySerializedThenSnakeCaseFieldNamesMatchSpec() {
        // Pins the ProtectedKeyEntry wire shape used in both POST /sync/keys/.../set-if-absent
        // and inside CreateAccessCredentialRequest.keys.
        val entry = ProtectedKeyEntry(
            kid = "k1",
            purpose = "ai_chats",
            encryptedWith = "ddg",
            encryptedPrivateKey = "jwe",
            publicKey = RsaJwk(n = "mod", e = "AQAB"),
        )
        val json = Moshi.Builder().build()
            .adapter(ProtectedKeyEntry::class.java)
            .toJson(entry)
        val parsed = JSONObject(json)
        assertEquals("k1", parsed.getString("kid"))
        assertEquals("ai_chats", parsed.getString("purpose"))
        assertEquals("ddg", parsed.getString("encrypted_with"))
        assertEquals("jwe", parsed.getString("encrypted_private_key"))
        assertTrue("public_key missing", parsed.has("public_key"))
        assertFalse("kotlin field name leaked", parsed.has("encryptedWith"))
        assertFalse("kotlin field name leaked", parsed.has("encryptedPrivateKey"))
        assertFalse("kotlin field name leaked", parsed.has("publicKey"))
    }
}
