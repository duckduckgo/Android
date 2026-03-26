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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.securestorage.RealL2DataTransformer
import com.duckduckgo.autofill.impl.securestorage.SecureStorageKeyProvider
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.security.Key

@RunWith(AndroidJUnit4::class)
class RealL2DataTransformerTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealL2DataTransformer
    private lateinit var encryptionHelper: FakeEncryptionHelper

    private val secureStorageKeyProvider: SecureStorageKeyProvider = mock()

    @Before
    fun setUp() = runTest {
        MockitoAnnotations.openMocks(this)
        val key = mock(Key::class.java)
        whenever(secureStorageKeyProvider.getl2Key()).thenReturn(key)
        encryptionHelper = FakeEncryptionHelper(expectedEncryptedData, expectedEncryptedIv, expectedDecryptedData)
        testee = RealL2DataTransformer(encryptionHelper, secureStorageKeyProvider, coroutineRule.testScope, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenCanProcessDataThenReturnCanAccessKeyStoreTrue() = runTest {
        whenever(secureStorageKeyProvider.canAccessKeyStore()).thenReturn(true)

        assertTrue(testee.canProcessData())
    }

    @Test
    fun whenCanProcessDataFalseThenReturnCanAccessKeyStoreFalse() = runTest {
        whenever(secureStorageKeyProvider.canAccessKeyStore()).thenReturn(false)

        assertFalse(testee.canProcessData())
    }

    @Test
    fun whenDataIsEncryptedThenDelegateEncryptionToEncryptionHelper() = runTest {
        val result = testee.encrypt("test123")

        assertEquals(expectedEncryptedData, result.data)
        assertEquals(expectedEncryptedIv, result.iv)
    }

    @Test
    fun whenDataIsDecryptedThenDelegateDecryptionToEncryptionHelper() = runTest {
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
