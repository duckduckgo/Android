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

package com.duckduckgo.app.nextsteps

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncStateMonitor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealNextStepsAdoptionMetricsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val sender: NextStepsExperimentMetricSender = mock()
    private val userStageStore: UserStageStore = mock()
    private val syncStates = MutableSharedFlow<SyncState>()
    private val syncStateMonitor: SyncStateMonitor = mock {
        whenever(it.syncState()).thenReturn(syncStates)
    }
    private val lifecycleOwner: LifecycleOwner = mock()

    private val testee = RealNextStepsAdoptionMetrics(
        sender = sender,
        userStageStore = userStageStore,
        syncStateMonitor = syncStateMonitor,
        appScope = coroutineRule.testScope,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun `widget added after the linear onboarding sends the widget added metric`() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)

        testee.onWidgetAdded()

        verify(sender).send("widget_added")
    }

    @Test
    fun `widget added during the linear onboarding sends nothing`() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)

        testee.onWidgetAdded()

        verify(sender, never()).send(any())
    }

    @Test
    fun `sync turning on after the linear onboarding sends the sync enabled metric`() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        testee.onCreate(lifecycleOwner)

        syncStates.emit(SyncState.OFF)
        syncStates.emit(SyncState.IN_PROGRESS)
        syncStates.emit(SyncState.READY)

        verify(sender, times(1)).send("sync_enabled")
    }

    @Test
    fun `sync already on at app start sends nothing`() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        testee.onCreate(lifecycleOwner)

        syncStates.emit(SyncState.READY)
        syncStates.emit(SyncState.IN_PROGRESS)

        verify(sender, never()).send(any())
    }

    @Test
    fun `sync turning on during the linear onboarding sends nothing`() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.onCreate(lifecycleOwner)

        syncStates.emit(SyncState.OFF)
        syncStates.emit(SyncState.READY)

        verify(sender, never()).send(any())
    }
}
