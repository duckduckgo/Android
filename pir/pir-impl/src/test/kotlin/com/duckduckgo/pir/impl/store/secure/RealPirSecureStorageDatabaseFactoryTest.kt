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

import android.content.Context
import com.duckduckgo.sqlcipher.loader.api.SqlCipherLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealPirSecureStorageDatabaseFactoryTest {

    private lateinit var testee: RealPirSecureStorageDatabaseFactory

    private val mockContext: Context = mock()
    private val mockSqlCipherLoader: SqlCipherLoader = mock()
    private val mockKeyProvider: PirSecureStorageKeyProvider = mock()

    @Before
    fun setUp() {
        testee = RealPirSecureStorageDatabaseFactory(
            context = mockContext,
            sqlCipherLoader = mockSqlCipherLoader,
            keyProvider = mockKeyProvider,
        )
    }

    @Test
    fun whenSqlCipherLoadFailsThenGetDatabaseReturnsNull() = runTest {
        // Given
        whenever(mockSqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.failure(RuntimeException("load failed")))

        // When
        val result = testee.getDatabase()

        // Then
        assertNull(result)
    }

    @Test
    fun whenCannotAccessKeyStoreThenGetDatabaseReturnsNull() = runTest {
        // Given
        whenever(mockSqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.success(Unit))
        whenever(mockKeyProvider.canAccessKeyStore()).thenReturn(false)

        // When
        val result = testee.getDatabase()

        // Then
        assertNull(result)
    }

    @Test
    fun whenKeyProviderThrowsExceptionThenGetDatabaseReturnsNull() = runTest {
        // Given
        whenever(mockSqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.success(Unit))
        whenever(mockKeyProvider.canAccessKeyStore()).thenThrow(RuntimeException("keystore error"))

        // When
        val result = testee.getDatabase()

        // Then
        assertNull(result)
    }

    @Test
    fun whenKeyProviderThrowsCancellationExceptionThenItIsPropagated() = runTest {
        // Given
        whenever(mockSqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.success(Unit))
        whenever(mockKeyProvider.canAccessKeyStore()).thenAnswer { throw CancellationException("cancelled") }

        // When / Then
        var cancellationThrown = false
        try {
            testee.getDatabase()
        } catch (e: CancellationException) {
            cancellationThrown = true
        }
        assertTrue("Expected CancellationException to be propagated", cancellationThrown)
    }

    @Test
    fun whenGetDatabaseCalledMultipleTimesAndSqlCipherFailsThenEachCallReturnsNull() = runTest {
        // Given
        whenever(mockSqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.failure(RuntimeException("load failed")))

        // When
        val result1 = testee.getDatabase()
        val result2 = testee.getDatabase()

        // Then - each call retries since database was never initialized
        assertNull(result1)
        assertNull(result2)
        verify(mockSqlCipherLoader, times(2)).waitForLibraryLoad()
    }

    @Test
    fun whenGetDatabaseCalledMultipleTimesAndCannotAccessKeyStoreThenEachCallReturnsNull() = runTest {
        // Given
        whenever(mockSqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.success(Unit))
        whenever(mockKeyProvider.canAccessKeyStore()).thenReturn(false)

        // When
        val result1 = testee.getDatabase()
        val result2 = testee.getDatabase()

        // Then
        assertNull(result1)
        assertNull(result2)
        verify(mockSqlCipherLoader, times(2)).waitForLibraryLoad()
        verify(mockKeyProvider, times(2)).canAccessKeyStore()
    }

    @Test
    fun whenGetDatabaseCalledConcurrentlyAndSqlCipherFailsThenAllCallsReturnNull() = runTest {
        // Given
        whenever(mockSqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.failure(RuntimeException("load failed")))

        // When - multiple concurrent calls, all of which fail
        val results = (1..5).map { async { testee.getDatabase() } }.awaitAll()

        // Then
        results.forEach { result -> assertNull(result) }
    }

    @Test
    fun whenGetDatabaseCalledConcurrentlyAndCannotAccessKeyStoreThenAllCallsReturnNull() = runTest {
        // Given
        whenever(mockSqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.success(Unit))
        whenever(mockKeyProvider.canAccessKeyStore()).thenReturn(false)

        // When
        val results = (1..5).map { async { testee.getDatabase() } }.awaitAll()

        // Then
        results.forEach { result -> assertNull(result) }
    }
}
