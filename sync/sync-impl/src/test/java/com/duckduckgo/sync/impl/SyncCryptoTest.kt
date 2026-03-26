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
import com.duckduckgo.sync.crypto.DecryptBytesResult
import com.duckduckgo.sync.crypto.DecryptResult
import com.duckduckgo.sync.crypto.EncryptBytesResult
import com.duckduckgo.sync.crypto.EncryptResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.error.SyncOperationErrorRecorder
import com.duckduckgo.sync.store.SyncStore
import com.duckduckgo.sync.store.model.SyncOperationErrorType.DATA_DECRYPT
import com.duckduckgo.sync.store.model.SyncOperationErrorType.DATA_ENCRYPT
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertArrayEquals
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
    private val recorder: SyncOperationErrorRecorder = mock()

    private lateinit var syncCrypto: SyncCrypto

    @Before
    fun setup() {
        syncCrypto = RealSyncCrypto(nativeLib, syncStore, recorder)
    }

    // String-based encrypt/decrypt tests

    @Test(expected = java.lang.Exception::class)
    fun whenEncryptStringFailsThenExceptionThrown() {
        whenever(nativeLib.encryptData(any<String>(), any())).thenReturn(EncryptResult(1, "not encrypted"))

        syncCrypto.encrypt("something")

        verify(recorder).record(DATA_ENCRYPT)
    }

    @Test
    fun whenEncryptStringSucceedsThenResultIsEncrypted() {
        whenever(nativeLib.encryptData(any<String>(), any())).thenReturn(EncryptResult(0, "encrypted"))

        val result = syncCrypto.encrypt("something")

        verifyNoInteractions(recorder)

        assertFalse(result.isEmpty())
    }

    @Test(expected = java.lang.Exception::class)
    fun whenDecryptStringFailsThenExceptionThrown() {
        whenever(nativeLib.decryptData(any<String>(), any())).thenReturn(DecryptResult(1, "not decrypted"))

        syncCrypto.decrypt("something")

        verify(recorder).record(DATA_DECRYPT)
    }

    @Test
    fun whenDecryptStringSucceedsThenResultIsDecrypted() {
        whenever(nativeLib.decryptData(any<String>(), any())).thenReturn(DecryptResult(0, "decrypted"))

        val result = syncCrypto.decrypt("something")

        verifyNoInteractions(recorder)

        assertFalse(result.isEmpty())
    }

    @Test
    fun whenStringDataToDecryptIsEmptyThenResultIsEmpty() {
        val result = syncCrypto.decrypt("")

        verifyNoInteractions(recorder)

        assertTrue(result.isEmpty())
    }

    // ByteArray-based encrypt/decrypt tests

    @Test(expected = java.lang.Exception::class)
    fun whenEncryptByteArrayFailsThenExceptionThrown() {
        whenever(nativeLib.encryptData(any<ByteArray>(), any())).thenReturn(EncryptBytesResult(1, byteArrayOf()))

        syncCrypto.encrypt("something".toByteArray())

        verify(recorder).record(DATA_ENCRYPT)
    }

    @Test
    fun whenEncryptByteArraySucceedsThenResultIsEncrypted() {
        val encryptedBytes = byteArrayOf(1, 2, 3)
        whenever(nativeLib.encryptData(any<ByteArray>(), any())).thenReturn(EncryptBytesResult(0, encryptedBytes))

        val result = syncCrypto.encrypt("something".toByteArray())

        verifyNoInteractions(recorder)

        assertArrayEquals(encryptedBytes, result)
    }

    @Test(expected = java.lang.Exception::class)
    fun whenDecryptByteArrayFailsThenExceptionThrown() {
        whenever(nativeLib.decryptData(any<ByteArray>(), any())).thenReturn(DecryptBytesResult(1, byteArrayOf()))

        syncCrypto.decrypt(byteArrayOf(1, 2, 3))

        verify(recorder).record(DATA_DECRYPT)
    }

    @Test
    fun whenDecryptByteArraySucceedsThenResultIsDecrypted() {
        val decryptedBytes = byteArrayOf(4, 5, 6)
        whenever(nativeLib.decryptData(any<ByteArray>(), any())).thenReturn(DecryptBytesResult(0, decryptedBytes))

        val result = syncCrypto.decrypt(byteArrayOf(1, 2, 3))

        verifyNoInteractions(recorder)

        assertArrayEquals(decryptedBytes, result)
    }

    @Test
    fun whenByteArrayDataToDecryptIsEmptyThenResultIsEmpty() {
        val result = syncCrypto.decrypt(byteArrayOf())

        verifyNoInteractions(recorder)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenByteArrayDataToEncryptIsEmptyThenResultIsEmpty() {
        val result = syncCrypto.encrypt(byteArrayOf())

        verifyNoInteractions(recorder)

        assertTrue(result.isEmpty())
    }
}
