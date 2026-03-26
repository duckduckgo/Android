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

package com.duckduckgo.duckchat.impl.messaging.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncStateMonitor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SyncStatusChangedObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockSyncStateMonitor: SyncStateMonitor = mock()
    private val mockSyncStatusHelper: SyncStatusHelper = mock()
    private val syncStateFlow = MutableSharedFlow<SyncState>()

    private lateinit var observer: RealSyncStatusChangedObserver

    @Before
    fun setUp() {
        whenever(mockSyncStateMonitor.syncState()).thenReturn(syncStateFlow)
        observer = RealSyncStatusChangedObserver(
            syncStateMonitor = mockSyncStateMonitor,
            syncStatusHelper = mockSyncStatusHelper,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when first signed-in state emitted then it is dropped`() = runTest {
        val payload = createPayload(syncAvailable = true, userId = "user123")
        whenever(mockSyncStatusHelper.buildSyncStatusPayload()).thenReturn(payload)

        observer.syncStatusChangedEvents.test {
            // First emission should be dropped (initial state)
            syncStateFlow.emit(SyncState.OFF)
            expectNoEvents()

            // Second emission (signed-in status change) should come through
            syncStateFlow.emit(SyncState.READY)
            val emittedPayload = awaitItem()
            assertEquals(true, emittedPayload.getBoolean("syncAvailable"))
            assertEquals("user123", emittedPayload.getString("userId"))
        }
    }

    @Test
    fun `when signed-in status changes then emits payload`() = runTest {
        val readyPayload = createPayload(syncAvailable = true, userId = "user123")
        val offPayload = createPayload(syncAvailable = false, userId = null)

        whenever(mockSyncStatusHelper.buildSyncStatusPayload())
            .thenReturn(readyPayload)
            .thenReturn(offPayload)

        observer.syncStatusChangedEvents.test {
            // First emission dropped (initial state)
            syncStateFlow.emit(SyncState.OFF)
            expectNoEvents()

            // Sign in - emits payload
            syncStateFlow.emit(SyncState.READY)
            val firstPayload = awaitItem()
            assertEquals(true, firstPayload.getBoolean("syncAvailable"))

            // Sign out - emits payload
            syncStateFlow.emit(SyncState.OFF)
            val secondPayload = awaitItem()
            assertEquals(false, secondPayload.getBoolean("syncAvailable"))
        }
    }

    @Test
    fun `when sync state transitions between signed-in states then no payload emitted`() = runTest {
        val payload = createPayload(syncAvailable = true, userId = "user123")
        whenever(mockSyncStatusHelper.buildSyncStatusPayload()).thenReturn(payload)

        observer.syncStatusChangedEvents.test {
            // First emission dropped (initial state)
            syncStateFlow.emit(SyncState.OFF)
            expectNoEvents()

            // Sign in - emits payload
            syncStateFlow.emit(SyncState.READY)
            awaitItem()

            // IN_PROGRESS is still signed in - no emission
            syncStateFlow.emit(SyncState.IN_PROGRESS)
            expectNoEvents()

            // Back to READY is still signed in - no emission
            syncStateFlow.emit(SyncState.READY)
            expectNoEvents()

            // FAILED is still signed in - no emission
            syncStateFlow.emit(SyncState.FAILED)
            expectNoEvents()
        }
    }

    @Test
    fun `when sync state goes from signed-in to OFF then emits payload`() = runTest {
        val offPayload = createPayload(syncAvailable = false, userId = null)

        // Only one call to buildSyncStatusPayload will happen (when OFF is emitted after drop)
        whenever(mockSyncStatusHelper.buildSyncStatusPayload()).thenReturn(offPayload)

        observer.syncStatusChangedEvents.test {
            // Start signed in - initial boolean (true) is dropped
            syncStateFlow.emit(SyncState.READY)
            expectNoEvents()

            // Various signed-in state transitions - filtered by distinctUntilChanged (still true)
            syncStateFlow.emit(SyncState.IN_PROGRESS)
            expectNoEvents()

            syncStateFlow.emit(SyncState.READY)
            expectNoEvents()

            // Sign out - boolean changes to false, emits payload
            syncStateFlow.emit(SyncState.OFF)
            val payload = awaitItem()
            assertEquals(false, payload.getBoolean("syncAvailable"))
        }
    }

    private fun createPayload(syncAvailable: Boolean, userId: String?): JSONObject {
        return JSONObject().apply {
            put("syncAvailable", syncAvailable)
            put("userId", userId ?: JSONObject.NULL)
            put("deviceId", if (userId != null) "device123" else JSONObject.NULL)
            put("deviceName", if (userId != null) "TestDevice" else JSONObject.NULL)
            put("deviceType", if (userId != null) "mobile" else JSONObject.NULL)
        }
    }
}
