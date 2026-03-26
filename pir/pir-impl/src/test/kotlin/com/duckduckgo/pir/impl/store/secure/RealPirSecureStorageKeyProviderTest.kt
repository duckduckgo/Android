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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealPirSecureStorageKeyProviderTest {

    private lateinit var testee: RealPirSecureStorageKeyProvider

    private val mockRandomBytesGenerator: PirRandomBytesGenerator = mock()
    private val mockSecureStorageKeyRepository: PirSecureStorageKeyRepository = mock()

    @Before
    fun setUp() {
        testee = RealPirSecureStorageKeyProvider(
            randomBytesGenerator = mockRandomBytesGenerator,
            secureStorageKeyRepository = mockSecureStorageKeyRepository,
        )
    }

    @Test
    fun whenCanAccessKeyStoreReturnsTrue() = runTest {
        // Given
        whenever(mockSecureStorageKeyRepository.canUseEncryption()).thenReturn(true)

        // When
        val result = testee.canAccessKeyStore()

        // Then
        assertTrue(result)
        verify(mockSecureStorageKeyRepository).canUseEncryption()
    }

    @Test
    fun whenCanAccessKeyStoreReturnsFalse() = runTest {
        // Given
        whenever(mockSecureStorageKeyRepository.canUseEncryption()).thenReturn(false)

        // When
        val result = testee.canAccessKeyStore()

        // Then
        assertFalse(result)
        verify(mockSecureStorageKeyRepository).canUseEncryption()
    }

    @Test
    fun whenGetl1KeyAndKeyDoesNotExistThenGenerateNewKeyAndStoreIt() = runTest {
        // Given
        val generatedKey = ByteArray(32) { it.toByte() }
        whenever(mockSecureStorageKeyRepository.getL1Key()).thenReturn(null)
        whenever(mockRandomBytesGenerator.generateBytes(32)).thenReturn(generatedKey)

        // When
        val result = testee.getl1Key()

        // Then
        assertArrayEquals(generatedKey, result)
        assertEquals(32, result.size)
        verify(mockSecureStorageKeyRepository).getL1Key()
        verify(mockRandomBytesGenerator).generateBytes(32)
        verify(mockSecureStorageKeyRepository).setL1Key(generatedKey)
    }

    @Test
    fun whenGetl1KeyAndKeyExistsThenReturnExistingKey() = runTest {
        // Given
        val existingKey = ByteArray(32) { (it * 2).toByte() }
        whenever(mockSecureStorageKeyRepository.getL1Key()).thenReturn(existingKey)

        // When
        val result = testee.getl1Key()

        // Then
        assertArrayEquals(existingKey, result)
        // getL1Key() is called twice: once in the if condition, once in the else branch
        verify(mockSecureStorageKeyRepository, times(2)).getL1Key()
        verify(mockRandomBytesGenerator, never()).generateBytes(any())
        verify(mockSecureStorageKeyRepository, never()).setL1Key(any())
    }

    @Test
    fun whenGetl1KeyCalledMultipleTimesWithExistingKeyThenReturnSameKey() = runTest {
        // Given
        val existingKey = ByteArray(32) { (it * 3).toByte() }
        whenever(mockSecureStorageKeyRepository.getL1Key()).thenReturn(existingKey)

        // When
        val result1 = testee.getl1Key()
        val result2 = testee.getl1Key()
        val result3 = testee.getl1Key()

        // Then
        assertArrayEquals(existingKey, result1)
        assertArrayEquals(existingKey, result2)
        assertArrayEquals(existingKey, result3)
        verify(mockSecureStorageKeyRepository, times(6)).getL1Key()
        verify(mockRandomBytesGenerator, never()).generateBytes(any())
    }

    @Test
    fun whenGetl1KeyCalledConcurrentlyThenHandledSafely() = runTest {
        // Given
        val existingKey = ByteArray(32) { (it * 4).toByte() }
        whenever(mockSecureStorageKeyRepository.getL1Key()).thenReturn(existingKey)

        // When - Call getl1Key concurrently from multiple coroutines
        val results = (1..10).map {
            async { testee.getl1Key() }
        }.awaitAll()

        // Then - All calls should succeed and return the same key
        assertEquals(10, results.size)
        results.forEach { result ->
            assertArrayEquals(existingKey, result)
        }
        verify(mockSecureStorageKeyRepository, times(20)).getL1Key()
    }

    @Test
    fun whenGetl1KeyCalledConcurrentlyAndKeyDoesNotExistThenGenerateOnlyOnce() = runTest {
        // Given
        val generatedKey = ByteArray(32) { it.toByte() }
        whenever(mockSecureStorageKeyRepository.getL1Key())
            .thenReturn(null)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
            .thenReturn(generatedKey)
        whenever(mockRandomBytesGenerator.generateBytes(32)).thenReturn(generatedKey)

        // When - Call getl1Key concurrently from multiple coroutines
        val results = (1..10).map {
            async { testee.getl1Key() }
        }.awaitAll()

        // Then - All calls should succeed and return the same key
        assertEquals(10, results.size)
        results.forEach { result ->
            assertArrayEquals(generatedKey, result)
        }

        verify(mockSecureStorageKeyRepository, times(19)).getL1Key()
        verify(mockRandomBytesGenerator).generateBytes(32)
        verify(mockSecureStorageKeyRepository).setL1Key(generatedKey)
    }
}
