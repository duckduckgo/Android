/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.store.secure

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealPirSecureStorageKeyRepositoryTest {

    private lateinit var testee: RealPirSecureStorageKeyRepository

    private val mockKeyStore: PirSecureStorageKeyStore = mock()

    @Before
    fun setUp() {
        testee = RealPirSecureStorageKeyRepository(
            keyStore = mockKeyStore,
        )
    }

    @Test
    fun whenGetL1KeyAndKeyExistsThenReturnKey() = runTest {
        // Given
        val expectedKey = ByteArray(32) { it.toByte() }
        whenever(mockKeyStore.getKey("KEY_L1KEY")).thenReturn(
            expectedKey,
        )

        // When
        val result = testee.getL1Key()

        // Then
        assertArrayEquals(expectedKey, result)
        verify(mockKeyStore).getKey("KEY_L1KEY")
    }

    @Test
    fun whenGetL1KeyAndKeyDoesNotExistThenReturnNull() = runTest {
        // Given
        whenever(mockKeyStore.getKey("KEY_L1KEY")).thenReturn(null)

        // When
        val result = testee.getL1Key()

        // Then
        assertNull(result)
        verify(mockKeyStore).getKey("KEY_L1KEY")
    }

    @Test
    fun whenSetL1KeyWithValueThenUpdateKeyStore() = runTest {
        // Given
        val keyValue = ByteArray(32) { (it * 2).toByte() }

        // When
        testee.setL1Key(keyValue)

        // Then
        verify(mockKeyStore).updateKey("KEY_L1KEY", keyValue)
    }

    @Test
    fun whenSetL1KeyWithNullThenClearKey() = runTest {
        // When
        testee.setL1Key(null)

        // Then
        verify(mockKeyStore).updateKey("KEY_L1KEY", null)
    }

    @Test
    fun whenCanUseEncryptionAndSupportedThenReturnTrue() = runTest {
        // Given
        whenever(mockKeyStore.canUseEncryption()).thenReturn(true)

        // When
        val result = testee.canUseEncryption()

        // Then
        assertTrue(result)
        verify(mockKeyStore).canUseEncryption()
    }

    @Test
    fun whenCanUseEncryptionAndNotSupportedThenReturnFalse() = runTest {
        // Given
        whenever(mockKeyStore.canUseEncryption()).thenReturn(false)

        // When
        val result = testee.canUseEncryption()

        // Then
        assertFalse(result)
        verify(mockKeyStore).canUseEncryption()
    }

    @Test
    fun whenGetL1KeyUsesCorrectKeyName() = runTest {
        // Given
        whenever(mockKeyStore.getKey("KEY_L1KEY")).thenReturn(null)

        // When
        testee.getL1Key()

        // Then
        verify(mockKeyStore).getKey("KEY_L1KEY")
    }

    @Test
    fun whenSetL1KeyUsesCorrectKeyName() = runTest {
        // Given
        val keyValue = ByteArray(32)

        // When
        testee.setL1Key(keyValue)

        // Then
        verify(mockKeyStore).updateKey("KEY_L1KEY", keyValue)
    }
}
