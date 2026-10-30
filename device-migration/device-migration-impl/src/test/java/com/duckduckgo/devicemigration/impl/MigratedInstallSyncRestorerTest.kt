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
package com.duckduckgo.devicemigration.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_PREVIOUS_DEVICE_HAD_SYNC
import com.duckduckgo.sync.api.SyncAutoRestore
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MigratedInstallSyncRestorerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val deviceMigration: DeviceMigration = mock()
    private val syncAutoRestore: SyncAutoRestore = mock()
    private val local = InMemorySharedPreferences()
    private val provider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn local
    }

    private val testee = MigratedInstallSyncRestorer(
        deviceMigration = deviceMigration,
        syncAutoRestore = syncAutoRestore,
        markerStore = DeviceMigrationMarkerStore(provider),
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    private fun startApp() {
        testee.onCreate(mock<LifecycleOwner>())
        coroutineTestRule.testScope.advanceUntilIdle()
    }

    @Test
    fun whenMigratedAndSyncRestorableThenRestoresOnceAndRecordsHadSync() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(syncAutoRestore.canRestore()).thenReturn(true)

        startApp()
        startApp()

        verify(syncAutoRestore, times(1)).restoreSyncAccount()
        assertTrue(local.getBoolean(KEY_PREVIOUS_DEVICE_HAD_SYNC, false))
    }

    @Test
    fun whenMigratedAndNotRestorableThenRecordsNoSyncAndNoRestore() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(syncAutoRestore.canRestore()).thenReturn(false)

        startApp()

        verify(syncAutoRestore, never()).restoreSyncAccount()
        assertFalse(local.getBoolean(KEY_PREVIOUS_DEVICE_HAD_SYNC, true))
    }

    @Test
    fun whenNotMigratedThenNoSyncCalls() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(false)

        startApp()

        verify(syncAutoRestore, never()).canRestore()
        verify(syncAutoRestore, never()).restoreSyncAccount()
    }

    @Test
    fun whenAlreadyRunThenNotRepeated() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(syncAutoRestore.canRestore()).thenReturn(false)
        startApp()

        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        startApp()

        verify(syncAutoRestore, times(1)).canRestore()
        verify(syncAutoRestore, never()).restoreSyncAccount()
    }

    @Test
    fun whenRestoreThrowsThenHadSyncStaysTrueAndNotRepeated() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        whenever(syncAutoRestore.restoreSyncAccount()).thenThrow(RuntimeException())

        startApp()
        startApp()

        assertTrue(local.getBoolean(KEY_PREVIOUS_DEVICE_HAD_SYNC, false))
        verify(syncAutoRestore, times(1)).restoreSyncAccount()
    }

    @Test
    fun whenCanRestoreThrowsThenNothingRecordedAndRetriedNextStart() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(syncAutoRestore.canRestore()).thenThrow(RuntimeException())

        startApp()

        assertFalse(local.contains(KEY_PREVIOUS_DEVICE_HAD_SYNC))

        doReturn(true).whenever(syncAutoRestore).canRestore()
        startApp()

        verify(syncAutoRestore, times(1)).restoreSyncAccount()
        assertTrue(local.getBoolean(KEY_PREVIOUS_DEVICE_HAD_SYNC, false))
    }
}
