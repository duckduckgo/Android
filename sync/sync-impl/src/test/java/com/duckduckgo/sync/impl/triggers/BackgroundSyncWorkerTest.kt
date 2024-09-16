/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.BACKGROUND_SYNC
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class BackgroundSyncWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockSyncEngine: SyncEngine = mock()
    private val mockSyncState: DeviceSyncState = mock()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mock()
    }

    @Test
    fun whenDoWorkTriggeredAndSyncEnabledThenSyncTriggeredReturnSuccess() =
        runTest {
            val worker = TestListenableWorkerBuilder<SyncBackgroundWorker>(context = context).build()
            worker.syncEngine = mockSyncEngine
            worker.dispatchers = coroutineRule.testDispatcherProvider
            worker.deviceSyncState = mockSyncState

            whenever(mockSyncState.isUserSignedInOnDevice()).thenReturn(true)

            val result = worker.doWork()

            verify(mockSyncEngine).triggerSync(BACKGROUND_SYNC)
            Assert.assertEquals(result, ListenableWorker.Result.success())
        }

    @Test
    fun whenDoWorkTriggeredAndSyncDisabledThenSyncNotTriggeredReturnSuccess() =
        runTest {
            val worker = TestListenableWorkerBuilder<SyncBackgroundWorker>(context = context).build()
            worker.syncEngine = mockSyncEngine
            worker.dispatchers = coroutineRule.testDispatcherProvider
            worker.deviceSyncState = mockSyncState

            whenever(mockSyncState.isUserSignedInOnDevice()).thenReturn(false)

            val result = worker.doWork()

            verifyNoInteractions(mockSyncEngine)
            Assert.assertEquals(result, ListenableWorker.Result.success())
        }
}
