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

package com.duckduckgo.persistent.storage.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.persistent.storage.api.PersistentStorageAvailability
import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesResponse
import com.google.android.gms.auth.blockstore.RetrieveBytesResponse.BlockstoreData
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealPersistentStorageTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val blockstoreClient: BlockstoreClient = mock()
    private val clientProvider: BlockstoreClientProvider = mock()

    private lateinit var testee: RealPersistentStorage

    @Before
    fun setup() {
        whenever(clientProvider.client).thenReturn(blockstoreClient)
        testee = RealPersistentStorage(
            clientProvider = clientProvider,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenPlayServicesNotAvailableThenReturnsUnavailable() = runTest {
        configurePlayServicesAvailable(false)

        val result = testee.checkAvailability()

        assertEquals(PersistentStorageAvailability.Unavailable, result)
    }

    @Test
    fun whenE2EEncryptionAvailableThenReturnsAvailableEncrypted() = runTest {
        configurePlayServicesAvailable(true)
        configureE2EAvailable(true)

        val result = testee.checkAvailability()

        assertEquals(PersistentStorageAvailability.AvailableEncrypted, result)
    }

    @Test
    fun whenE2EEncryptionNotAvailableThenReturnsAvailableUnencrypted() = runTest {
        configurePlayServicesAvailable(true)
        configureE2EAvailable(false)

        val result = testee.checkAvailability()

        assertEquals(PersistentStorageAvailability.AvailableUnencrypted, result)
    }

    @Test
    fun whenE2ECheckFailsThenReturnsAvailableUnencrypted() = runTest {
        configurePlayServicesAvailable(true)
        configureE2ECheckFails()

        val result = testee.checkAvailability()

        assertEquals(PersistentStorageAvailability.AvailableUnencrypted, result)
    }

    @Test
    fun whenWriteAndPlayServicesNotAvailableThenReturnsFailure() = runTest {
        configurePlayServicesAvailable(false)

        val result = testee.write("key", "value")

        assertTrue(result.isFailure)
    }

    @Test
    fun whenWriteSucceedsThenReturnsSuccess() = runTest {
        configurePlayServicesAvailable(true)
        configureWriteSuccess()

        val result = testee.write("key", "value")

        assertTrue(result.isSuccess)
    }

    @Test
    fun whenWriteFailsThenReturnsFailure() = runTest {
        configurePlayServicesAvailable(true)
        configureWriteFails()

        val result = testee.write("key", "value")

        assertTrue(result.isFailure)
    }

    @Test
    fun whenReadAndPlayServicesNotAvailableThenReturnsFailure() = runTest {
        configurePlayServicesAvailable(false)

        val result = testee.read("key")

        assertTrue(result.isFailure)
    }

    @Test
    fun whenReadSucceedsWithDataThenReturnsValue() = runTest {
        configurePlayServicesAvailable(true)
        configureReadSuccess("key", "test_value")

        val result = testee.read("key")

        assertTrue(result.isSuccess)
        assertEquals("test_value", result.getOrNull())
    }

    @Test
    fun whenReadSucceedsWithNoDataThenReturnsNull() = runTest {
        configurePlayServicesAvailable(true)
        configureReadSuccessNoData("key")

        val result = testee.read("key")

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun whenReadFailsThenReturnsFailure() = runTest {
        configurePlayServicesAvailable(true)
        configureReadFails()

        val result = testee.read("key")

        assertTrue(result.isFailure)
    }

    private fun configurePlayServicesAvailable(available: Boolean) {
        whenever(clientProvider.isPlayServicesAvailable).thenReturn(available)
    }

    private fun configureE2EAvailable(available: Boolean) {
        whenever(blockstoreClient.isEndToEndEncryptionAvailable).thenReturn(Tasks.forResult(available))
    }

    private fun configureE2ECheckFails() {
        whenever(blockstoreClient.isEndToEndEncryptionAvailable).thenReturn(Tasks.forException(RuntimeException("E2E check failed")))
    }

    private fun configureWriteSuccess() {
        whenever(blockstoreClient.storeBytes(any<StoreBytesData>())).thenReturn(Tasks.forResult(0))
    }

    private fun configureWriteFails() {
        whenever(blockstoreClient.storeBytes(any<StoreBytesData>())).thenReturn(Tasks.forException(RuntimeException("Write failed")))
    }

    private fun configureReadSuccess(key: String, value: String) {
        val blockstoreData: BlockstoreData = mock()
        whenever(blockstoreData.bytes).thenReturn(value.toByteArray(Charsets.UTF_8))

        val response: RetrieveBytesResponse = mock()
        whenever(response.blockstoreDataMap).thenReturn(mapOf(key to blockstoreData))

        whenever(blockstoreClient.retrieveBytes(any<RetrieveBytesRequest>())).thenReturn(Tasks.forResult(response))
    }

    @Suppress("UNUSED_PARAMETER")
    private fun configureReadSuccessNoData(key: String) {
        val response: RetrieveBytesResponse = mock()
        whenever(response.blockstoreDataMap).thenReturn(emptyMap())

        whenever(blockstoreClient.retrieveBytes(any<RetrieveBytesRequest>())).thenReturn(Tasks.forResult(response))
    }

    private fun configureReadFails() {
        whenever(blockstoreClient.retrieveBytes(any<RetrieveBytesRequest>())).thenReturn(Tasks.forException(RuntimeException("Read failed")))
    }
}
