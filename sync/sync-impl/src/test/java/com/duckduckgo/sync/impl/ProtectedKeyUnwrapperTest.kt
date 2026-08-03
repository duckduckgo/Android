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
import com.duckduckgo.sync.crypto.DecryptBytesResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.ScopedPassword
import com.duckduckgo.sync.store.SyncStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ProtectedKeyUnwrapperTest {

    private val syncStore: SyncStore = mock()
    private val syncJweCrypto: SyncJweCrypto = mock()
    private val nativeLib: SyncLib = mock()
    private val unwrapper = RealProtectedKeyUnwrapper(syncStore, syncJweCrypto, nativeLib)

    private val scopedPasswordBase64 = "AAECAwQFBgcICQoLDA0ODw=="

    private val ddgKey = ProtectedKeyEntry(
        kid = "kid-1",
        purpose = "account_info",
        encryptedWith = "ddg",
        encryptedPrivateKey = "AAAA",
        publicKey = RsaJwk(n = "n", e = "AQAB"),
    )

    private val thirdPartyKey = ddgKey.copy(encryptedWith = "3party")

    @Test
    fun whenDdgKeyThenLibsodiumDecryptWithAccountSecretKey() {
        whenever(syncStore.secretKey).thenReturn("secretKey")
        whenever(nativeLib.decryptData(any<ByteArray>(), eq("secretKey"))).thenReturn(DecryptBytesResult(0, byteArrayOf(1, 2, 3)))

        val result = unwrapper.unwrap(ddgKey)

        assertArrayEquals(byteArrayOf(1, 2, 3), (result as Result.Success).data)
    }

    @Test
    fun whenDdgKeyAndNoAccountSecretKeyThenError() {
        whenever(syncStore.secretKey).thenReturn(null)

        assertTrue(unwrapper.unwrap(ddgKey) is Result.Error)
    }

    @Test
    fun whenDdgDecryptFailsThenError() {
        whenever(syncStore.secretKey).thenReturn("secretKey")
        whenever(nativeLib.decryptData(any<ByteArray>(), eq("secretKey"))).thenReturn(DecryptBytesResult(1, ByteArray(0)))

        assertTrue(unwrapper.unwrap(ddgKey) is Result.Error)
    }

    @Test
    fun whenThirdPartyKeyThenJweDecryptWithDerivedMainKey() {
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword(scopedPasswordBase64))
        whenever(syncStore.userId).thenReturn("user-42")
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), eq("Main Key"), eq(32))).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweDecryptSymmetric(eq("AAAA"), any())).thenReturn(byteArrayOf(4, 5, 6))

        val result = unwrapper.unwrap(thirdPartyKey)

        assertArrayEquals(byteArrayOf(4, 5, 6), (result as Result.Success).data)
    }

    @Test
    fun whenThirdPartyKeyAndNoScopedPasswordThenError() {
        whenever(syncStore.scopedPassword).thenReturn(null)
        whenever(syncStore.userId).thenReturn("user-42")

        assertTrue(unwrapper.unwrap(thirdPartyKey) is Result.Error)
    }

    @Test
    fun whenCredentialIsUnknownThenError() {
        assertTrue(unwrapper.unwrap(ddgKey.copy(encryptedWith = "magic")) is Result.Error)
    }
}
