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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.APP_OPEN
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppLifecycleSyncObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockLifecycleOwner = mock<LifecycleOwner>()
    private val mockDeviceSyncState = mock<DeviceSyncState>()
    private val mockSyncEngine = mock<SyncEngine>()

    private val testee = AppLifecycleSyncObserver(
        appCoroutineScope = coroutineTestRule.testScope,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        deviceSyncState = mockDeviceSyncState,
        syncEngine = mockSyncEngine,
    )

    @Test
    fun whenAppStartedAndSyncDisabledThenNothingHappens() = runTest {
        whenever(mockDeviceSyncState.isUserSignedInOnDevice()).thenReturn(false)

        testee.onStart(mockLifecycleOwner)

        verifyNoInteractions(mockSyncEngine)
    }

    @Test
    fun whenAppStartedAndSyncEnabledThenSyncIsTriggered() = runTest {
        whenever(mockDeviceSyncState.isUserSignedInOnDevice()).thenReturn(true)

        testee.onStart(mockLifecycleOwner)

        verify(mockSyncEngine).triggerSync(APP_OPEN)
    }
}
