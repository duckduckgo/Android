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

package com.duckduckgo.sync.impl.triggers

import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy.KEEP
import androidx.work.WorkManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BackgroundSyncWorkerSchedulerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val deviceSyncState: DeviceSyncState = mock()
    private val mockWorkManager: WorkManager = mock()
    private val mockOwner: LifecycleOwner = mock()

    lateinit var syncBackgroundWorkerScheduler: SyncBackgroundWorkerScheduler

    @Before
    fun before() {
        syncBackgroundWorkerScheduler =
            SyncBackgroundWorkerScheduler(mockWorkManager, deviceSyncState, TestScope(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenOnCreateAndSyncEnabledThenWorkerEnqueued() {
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(true)
        syncBackgroundWorkerScheduler.onCreate(mockOwner)

        verify(mockWorkManager).enqueueUniquePeriodicWork(any(), eq(KEEP), any())
    }

    @Test
    fun whenOnCreateAndSyncDisabledThenWorkerIsNotEnqueued() {
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(false)
        syncBackgroundWorkerScheduler.onCreate(mockOwner)

        verify(mockWorkManager).cancelAllWorkByTag(any())
    }
}
