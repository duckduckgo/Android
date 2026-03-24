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

package com.duckduckgo.persistentstorage.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.persistentstorage.api.PersistentStorageAvailability.Available
import com.duckduckgo.persistentstorage.api.PersistentStorageAvailability.Unavailable
import com.duckduckgo.persistentstorage.api.PersistentStorageKey
import com.duckduckgo.persistentstorage.api.PersistentStorageUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.coroutines.coroutineContext

class RealPersistentStorageTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val blockStoreWrapper: BlockStoreWrapper = mock()
    private var currentJob: Job? = null

    private lateinit var testee: RealPersistentStorage

    @Before
    fun setup() {
        testee = RealPersistentStorage(
            blockStoreWrapper = blockStoreWrapper,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    // region checkAvailability

    @Test
    fun whenPlayServicesUnavailableThenCheckAvailabilityReturnsUnavailable() = runTest {
        configurePlayServicesUnavailable()

        val result = testee.checkAvailability()

        assertEquals(Unavailable, result)
    }

    @Test
    fun whenPlayServicesAvailableAndE2EAvailableThenCheckAvailabilityReturnsAvailableWithEncryptionTrue() = runTest {
        configurePlayServicesAvailable()
        configureE2EEncryptionAvailable(true)

        val result = testee.checkAvailability()

        assertEquals(Available(isEndToEndEncryptionSupported = true), result)
    }

    @Test
    fun whenPlayServicesAvailableAndE2EUnavailableThenCheckAvailabilityReturnsAvailableWithEncryptionFalse() = runTest {
        configurePlayServicesAvailable()
        configureE2EEncryptionAvailable(false)

        val result = testee.checkAvailability()

        assertEquals(Available(isEndToEndEncryptionSupported = false), result)
    }

    @Test
    fun whenPlayServicesAvailableAndE2ECheckThrowsThenCheckAvailabilityReturnsAvailableWithEncryptionFalse() = runTest {
        configurePlayServicesAvailable()
        configureE2EEncryptionCheckThrows(RuntimeException("E2E check failed"))

        val result = testee.checkAvailability()

        assertEquals(Available(isEndToEndEncryptionSupported = false), result)
    }

    // endregion

    // region retrieve

    @Test
    fun whenPlayServicesUnavailableThenRetrieveReturnsFailureWithUnavailableException() = runTest {
        configurePlayServicesUnavailable()

        val result = testee.retrieve(TestKey)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PersistentStorageUnavailableException)
    }

    @Test
    fun whenRetrieveSucceedsWithBytesThenReturnsSuccessWithBytes() = runTest {
        val bytes = "hello".toByteArray()
        configurePlayServicesAvailable()
        configureRetrieveBytesReturns(bytes)

        val result = testee.retrieve(TestKey)

        assertTrue(result.isSuccess)
        assertEquals(bytes, result.getOrNull())
    }

    @Test
    fun whenRetrieveSucceedsWithNullThenReturnsSuccessWithNull() = runTest {
        configurePlayServicesAvailable()
        configureRetrieveBytesReturns(null)

        val result = testee.retrieve(TestKey)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun whenBlockStoreWrapperThrowsOnRetrieveThenReturnsFailureWrappingException() = runTest {
        val cause = RuntimeException("Block store error")
        configurePlayServicesAvailable()
        configureRetrieveBytesThrows(cause)

        val result = testee.retrieve(TestKey)

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    @Test
    fun whenCoroutineCancelledDuringRetrieveThenCancellationPropagates() = runTest {
        configurePlayServicesAvailable()
        configureRetrieveBytesThrowsAfterCancelling()

        var caughtCancellation = false
        val job = launch {
            currentJob = coroutineContext[Job]
            try {
                testee.retrieve(TestKey)
            } catch (e: CancellationException) {
                caughtCancellation = true
            }
        }
        job.join()
        assertTrue("Expected CancellationException to propagate", caughtCancellation)
    }

    // endregion

    // region store

    @Test
    fun whenPlayServicesUnavailableThenStoreReturnsFailureWithUnavailableException() = runTest {
        configurePlayServicesUnavailable()

        val result = testee.store(TestKey, "value".toByteArray())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PersistentStorageUnavailableException)
    }

    @Test
    fun whenStoreSucceedsThenReturnsSuccess() = runTest {
        configurePlayServicesAvailable()
        configureStoreBytesSucceeds()

        val result = testee.store(TestKey, "value".toByteArray())

        assertTrue(result.isSuccess)
    }

    @Test
    fun whenBlockStoreWrapperThrowsOnStoreThenReturnsFailureWrappingException() = runTest {
        val cause = RuntimeException("Block store error")
        configurePlayServicesAvailable()
        configureStoreBytesThrows(cause)

        val result = testee.store(TestKey, "value".toByteArray())

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    @Test
    fun whenStoringThenPassesCorrectKeyBytesAndShouldBackupToCloudToWrapper() = runTest {
        val bytes = "payload".toByteArray()
        configurePlayServicesAvailable()
        configureStoreBytesSucceeds()

        testee.store(TestKey, bytes)

        verify(blockStoreWrapper).storeBytes(
            key = eq(TestKey.key),
            bytes = eq(bytes),
            shouldBackupToCloud = eq(TestKey.shouldBackupToCloud),
        )
    }

    @Test
    fun whenCoroutineCancelledDuringStoreThenCancellationPropagates() = runTest {
        configurePlayServicesAvailable()
        configureStoreBytesThrowsAfterCancelling()

        var caughtCancellation = false
        val job = launch {
            currentJob = coroutineContext[Job]
            try {
                testee.store(TestKey, "value".toByteArray())
            } catch (e: CancellationException) {
                caughtCancellation = true
            }
        }
        job.join()
        assertTrue("Expected CancellationException to propagate", caughtCancellation)
    }

    // endregion

    // region clear

    @Test
    fun whenPlayServicesUnavailableThenClearReturnsFailureWithUnavailableException() = runTest {
        configurePlayServicesUnavailable()

        val result = testee.clear(TestKey)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PersistentStorageUnavailableException)
    }

    @Test
    fun whenClearSucceedsThenReturnsSuccess() = runTest {
        configurePlayServicesAvailable()
        configureDeleteBytesSucceeds()

        val result = testee.clear(TestKey)

        assertTrue(result.isSuccess)
    }

    @Test
    fun whenBlockStoreWrapperThrowsOnClearThenReturnsFailureWrappingException() = runTest {
        val cause = RuntimeException("Block store error")
        configurePlayServicesAvailable()
        configureDeleteBytesThrows(cause)

        val result = testee.clear(TestKey)

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    @Test
    fun whenCoroutineCancelledDuringClearThenCancellationPropagates() = runTest {
        configurePlayServicesAvailable()
        configureDeleteBytesThrowsAfterCancelling()

        var caughtCancellation = false
        val job = launch {
            currentJob = coroutineContext[Job]
            try {
                testee.clear(TestKey)
            } catch (e: CancellationException) {
                caughtCancellation = true
            }
        }
        job.join()
        assertTrue("Expected CancellationException to propagate", caughtCancellation)
    }

    // endregion

    // region test helpers

    private object TestKey : PersistentStorageKey(key = "test.key", shouldBackupToCloud = false)

    private fun configurePlayServicesAvailable() {
        whenever(blockStoreWrapper.isPlayServicesAvailable).thenReturn(true)
    }

    private fun configurePlayServicesUnavailable() {
        whenever(blockStoreWrapper.isPlayServicesAvailable).thenReturn(false)
    }

    private suspend fun configureE2EEncryptionAvailable(available: Boolean) {
        whenever(blockStoreWrapper.isEndToEndEncryptionAvailable()).thenReturn(available)
    }

    private suspend fun configureE2EEncryptionCheckThrows(throwable: Throwable) {
        whenever(blockStoreWrapper.isEndToEndEncryptionAvailable()).thenThrow(throwable)
    }

    private suspend fun configureRetrieveBytesReturns(bytes: ByteArray?) {
        whenever(blockStoreWrapper.retrieveBytes(any())).thenReturn(bytes)
    }

    private suspend fun configureRetrieveBytesThrows(throwable: Throwable) {
        whenever(blockStoreWrapper.retrieveBytes(any())).thenThrow(throwable)
    }

    private suspend fun configureStoreBytesSucceeds() {
        whenever(blockStoreWrapper.storeBytes(any(), any(), any())).thenReturn(Unit)
    }

    private suspend fun configureStoreBytesThrows(throwable: Throwable) {
        whenever(blockStoreWrapper.storeBytes(any(), any(), any())).thenThrow(throwable)
    }

    private suspend fun configureDeleteBytesSucceeds() {
        whenever(blockStoreWrapper.deleteBytes(any())).thenReturn(Unit)
    }

    private suspend fun configureDeleteBytesThrows(throwable: Throwable) {
        whenever(blockStoreWrapper.deleteBytes(any())).thenThrow(throwable)
    }

    private suspend fun configureRetrieveBytesThrowsAfterCancelling() {
        whenever(blockStoreWrapper.retrieveBytes(any())).thenAnswer {
            currentJob?.cancel()
            throw CancellationException("cancelled")
        }
    }

    private suspend fun configureStoreBytesThrowsAfterCancelling() {
        whenever(blockStoreWrapper.storeBytes(any(), any(), any())).thenAnswer {
            currentJob?.cancel()
            throw CancellationException("cancelled")
        }
    }

    private suspend fun configureDeleteBytesThrowsAfterCancelling() {
        whenever(blockStoreWrapper.deleteBytes(any())).thenAnswer {
            currentJob?.cancel()
            throw CancellationException("cancelled")
        }
    }

    // endregion
}
