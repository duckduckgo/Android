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
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.AccountInfoPublicKey
import com.duckduckgo.sync.store.SyncStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DeviceInfoEncryptorTest {

    private val syncStore: SyncStore = mock()
    private val syncJweCrypto: SyncJweCrypto = mock()
    private val encryptor = RealDeviceInfoEncryptor(syncStore, syncJweCrypto)

    @Test
    fun whenNoCachedAccountInfoKeyThenReturnError() {
        whenever(syncStore.accountInfoPublicKey).thenReturn(null)

        val result = encryptor.encrypt("My Phone", "phone")

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenKeyPresentThenEncryptsNameTypeJsonWithKid() {
        whenever(syncStore.accountInfoPublicKey).thenReturn(AccountInfoPublicKey(keyId = "kid-1", modulus = "n", exponent = "e"))
        whenever(syncJweCrypto.rsaSpkiFromJwkComponents("n", "e")).thenReturn("spki")
        whenever(syncJweCrypto.jweEncryptRsaOaep(any(), eq("spki"), eq("kid-1"))).thenReturn("header.enckey.iv.ct.tag")

        val result = encryptor.encrypt("My Phone", "phone")

        assertEquals(Result.Success("header.enckey.iv.ct.tag"), result)
        argumentCaptor<ByteArray>().apply {
            verify(syncJweCrypto).jweEncryptRsaOaep(capture(), eq("spki"), eq("kid-1"))
            val json = JSONObject(String(firstValue, Charsets.UTF_8))
            assertEquals("My Phone", json.getString("name"))
            assertEquals("phone", json.getString("type"))
        }
    }

    @Test
    fun whenEncryptedBlobExceedsMaxLengthThenReturnError() {
        whenever(syncStore.accountInfoPublicKey).thenReturn(AccountInfoPublicKey(keyId = "kid-1", modulus = "n", exponent = "e"))
        whenever(syncJweCrypto.rsaSpkiFromJwkComponents("n", "e")).thenReturn("spki")
        whenever(syncJweCrypto.jweEncryptRsaOaep(any(), eq("spki"), eq("kid-1"))).thenReturn("x".repeat(2001))

        val result = encryptor.encrypt("My Phone", "phone")

        assertTrue(result is Result.Error)
    }
}
