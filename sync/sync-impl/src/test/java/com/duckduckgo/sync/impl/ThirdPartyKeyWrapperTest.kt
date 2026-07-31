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
import com.duckduckgo.sync.store.ScopedPassword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ThirdPartyKeyWrapperTest {

    private val syncJweCrypto: SyncJweCrypto = mock()
    private val wrapper = RealThirdPartyKeyWrapper(syncJweCrypto)

    private val minted = MintedProtectedKey(
        entry = ProtectedKeyEntry(
            kid = "kid-1",
            purpose = "account_info",
            encryptedWith = "ddg",
            encryptedPrivateKey = "ddg-wrapped",
            publicKey = RsaJwk(n = "modulus", e = "AQAB"),
        ),
        rawPrivateKeyBytes = "priv".toByteArray(),
    )

    @Test
    fun whenWrapSucceedsThenReturns3partyEntrySharingKidAndPublicKey() {
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweEncryptSymmetric(any(), any(), anyOrNull())).thenReturn("3party-wrapped")

        val result = wrapper.wrap(minted, "account_info", ScopedPassword("c3BSYXc="), "user-id") as Result.Success

        val entry = result.data
        assertEquals("kid-1", entry.kid)
        assertEquals("account_info", entry.purpose)
        assertEquals("3party", entry.encryptedWith)
        assertEquals("3party-wrapped", entry.encryptedPrivateKey)
        assertEquals(RsaJwk(n = "modulus", e = "AQAB"), entry.publicKey)
    }

    @Test
    fun whenMekDerivationFailsThenReturnsError() {
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenThrow(RuntimeException())

        val result = wrapper.wrap(minted, "account_info", ScopedPassword("c3BSYXc="), "user-id")

        assertTrue(result is Result.Error)
    }

    @Test
    fun whenEncryptionFailsThenReturnsError() {
        whenever(syncJweCrypto.hkdfSha256SingleBlock(any(), any(), any(), any())).thenReturn(ByteArray(32))
        whenever(syncJweCrypto.jweEncryptSymmetric(any(), any(), anyOrNull())).thenThrow(RuntimeException())

        val result = wrapper.wrap(minted, "account_info", ScopedPassword("c3BSYXc="), "user-id")

        assertTrue(result is Result.Error)
    }
}
