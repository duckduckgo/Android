/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.autorestore

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealSyncAutoRestoreManagerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val persistentStorage: PersistentStorage = mock()

    private lateinit var testee: RealSyncAutoRestoreManager

    @Before
    fun setup() = runTest {
        whenever(persistentStorage.store(any(), any())).thenReturn(Result.success(Unit))
        whenever(persistentStorage.clear(any())).thenReturn(Result.success(Unit))
        testee = RealSyncAutoRestoreManager(
            persistentStorage = persistentStorage,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            moshi = Moshi.Builder().build(),
        )
    }

    @Test
    fun whenSaveRecoveryPayloadCalledThenStoresSerializedJson() = runTest {
        val recoveryCode = "recovery-abc-123"
        val deviceId = "device-xyz-456"

        testee.saveRecoveryPayload(recoveryCode, deviceId)

        val bytesCaptor = argumentCaptor<ByteArray>()
        verify(persistentStorage).store(any(), bytesCaptor.capture())
        val json = String(bytesCaptor.firstValue, Charsets.UTF_8)
        assert(json.contains("\"recovery_code\":\"$recoveryCode\"")) { "JSON missing recovery_code: $json" }
        assert(json.contains("\"device_id\":\"$deviceId\"")) { "JSON missing device_id: $json" }
    }

    @Test
    fun whenSaveRecoveryPayloadWithNullDeviceIdThenSerializesWithoutDeviceIdField() = runTest {
        testee.saveRecoveryPayload("code", null)

        val bytesCaptor = argumentCaptor<ByteArray>()
        verify(persistentStorage).store(any(), bytesCaptor.capture())
        val json = String(bytesCaptor.firstValue, Charsets.UTF_8)
        assert(json.contains("\"recovery_code\":\"code\"")) { "JSON missing recovery_code: $json" }
        // Moshi omits null fields by default
        assert(!json.contains("device_id")) { "JSON should not contain device_id when null: $json" }
    }

    @Test
    fun whenRetrievePayloadAndStorageHasDataThenReturnsDeserializedPayload() = runTest {
        val json = """{"recovery_code":"code-123","device_id":"device-456"}"""
        whenever(persistentStorage.retrieve(any())).thenReturn(Result.success(json.toByteArray(Charsets.UTF_8)))

        val result = testee.retrieveRecoveryPayload()

        assertEquals(RestorePayload(recoveryCode = "code-123", deviceId = "device-456"), result)
    }

    @Test
    fun whenRetrievePayloadAndDeviceIdIsAbsentThenReturnsPayloadWithNullDeviceId() = runTest {
        // Moshi omits null fields when storing, so a payload saved without device_id has no device_id key
        val json = """{"recovery_code":"code-123"}"""
        whenever(persistentStorage.retrieve(any())).thenReturn(Result.success(json.toByteArray(Charsets.UTF_8)))

        val result = testee.retrieveRecoveryPayload()

        assertEquals(RestorePayload(recoveryCode = "code-123", deviceId = null), result)
    }

    @Test
    fun whenRetrievePayloadAndStorageReturnsNullBytesThenReturnsNull() = runTest {
        whenever(persistentStorage.retrieve(any())).thenReturn(Result.success(null))

        val result = testee.retrieveRecoveryPayload()

        assertNull(result)
    }

    @Test
    fun whenRetrievePayloadAndStorageFailsThenReturnsNull() = runTest {
        whenever(persistentStorage.retrieve(any())).thenReturn(Result.failure(RuntimeException("Block Store error")))

        val result = testee.retrieveRecoveryPayload()

        assertNull(result)
    }

    @Test
    fun whenClearRecoveryCodeCalledThenClearsPersistentStorage() = runTest {
        testee.clearRecoveryCode()

        verify(persistentStorage).clear(any())
    }
}
