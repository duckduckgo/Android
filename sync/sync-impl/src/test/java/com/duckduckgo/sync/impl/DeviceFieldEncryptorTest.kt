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
import com.duckduckgo.sync.crypto.EncryptResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.ScopedPassword
import com.duckduckgo.sync.store.SyncStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DeviceFieldEncryptorTest {

    private val syncStore: SyncStore = mock()
    private val syncJweCrypto: SyncJweCrypto = mock()
    private val nativeLib: SyncLib = mock()

    private lateinit var encryptor: DeviceFieldEncryptor

    // Valid standard-base64 string so hkdfDeriveBytes' inner Base64.decode doesn't throw.
    private val spBase64 = "AAECAwQFBgcICQoLDA0ODw=="
    private val userId = "user-42"
    private val primaryKey = "primaryKeyBase64"

    @Before
    fun before() {
        encryptor = RealDeviceFieldEncryptor(syncStore, syncJweCrypto, nativeLib)
    }

    // ---------- 3party path ----------

    @Test
    fun whenThirdPartyWithValidSpAndUserIdThenEncryptsNameAndTypeUnderMek() {
        whenever(syncStore.credentialId).thenReturn("3party")
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), eq("Main Key"), eq(32))).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweEncryptSymmetric(any(), any(), anyOrNull())).thenAnswer {
            "enc-" + String(it.getArgument(0), Charsets.UTF_8)
        }

        val result = encryptor.encrypt("My Phone", "phone")

        assertEquals(Success(EncryptedDeviceFields(name = "enc-My Phone", type = "enc-phone")), result)
    }

    @Test
    fun whenThirdPartyAndScopedPasswordMissingThenError() {
        whenever(syncStore.credentialId).thenReturn("3party")
        whenever(syncStore.scopedPassword).thenReturn(null)
        whenever(syncStore.userId).thenReturn(userId)

        val result = encryptor.encrypt("My Phone", "phone")

        assertTrue(result is Error)
    }

    @Test
    fun whenThirdPartyAndUserIdMissingThenError() {
        whenever(syncStore.credentialId).thenReturn("3party")
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(null)

        val result = encryptor.encrypt("My Phone", "phone")

        assertTrue(result is Error)
    }

    @Test
    fun whenThirdPartyEncryptThrowsThenError() {
        whenever(syncStore.credentialId).thenReturn("3party")
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweEncryptSymmetric(any(), any(), anyOrNull())).thenThrow(RuntimeException("boom"))

        val result = encryptor.encrypt("My Phone", "phone")

        assertTrue(result is Error)
    }

    // ---------- ddg path ----------

    @Test
    fun whenDdgWithValidPrimaryKeyThenEncryptsNameAndType() {
        whenever(syncStore.credentialId).thenReturn("ddg")
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(nativeLib.encryptData(eq("My Phone"), eq(primaryKey))).thenReturn(EncryptResult(0, "enc-name"))
        whenever(nativeLib.encryptData(eq("phone"), eq(primaryKey))).thenReturn(EncryptResult(0, "enc-type"))

        val result = encryptor.encrypt("My Phone", "phone")

        assertEquals(Success(EncryptedDeviceFields(name = "enc-name", type = "enc-type")), result)
    }

    @Test
    fun whenCredentialIdIsNullThenRoutesToDdgPath() {
        whenever(syncStore.credentialId).thenReturn(null)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(nativeLib.encryptData(eq("My Phone"), eq(primaryKey))).thenReturn(EncryptResult(0, "enc-name"))
        whenever(nativeLib.encryptData(eq("phone"), eq(primaryKey))).thenReturn(EncryptResult(0, "enc-type"))

        val result = encryptor.encrypt("My Phone", "phone")

        assertEquals(Success(EncryptedDeviceFields(name = "enc-name", type = "enc-type")), result)
    }

    @Test
    fun whenDdgAndPrimaryKeyMissingThenError() {
        whenever(syncStore.credentialId).thenReturn("ddg")
        whenever(syncStore.primaryKey).thenReturn(null)

        val result = encryptor.encrypt("My Phone", "phone")

        assertTrue(result is Error)
    }

    @Test
    fun whenDdgAndPrimaryKeyIsEmptyThenError() {
        whenever(syncStore.credentialId).thenReturn("ddg")
        whenever(syncStore.primaryKey).thenReturn("")

        val result = encryptor.encrypt("My Phone", "phone")

        assertTrue(result is Error)
    }

    @Test
    fun whenDdgEncryptReturnsNonZeroResultThenError() {
        whenever(syncStore.credentialId).thenReturn("ddg")
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(nativeLib.encryptData(eq("My Phone"), eq(primaryKey))).thenReturn(EncryptResult(1, "not encrypted"))

        val result = encryptor.encrypt("My Phone", "phone")

        assertTrue(result is Error)
    }

    // ---------- generic ----------

    @Test
    fun whenCredentialIdIsUnknownThenError() {
        whenever(syncStore.credentialId).thenReturn("magic")

        val result = encryptor.encrypt("My Phone", "phone")

        assertTrue(result is Error)
        // Sanity: didn't try to encrypt with either path.
        verify(nativeLib, never()).encryptData(any<String>(), any())
        verify(syncJweCrypto, never()).jweEncryptSymmetric(any(), any(), anyOrNull())
    }
}
