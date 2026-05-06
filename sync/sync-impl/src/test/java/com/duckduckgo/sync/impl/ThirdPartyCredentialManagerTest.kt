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
import com.duckduckgo.sync.TestSyncFixtures.accountKeys
import com.duckduckgo.sync.TestSyncFixtures.primaryKey
import com.duckduckgo.sync.TestSyncFixtures.secretKey
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.userId
import com.duckduckgo.sync.TestSyncFixtures.validLoginKeys
import com.duckduckgo.sync.crypto.DecryptBytesResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.ScopedPassword
import com.duckduckgo.sync.store.SyncStore
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class ThirdPartyCredentialManagerTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val syncJweCrypto: SyncJweCrypto = mock()
    private val nativeLib: SyncLib = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    private lateinit var manager: ThirdPartyCredentialManager

    @Before
    fun before() {
        manager = RealThirdPartyCredentialManager(syncStore, syncApi, syncJweCrypto, nativeLib, syncFeature)
    }

    // ---- create() ----

    @Test
    fun whenFlagOffThenCreateReturnsError() {
        val result = manager.create()
        assertTrue(result is Error)
    }

    @Test
    fun whenNotSignedInThenCreateReturnsError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(null)

        val result = manager.create()
        assertTrue(result is Error)
    }

    @Test
    fun whenCredentialAlreadyExistsOnServerThenDecryptsAndStores() {
        // Server's `encryptedCredential` is decrypted via AES-GCM-JWE-dir(SP, DDG-MEK); the
        // resulting base64url SP is re-encoded as standard base64 for local storage. Fixture
        // chosen so wire ("_-__") and stored ("/+//") differ on every character.
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncApi.getAccessCredentials(token)).thenReturn(
            Success(listOf(AccessCredentialEntry(id = "3party", scope = "ai_chats", encryptedCredential = "encrypted_sp_from_server"))),
        )
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("encrypted_sp_from_server"), any()))
            .thenReturn("_-__".toByteArray(Charsets.UTF_8))

        val result = manager.create()

        assertEquals(Success(true), result)
        verify(syncStore).scopedPassword = ScopedPassword("/+//")
        verify(syncApi, times(0)).createAccessCredential(anyString(), anyString(), any())
    }

    @Test
    fun whenServerReturnsCredentialMissingEncryptedThenError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.getAccessCredentials(token)).thenReturn(
            Success(listOf(AccessCredentialEntry(id = "3party", scope = "ai_chats", encryptedCredential = null))),
        )

        val result = manager.create()

        assertTrue(result is Error)
        verify(syncStore, times(0)).scopedPassword = any()
    }

    @Test
    fun whenCreateSucceedsThenStoresScopedPassword() {
        primeCreate()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Success(emptyList()))

        val result = manager.create()

        assertEquals(Success(true), result)
        verify(syncStore).scopedPassword = ScopedPassword(primaryKey)
    }

    @Test
    fun whenCreateSucceedsThenCredentialHashedPasswordIsHkdfOfSpRawBytes() {
        // Regression guard: 3party `credential_hashed_password` MUST be HKDF-SHA-256(SP_raw,
        // salt=user_id_bytes, info="Password", 32) re-encoded as base64url (no padding). Per
        // Encryption Algorithms TD §"Hashed password derivation" (Asana 1214802412121967).
        primeCreate()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Success(emptyList()))
        // Use the real HKDF impl so we can compute the expected output.
        val realJweCrypto = com.duckduckgo.sync.impl.crypto.RealSyncJweCrypto()
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).doAnswer { invocation ->
            realJweCrypto.hkdfSha256SingleBlock(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
            )
        }
        val expected = run {
            val raw = java.util.Base64.getDecoder().decode(primaryKey)
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(userId.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val prk = mac.doFinal(raw)
            mac.init(javax.crypto.spec.SecretKeySpec(prk, "HmacSHA256"))
            mac.update("Password".toByteArray(Charsets.UTF_8))
            mac.update(0x01.toByte())
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal())
        }

        manager.create()

        verify(syncApi).createAccessCredential(
            eq(token),
            eq("3party"),
            check { request -> assertEquals(expected, request.credentialHashedPassword) },
        )
    }

    @Test
    fun whenExistingKeysOnServerThenReEncryptsForThirdParty() {
        primeCreate()
        val existingKey = ProtectedKeyEntry(
            kid = "k1",
            purpose = "ai_chats",
            encryptedWith = "ddg",
            encryptedPrivateKey = "AAAA",
            publicKey = RsaJwk(n = "mod", e = "AQAB"),
        )
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Success(listOf(existingKey)))
        whenever(nativeLib.decryptData(any<ByteArray>(), eq(secretKey))).thenReturn(DecryptBytesResult(0, "raw_key".toByteArray()))

        val result = manager.create()

        assertEquals(Success(true), result)
        verify(syncApi).createAccessCredential(
            eq(token),
            eq("3party"),
            check { request ->
                val keys = request.keys
                assertEquals(1, keys?.size)
                assertEquals("k1", keys?.first()?.kid)
                assertEquals("3party", keys?.first()?.encryptedWith)
                assertEquals(RsaJwk(n = "mod", e = "AQAB"), keys?.first()?.publicKey)
            },
        )
    }

    @Test
    fun whenCreateAccessCredentialApiFailsThenError() {
        primeCreate()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Success(emptyList()))
        whenever(syncApi.createAccessCredential(anyString(), anyString(), any())).thenReturn(
            Error(code = 500, reason = "server error"),
        )

        val result = manager.create()

        assertTrue(result is Error)
    }

    @Test
    fun whenCreate409ConflictAndCredentialNowPresentThenAdoptsAndStoresSp() {
        // Spec (Asana 1214702966683640, "Setting up usage of a new scope"): on conflict,
        // refetch and adopt the credential another device just created.
        primeCreate()
        // First getAccessCredentials call (initial adopt check): empty → take mint path.
        // Second call (after 409): credential now present → adopt and decrypt.
        whenever(syncApi.getAccessCredentials(token)).thenReturn(
            Success(emptyList()),
            Success(listOf(AccessCredentialEntry(id = "3party", scope = "ai_chats", encryptedCredential = "encrypted_sp_from_server"))),
        )
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Success(emptyList()))
        whenever(syncApi.createAccessCredential(anyString(), anyString(), any())).thenReturn(
            Error(code = 409, reason = "credential already exists"),
        )
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("encrypted_sp_from_server"), any()))
            .thenReturn("_-__".toByteArray(Charsets.UTF_8))

        val result = manager.create()

        assertEquals(Success(true), result)
        // Adopted SP from server is converted from wire base64url ("_-__") to stored standard base64 ("/+//").
        verify(syncStore).scopedPassword = ScopedPassword("/+//")
    }

    @Test
    fun whenCreate409ConflictButCredentialStillMissingThenError() {
        primeCreate()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Success(emptyList()))
        whenever(syncApi.createAccessCredential(anyString(), anyString(), any())).thenReturn(
            Error(code = 409, reason = "credential already exists"),
        )

        val result = manager.create()

        assertTrue(result is Error)
        verify(syncStore, times(0)).scopedPassword = any()
    }

    @Test
    fun whenGetProtectedKeysFailsThenAbortsBeforeCreatingCredential() {
        primeCreate()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Error(code = 500, reason = "getKeys failed"))

        val result = manager.create()

        assertTrue(result is Error)
        verify(syncApi, times(0)).createAccessCredential(anyString(), anyString(), any())
    }

    // ---- refresh() ----

    @Test
    fun whenFlagOffThenRefreshReturnsError() {
        val result = manager.refresh()
        assertTrue(result is Error)
    }

    @Test
    fun whenNotSignedInThenRefreshReturnsError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(null)

        val result = manager.refresh()
        assertTrue(result is Error)
    }

    @Test
    fun whenNoCredentialOnServerThenRefreshReturnsSuccessFalse() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.getAccessCredentials(token)).thenReturn(Success(emptyList()))

        val result = manager.refresh()

        assertEquals(Success(false), result)
        verify(syncStore, times(0)).scopedPassword = any()
    }

    @Test
    fun whenServerCredentialMissingEncryptedThenRefreshReturnsError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.getAccessCredentials(token)).thenReturn(
            Success(listOf(AccessCredentialEntry(id = "3party", encryptedCredential = null))),
        )

        val result = manager.refresh()

        assertTrue(result is Error)
        verify(syncStore, times(0)).scopedPassword = any()
    }

    @Test
    fun whenRefreshSucceedsThenDecryptsAndStoresSp() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncApi.getAccessCredentials(token)).thenReturn(
            Success(listOf(AccessCredentialEntry(id = "3party", encryptedCredential = "encrypted_sp_from_server"))),
        )
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("encrypted_sp_from_server"), any()))
            .thenReturn("_-__".toByteArray(Charsets.UTF_8))

        val result = manager.refresh()

        assertEquals(Success(true), result)
        verify(syncStore).scopedPassword = ScopedPassword("/+//")
    }

    @Test
    fun whenRefreshDecryptFailsThenError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncApi.getAccessCredentials(token)).thenReturn(
            Success(listOf(AccessCredentialEntry(id = "3party", encryptedCredential = "encrypted_sp"))),
        )
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("encrypted_sp"), any()))
            .thenThrow(RuntimeException("decrypt failed"))

        val result = manager.refresh()

        assertTrue(result is Error)
        verify(syncStore, times(0)).scopedPassword = any()
    }

    @Test
    fun whenRefreshGetAccessCredentialsFailsThenError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.getAccessCredentials(token)).thenReturn(Error(code = 500, reason = "boom"))

        val result = manager.refresh()

        assertTrue(result is Error)
    }

    // ---- getRecoveryCode() ----

    @Test
    fun whenFlagOffThenGetRecoveryCodeReturnsError() {
        val result = manager.getRecoveryCode()
        assertTrue(result is Error)
    }

    @Test
    fun whenNoScopedPasswordThenGetRecoveryCodeReturnsError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.scopedPassword).thenReturn(null)

        val result = manager.getRecoveryCode()
        assertTrue(result is Error)
    }

    @Test
    fun whenGetRecoveryCodeThenJsonEnvelopeMatchesSpec() {
        // SP stored as standard base64 "/+//" — wire form must be base64url "_-__" (no padding).
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword("/+//"))
        whenever(syncStore.userId).thenReturn(userId)

        val result = manager.getRecoveryCode() as Success<String>
        // qrCode is base64url(JSON); decode once to inspect the JSON envelope.
        val decodedJson = String(
            java.util.Base64.getUrlDecoder().decode(result.data),
            Charsets.UTF_8,
        )
        val outer = JSONObject(decodedJson)
        assertEquals(setOf("recovery"), outer.keys().asSequence().toSet())
        val recovery = outer.getJSONObject("recovery")
        assertEquals(userId, recovery.getString("user_id"))
        assertEquals("_-__", recovery.getString("secret"))
        assertEquals("3party", recovery.getString("cid"))
        assertEquals("2.0", recovery.getString("v"))
        assertEquals(setOf("user_id", "secret", "cid", "v"), recovery.keys().asSequence().toSet())
    }

    // ---- Wire-shape pinning ----

    @Test
    fun whenAccessCredentialEntryParsedFromBeJsonThenEncryptedBlobMappedFrom3PartyFieldName() {
        // BE returns the encrypted 3party blob under `encrypted_3party_credential`. Pin it so a
        // silent rename (e.g. to match the older TD shape `encrypted_credential`) breaks tests.
        val beJson = """{"access_credentials":[{"id":"ddg"},{"encrypted_3party_credential":"jwe","id":"3party","scope":"ai_chats"}]}"""

        val parsed = Moshi.Builder().build()
            .adapter(AccessCredentialsResponse::class.java)
            .fromJson(beJson)
        val threeParty = parsed?.accessCredentials?.find { it.id == "3party" }
        assertEquals("ai_chats", threeParty?.scope)
        assertEquals("jwe", threeParty?.encryptedCredential)
    }

    @Test
    fun whenCreateAccessCredentialPostedThenRequestJsonMatchesSpec() {
        primeCreate()
        whenever(syncApi.getProtectedKeys(token)).thenReturn(Success(emptyList()))

        manager.create()

        verify(syncApi).createAccessCredential(
            eq(token),
            eq("3party"),
            check { request ->
                val json = Moshi.Builder().build()
                    .adapter(CreateAccessCredentialRequest::class.java)
                    .toJson(request)
                val parsed = JSONObject(json)
                assertTrue("hashed_password missing", parsed.has("hashed_password"))
                assertTrue("credential_hashed_password missing", parsed.has("credential_hashed_password"))
                assertTrue("encrypted_3party_credential missing", parsed.has("encrypted_3party_credential"))
                org.junit.Assert.assertFalse("kotlin field name leaked", parsed.has("credentialHashedPassword"))
                org.junit.Assert.assertFalse("kotlin field name leaked", parsed.has("hashedPassword"))
                org.junit.Assert.assertFalse("kotlin field name leaked", parsed.has("encrypted3partyCredential"))
            },
        )
    }

    // ---- helpers ----

    private fun primeCreate() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(nativeLib.prepareForLogin(anyString())).thenReturn(validLoginKeys)
        whenever(nativeLib.generateAccountKeys(userId = eq(userId), password = anyString())).thenReturn(accountKeys)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweEncryptSymmetric(any(), any(), anyOrNull())).thenReturn("encrypted_sp")
        whenever(syncApi.getAccessCredentials(token)).thenReturn(Success(emptyList()))
        whenever(syncApi.createAccessCredential(anyString(), anyString(), any())).thenReturn(Success(true))
    }
}
