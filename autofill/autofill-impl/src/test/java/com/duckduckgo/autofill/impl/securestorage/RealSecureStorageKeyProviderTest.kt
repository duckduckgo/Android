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

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.securestorage.RealSecureStorageKeyProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.securestorage.impl.encryption.RandomBytesGenerator
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.security.Key

class RealSecureStorageKeyProviderTest {
    private lateinit var testee: RealSecureStorageKeyProvider
    private lateinit var secureStorageKeyRepository: FakeSecureStorageKeyRepository
    private lateinit var encryptionHelper: FakeEncryptionHelper
    private lateinit var secureStorageKeyGenerator: FakeSecureStorageKeyGenerator

    private val randomBytesGenerator: RandomBytesGenerator = mock()
    private val mockKey: Key = mock()
    private val fakeAutofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    private val testRandomBytes = "Zm9vYmFy".toByteArray()

    @Before
    fun setUp() = runTest {
        secureStorageKeyRepository = FakeSecureStorageKeyRepository(true)
        encryptionHelper = FakeEncryptionHelper(expectedEncryptedData, expectedEncryptedIv, expectedDecryptedData)
        secureStorageKeyGenerator = FakeSecureStorageKeyGenerator(mockKey)
        testee = RealSecureStorageKeyProvider(
            randomBytesGenerator,
            secureStorageKeyRepository,
            encryptionHelper,
            secureStorageKeyGenerator,
            fakeAutofillFeature,
        )
    }

    @Test
    fun whenCanAccessKeyStoreIsCheckedThenReturnRepositoryCanUseEncryption() = runTest {
        assertTrue(testee.canAccessKeyStore())
    }

    @Test
    fun whenNoL1KeySetThenGenerateAndStoreL1Key() = runTest {
        whenever(randomBytesGenerator.generateBytes(32)).thenReturn(testRandomBytes)
        assertNull("Initial state is incorrect since L1key is not null", secureStorageKeyRepository.getL1Key())

        val result = testee.getl1Key()

        assertEquals(testRandomBytes, secureStorageKeyRepository.getL1Key()) // key is stored
        assertEquals(testRandomBytes, result) // returned value is correct
    }

    @Test
    fun whenL1KeySetThenReturnStoredL1Key() = runTest {
        secureStorageKeyRepository.setL1Key(testRandomBytes)

        val result = testee.getl1Key()

        assertEquals(testRandomBytes, result) // returned value is correct
    }

    @Test
    fun whenNoValueStoredInKeyRepositoryThenReturnKeyAndGenerateAndStoreKeyValues() = runTest {
        whenever(mockKey.encoded).thenReturn(testRandomBytes)
        whenever(randomBytesGenerator.generateBytes(32)).thenReturn(testRandomBytes)
        assertNull("Initial state is incorrect since password is not null", secureStorageKeyRepository.getPassword())
        assertNull("Initial state is incorrect since passwordSalt is not null", secureStorageKeyRepository.getPasswordSalt())
        assertNull("Initial state is incorrect since encryptedL2Key is not null", secureStorageKeyRepository.getEncryptedL2Key())
        assertNull("Initial state is incorrect since encryptedL2KeyIV is not null", secureStorageKeyRepository.getEncryptedL2KeyIV())

        val result = testee.getl2Key()

        assertEquals(testRandomBytes, secureStorageKeyRepository.getPassword()) // key is stored
        assertEquals(testRandomBytes, secureStorageKeyRepository.getPasswordSalt()) // key is stored
        assertEquals(expectedEncryptedData, secureStorageKeyRepository.getEncryptedL2Key()!!.toByteString().base64()) // key is stored
        assertEquals(expectedEncryptedIv, secureStorageKeyRepository.getEncryptedL2KeyIV()!!.toByteString().base64()) // key is stored
        assertEquals(mockKey, result)
    }

    @Test
    fun whenAllKeysStoredInKeyRepositoryThenUseKeysWhenConstructingL2Key() = runTest {
        secureStorageKeyRepository.setPassword(testRandomBytes)
        secureStorageKeyRepository.setPasswordSalt(testRandomBytes)
        secureStorageKeyRepository.setEncryptedL2Key(testRandomBytes)
        secureStorageKeyRepository.setEncryptedL2KeyIV(testRandomBytes)

        val result = testee.getl2Key()

        assertEquals(mockKey, result)
    }

    companion object {
        private const val expectedEncryptedData: String = "ZXhwZWN0ZWRFbmNyeXB0ZWREYXRh"
        private const val expectedEncryptedIv: String = "ZXhwZWN0ZWRFbmNyeXB0ZWRJVg=="
        private const val expectedDecryptedData: String = "ZXhwZWN0ZWREZWNyeXB0ZWREYXRh"
    }
}
