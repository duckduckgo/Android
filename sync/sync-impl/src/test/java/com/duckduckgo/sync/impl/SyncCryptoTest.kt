/*
 * Copyright (c) 2023 DuckDuckGo
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

import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.crypto.DecryptResult
import com.duckduckgo.sync.crypto.EncryptResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.SyncStore
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SyncCryptoTest {

    private val nativeLib: SyncLib = mock()
    private val syncStore: SyncStore = mock()
    private val syncPixels: SyncPixels = mock()

    private lateinit var syncCrypto: SyncCrypto

    @Before
    fun setup() {
        syncCrypto = RealSyncCrypto(nativeLib, syncStore, syncPixels)
    }

    @Test
    fun whenEncryptFailsThenPixelIsSentAndResultIsEmpty() {
        whenever(nativeLib.encryptData(any(), any())).thenReturn(EncryptResult(1, "not encrypted"))

        val result = syncCrypto.encrypt("something")

        verify(syncPixels).fireEncryptFailurePixel()

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenEncryptSucceedsThenPixelIsNotSentAndResultIsEncrypted() {
        whenever(nativeLib.encryptData(any(), any())).thenReturn(EncryptResult(0L, "not encrypted"))

        val result = syncCrypto.encrypt("something")

        verifyNoInteractions(syncPixels)

        assertFalse(result.isEmpty())
    }

    @Test
    fun whenDecryptFailsThenPixelIsSentAndResultIsEmpty() {
        whenever(nativeLib.decryptData(any(), any())).thenReturn(DecryptResult(1, "not decrypted"))

        val result = syncCrypto.decrypt("something")

        verify(syncPixels).fireDecryptFailurePixel()

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenDecryptSucceedsThenPixelIsNotSentAndResultIsDecrypted() {
        whenever(nativeLib.decryptData(any(), any())).thenReturn(DecryptResult(0L, "not decrypted"))

        val result = syncCrypto.decrypt("something")

        verifyNoInteractions(syncPixels)

        assertFalse(result.isEmpty())
    }

    @Test
    fun whenDataToDecryptIsEmptyThenPixelIsNotSentAndResultIsEmpty() {
        val result = syncCrypto.decrypt("")

        verifyNoInteractions(syncPixels)

        assertTrue(result.isEmpty())
    }
}
