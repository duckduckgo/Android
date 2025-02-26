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

package com.duckduckgo.app.settings

import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_SYNC_PRESSED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncStateMonitor
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SettingsPixelDispatcherTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock private lateinit var pixelMock: Pixel

    @Mock private lateinit var syncStateMonitorMock: SyncStateMonitor

    lateinit var testee: SettingsPixelDispatcherImpl

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `when fireSyncPressed and sync state is null, then send a pixel with disabled state`() = runTest {
        whenever(syncStateMonitorMock.syncState()).thenReturn(emptyFlow())

        val testee = createTestee()
        testee.fireSyncPressed()

        verify(pixelMock).fire(
            pixel = SETTINGS_SYNC_PRESSED,
            parameters = mapOf("is_enabled" to "0"),
        )
    }

    @Test
    fun `when fireSyncPressed and sync state is OFF, then send a pixel with disabled state`() = runTest {
        whenever(syncStateMonitorMock.syncState()).thenReturn(flowOf(SyncState.OFF))

        val testee = createTestee()
        testee.fireSyncPressed()

        verify(pixelMock).fire(
            pixel = SETTINGS_SYNC_PRESSED,
            parameters = mapOf("is_enabled" to "0"),
        )
    }

    @Test
    fun `when fireSyncPressed and sync state is FAILED, then send a pixel with enabled state`() = runTest {
        whenever(syncStateMonitorMock.syncState()).thenReturn(flowOf(SyncState.FAILED))

        val testee = createTestee()
        testee.fireSyncPressed()

        verify(pixelMock).fire(
            pixel = SETTINGS_SYNC_PRESSED,
            parameters = mapOf("is_enabled" to "1"),
        )
    }

    @Test
    fun `when fireSyncPressed and sync state is READY, then send a pixel with enabled state`() = runTest {
        whenever(syncStateMonitorMock.syncState()).thenReturn(flowOf(SyncState.READY))

        val testee = createTestee()
        testee.fireSyncPressed()

        verify(pixelMock).fire(
            pixel = SETTINGS_SYNC_PRESSED,
            parameters = mapOf("is_enabled" to "1"),
        )
    }

    @Test
    fun `when fireSyncPressed and sync state is IN_PROGRESS, then send a pixel with enabled state`() = runTest {
        whenever(syncStateMonitorMock.syncState()).thenReturn(flowOf(SyncState.IN_PROGRESS))

        val testee = createTestee()
        testee.fireSyncPressed()

        verify(pixelMock).fire(
            pixel = SETTINGS_SYNC_PRESSED,
            parameters = mapOf("is_enabled" to "1"),
        )
    }

    private fun createTestee() = SettingsPixelDispatcherImpl(
        appCoroutineScope = coroutinesTestRule.testScope,
        pixel = pixelMock,
        syncStateMonitor = syncStateMonitorMock,
    )
}
