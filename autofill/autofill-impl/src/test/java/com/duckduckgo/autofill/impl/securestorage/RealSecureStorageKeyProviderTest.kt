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

package com.duckduckgo.securestorage

import com.duckduckgo.autofill.impl.securestorage.RealSecureStorageKeyProvider
import com.duckduckgo.securestorage.impl.encryption.RandomBytesGenerator
import java.security.Key
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealSecureStorageKeyProviderTest {
    private lateinit var testee: RealSecureStorageKeyProvider
    private lateinit var secureStorageKeyRepository: FakeSecureStorageKeyRepository
    private lateinit var encryptionHelper: FakeEncryptionHelper
    private lateinit var secureStorageKeyGenerator: FakeSecureStorageKeyGenerator

    @Mock
    private lateinit var randomBytesGenerator: RandomBytesGenerator

    @Mock
    private lateinit var mockKey: Key

    private val testRandomBytes = "Zm9vYmFy".toByteArray()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        secureStorageKeyRepository = FakeSecureStorageKeyRepository(true)
        encryptionHelper = FakeEncryptionHelper(expectedEncryptedData, expectedEncryptedIv, expectedDecryptedData)
        secureStorageKeyGenerator = FakeSecureStorageKeyGenerator(mockKey)
        testee = RealSecureStorageKeyProvider(randomBytesGenerator, secureStorageKeyRepository, encryptionHelper, secureStorageKeyGenerator)
    }

    @Test
    fun whenCanAccessKeyStoreIsCheckedThenReturnRepositoryCanUseEncryption() {
        assertTrue(testee.canAccessKeyStore())
    }

    @Test
    fun whenNoL1KeySetThenGenerateAndStoreL1Key() {
        whenever(randomBytesGenerator.generateBytes(32)).thenReturn(testRandomBytes)
        assertNull("Initial state is incorrect since L1key is not null", secureStorageKeyRepository.l1Key)

        val result = testee.getl1Key()

        assertEquals(testRandomBytes, secureStorageKeyRepository.l1Key) // key is stored
        assertEquals(testRandomBytes, result) // returned value is correct
    }

    @Test
    fun whenL1KeySetThenReturnStoredL1Key() {
        secureStorageKeyRepository.l1Key = testRandomBytes

        val result = testee.getl1Key()

        assertEquals(testRandomBytes, result) // returned value is correct
    }

    @Test
    fun whenNoValueStoredInKeyRepositoryThenReturnKeyAndGenerateAndStoreKeyValues() {
        whenever(mockKey.encoded).thenReturn(testRandomBytes)
        whenever(randomBytesGenerator.generateBytes(32)).thenReturn(testRandomBytes)
        assertNull("Initial state is incorrect since password is not null", secureStorageKeyRepository.password)
        assertNull("Initial state is incorrect since passwordSalt is not null", secureStorageKeyRepository.passwordSalt)
        assertNull("Initial state is incorrect since encryptedL2Key is not null", secureStorageKeyRepository.encryptedL2Key)
        assertNull("Initial state is incorrect since encryptedL2KeyIV is not null", secureStorageKeyRepository.encryptedL2KeyIV)

        val result = testee.getl2Key()

        assertEquals(testRandomBytes, secureStorageKeyRepository.password) // key is stored
        assertEquals(testRandomBytes, secureStorageKeyRepository.passwordSalt) // key is stored
        assertEquals(expectedEncryptedData, secureStorageKeyRepository.encryptedL2Key!!.toByteString().base64()) // key is stored
        assertEquals(expectedEncryptedIv, secureStorageKeyRepository.encryptedL2KeyIV!!.toByteString().base64()) // key is stored
        assertEquals(mockKey, result)
    }

    @Test
    fun whenAllKeysStoredInKeyRepositoryThenUseKeysWhenConstructingL2Key() {
        secureStorageKeyRepository.password = testRandomBytes
        secureStorageKeyRepository.passwordSalt = testRandomBytes
        secureStorageKeyRepository.encryptedL2Key = testRandomBytes
        secureStorageKeyRepository.encryptedL2KeyIV = testRandomBytes

        val result = testee.getl2Key()

        assertEquals(mockKey, result)
    }

    companion object {
        private const val expectedEncryptedData: String = "ZXhwZWN0ZWRFbmNyeXB0ZWREYXRh"
        private const val expectedEncryptedIv: String = "ZXhwZWN0ZWRFbmNyeXB0ZWRJVg=="
        private const val expectedDecryptedData: String = "ZXhwZWN0ZWREZWNyeXB0ZWREYXRh"
    }
}
