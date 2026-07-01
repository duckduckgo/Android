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
import com.duckduckgo.sync.crypto.DecryptResult
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DeviceFieldDecryptorTest {

    private val syncStore: SyncStore = mock()
    private val syncJweCrypto: SyncJweCrypto = mock()
    private val nativeLib: SyncLib = mock()

    private lateinit var decryptor: DeviceFieldDecryptor

    // Valid standard-base64 string so hkdfDeriveBytes' inner Base64.decode doesn't throw.
    private val spBase64 = "AAECAwQFBgcICQoLDA0ODw=="
    private val userId = "user-42"
    private val primaryKey = "primaryKeyBase64"

    @Before
    fun before() {
        decryptor = RealDeviceFieldDecryptor(syncStore, syncJweCrypto, nativeLib)
    }

    // ---------- 3party path ----------

    @Test
    fun whenThirdPartyEntryWithValidSpAndUserIdThenDecryptsNameAndType() {
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), eq("Main Key"), eq(32))).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("ENC_NAME"), any())).thenReturn("Chrome/148.0.0.0".toByteArray())
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("ENC_TYPE"), any())).thenReturn("Browser".toByteArray())

        val result = decryptor.decrypt(
            DeviceV2(deviceId = "d1", deviceName = "ENC_NAME", deviceType = "ENC_TYPE", credentialId = "3party"),
        )

        assertEquals(Success(DecryptedDevice("d1", "Chrome/148.0.0.0", "Browser")), result)
    }

    @Test
    fun whenThirdPartyEntryHasNoTypeThenReturnsNullType() {
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), eq("Main Key"), eq(32))).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("ENC_NAME"), any())).thenReturn("Chrome/148.0.0.0".toByteArray())

        val result = decryptor.decrypt(
            DeviceV2(deviceId = "d1", deviceName = "ENC_NAME", deviceType = null, credentialId = "3party"),
        )

        assertEquals(Success(DecryptedDevice("d1", "Chrome/148.0.0.0", null)), result)
    }

    @Test
    fun whenThirdPartyEntryHasEmptyTypeThenReturnsNullType() {
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), eq("Main Key"), eq(32))).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("ENC_NAME"), any())).thenReturn("Chrome/148.0.0.0".toByteArray())

        val result = decryptor.decrypt(
            DeviceV2(deviceId = "d1", deviceName = "ENC_NAME", deviceType = "", credentialId = "3party"),
        )

        assertEquals(Success(DecryptedDevice("d1", "Chrome/148.0.0.0", null)), result)
    }

    @Test
    fun whenThirdPartyEntryAndScopedPasswordMissingThenError() {
        whenever(syncStore.scopedPassword).thenReturn(null)
        whenever(syncStore.userId).thenReturn(userId)

        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = "ENC", credentialId = "3party"))

        assertTrue(result is Error)
    }

    @Test
    fun whenThirdPartyEntryAndUserIdMissingThenError() {
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(null)

        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = "ENC", credentialId = "3party"))

        assertTrue(result is Error)
    }

    @Test
    fun whenThirdPartyMekDerivationThrowsThenError() {
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenThrow(RuntimeException("hkdf boom"))

        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = "ENC", credentialId = "3party"))

        assertTrue(result is Error)
    }

    @Test
    fun whenThirdPartyNameDecryptThrowsThenError() {
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("BAD"), any())).thenThrow(RuntimeException("bad envelope"))

        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = "BAD", credentialId = "3party"))

        assertTrue(result is Error)
    }

    @Test
    fun whenThirdPartyTypeDecryptThrowsThenError() {
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("ENC_NAME"), any())).thenReturn("Chrome".toByteArray())
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("BAD_TYPE"), any())).thenThrow(RuntimeException("bad type"))

        val result = decryptor.decrypt(
            DeviceV2(deviceId = "d1", deviceName = "ENC_NAME", deviceType = "BAD_TYPE", credentialId = "3party"),
        )

        assertTrue(result is Error)
    }

    @Test
    fun whenThirdPartyEntryHasNoNameThenError() {
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(spBase64))
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))

        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = null, credentialId = "3party"))

        assertTrue(result is Error)
    }

    // ---------- ddg path ----------

    @Test
    fun whenDdgEntryWithValidPrimaryKeyThenDecryptsNameAndType() {
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(nativeLib.decryptData(eq("ENC_NAME"), eq(primaryKey))).thenReturn(DecryptResult(0, "Pixel 7"))
        whenever(nativeLib.decryptData(eq("ENC_TYPE"), eq(primaryKey))).thenReturn(DecryptResult(0, "phone"))

        val result = decryptor.decrypt(
            DeviceV2(deviceId = "d1", deviceName = "ENC_NAME", deviceType = "ENC_TYPE", credentialId = "ddg"),
        )

        assertEquals(Success(DecryptedDevice("d1", "Pixel 7", "phone")), result)
    }

    @Test
    fun whenCredentialIdIsNullThenRoutesToDdgPath() {
        // null credential_id is treated as ddg per spec (legacy entries don't carry the field).
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(nativeLib.decryptData(eq("ENC_NAME"), eq(primaryKey))).thenReturn(DecryptResult(0, "Pixel 7"))

        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = "ENC_NAME", credentialId = null))

        assertEquals(Success(DecryptedDevice("d1", "Pixel 7", null)), result)
    }

    @Test
    fun whenDdgEntryAndPrimaryKeyMissingThenError() {
        whenever(syncStore.primaryKey).thenReturn(null)

        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = "ENC", credentialId = "ddg"))

        assertTrue(result is Error)
    }

    @Test
    fun whenDdgEntryAndPrimaryKeyIsEmptyThenError() {
        whenever(syncStore.primaryKey).thenReturn("")

        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = "ENC", credentialId = "ddg"))

        assertTrue(result is Error)
    }

    @Test
    fun whenDdgNameDecryptThrowsThenError() {
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(nativeLib.decryptData(eq("BAD"), eq(primaryKey))).thenThrow(RuntimeException("libsodium boom"))

        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = "BAD", credentialId = "ddg"))

        assertTrue(result is Error)
    }

    @Test
    fun whenDdgEntryHasNoNameThenError() {
        whenever(syncStore.primaryKey).thenReturn(primaryKey)

        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = null, credentialId = "ddg"))

        assertTrue(result is Error)
    }

    // ---------- generic ----------

    @Test
    fun whenEntryHasNoDeviceIdThenError() {
        val result = decryptor.decrypt(DeviceV2(deviceId = null, deviceName = "ENC", credentialId = "ddg"))

        assertTrue(result is Error)
    }

    @Test
    fun whenCredentialIdIsUnknownThenError() {
        val result = decryptor.decrypt(DeviceV2(deviceId = "d1", deviceName = "ENC", credentialId = "magic"))

        assertTrue(result is Error)
        // Sanity: didn't try to decrypt with either path.
        verify(nativeLib, never()).decryptData(any<String>(), any())
        verify(syncJweCrypto, never()).jweDecryptSymmetric(any(), any())
    }
}
