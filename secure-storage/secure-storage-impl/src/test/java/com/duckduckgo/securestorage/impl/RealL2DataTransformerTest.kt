/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.securestorage.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.security.Key

class RealL2DataTransformerTest {
    private lateinit var testee: RealL2DataTransformer
    private lateinit var encryptionHelper: FakeEncryptionHelper

    @Mock
    private lateinit var secureStorageKeyProvider: SecureStorageKeyProvider

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        val key = mock(Key::class.java)
        whenever(secureStorageKeyProvider.getl2Key()).thenReturn(key)
        encryptionHelper = FakeEncryptionHelper(expectedEncryptedData, expectedEncryptedIv, expectedDecryptedData)
        testee = RealL2DataTransformer(encryptionHelper, secureStorageKeyProvider)
    }

    @Test
    fun whenCanProcessDataThenReturnCanAccessKeyStoreTrue() {
        whenever(secureStorageKeyProvider.canAccessKeyStore()).thenReturn(true)

        assertTrue(testee.canProcessData())
    }

    @Test
    fun whenCanProcessDataFalseThenReturnCanAccessKeyStoreFalse() {
        whenever(secureStorageKeyProvider.canAccessKeyStore()).thenReturn(false)

        assertFalse(testee.canProcessData())
    }

    @Test
    fun whenDataIsEncryptedThenDelegateEncryptionToEncryptionHelper() {
        val result = testee.encrypt("test123")

        assertEquals(expectedEncryptedData, result.data)
        assertEquals(expectedEncryptedIv, result.iv)
    }

    @Test
    fun whenDataIsDecryptedThenDelegateDecryptionToEncryptionHelper() {
        val result = testee.decrypt("test123", "iv")

        assertEquals(decodedDecryptedData, result)
    }

    companion object {
        private const val expectedEncryptedData: String = "ZXhwZWN0ZWRFbmNyeXB0ZWREYXRh"
        private const val expectedEncryptedIv: String = "ZXhwZWN0ZWRFbmNyeXB0ZWRJVg=="
        private const val expectedDecryptedData: String = "ZXhwZWN0ZWREZWNyeXB0ZWREYXRh"
        private const val decodedDecryptedData: String = "expectedDecryptedData"
    }
}
