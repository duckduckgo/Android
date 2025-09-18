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

package com.duckduckgo.pir.impl.brokers

import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.testing.TestLifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PirDataUpdateObserverTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val lifecycleOwner = TestLifecycleOwner(initialState = INITIALIZED)
    private val brokerJsonUpdater: BrokerJsonUpdater = mock()
    private val pirWorkHandler: PirWorkHandler = mock()
    private val canRunPirFlow = MutableStateFlow(false)

    private lateinit var pirDataUpdateObserver: PirDataUpdateObserver

    @Before
    fun setUp() = runTest {
        whenever(pirWorkHandler.canRunPir()).thenReturn(canRunPirFlow)

        pirDataUpdateObserver = PirDataUpdateObserver(
            coroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokerJsonUpdater = brokerJsonUpdater,
            pirWorkHandler = pirWorkHandler,
        )
    }

    @Test
    fun whenOnCreateAndPIRIsNotEnabledThenCancelsWork() = runTest {
        pirDataUpdateObserver.onCreate(lifecycleOwner)
        canRunPirFlow.value = false

        verify(pirWorkHandler).cancelWork()
        verify(brokerJsonUpdater, never()).update()
    }

    @Test
    fun whenOnCreateAndPIRStateChangesFromEnabledToDisabledThenCancelsWork() = runTest {
        whenever(brokerJsonUpdater.update()).thenReturn(true)

        // First enable PIR
        canRunPirFlow.value = true
        pirDataUpdateObserver.onCreate(lifecycleOwner)
        verify(brokerJsonUpdater).update()

        // Then disable PIR
        canRunPirFlow.value = false
        verify(pirWorkHandler).cancelWork()
    }

    @Test
    fun whenOnCreateAndPIRStateChangesFromDisabledToEnabledThenUpdatesBrokerData() = runTest {
        whenever(brokerJsonUpdater.update()).thenReturn(true)

        pirDataUpdateObserver.onCreate(lifecycleOwner)

        // First PIR is disabled
        canRunPirFlow.value = false
        verify(pirWorkHandler).cancelWork()

        // Then enable PIR
        canRunPirFlow.value = true
        verify(brokerJsonUpdater).update()
    }

    @Test
    fun whenOnCreateCalledMultipleTimesAndPIREnabledThenUpdatesBrokerOnce() = runTest {
        whenever(brokerJsonUpdater.update()).thenReturn(true)

        pirDataUpdateObserver.onCreate(lifecycleOwner)

        // Enable PIR multiple times
        canRunPirFlow.value = true
        canRunPirFlow.value = true
        canRunPirFlow.value = true

        // Should call update only once
        verify(brokerJsonUpdater, times(1)).update()
    }

    @Test
    fun whenOnCreateWithPIRDisabledThenCancelWork() = runTest {
        pirDataUpdateObserver.onCreate(lifecycleOwner)

        verify(pirWorkHandler).cancelWork()
        verify(brokerJsonUpdater, never()).update()
    }

    @Test
    fun whenOnCreateWithPIREnabledThenUpdatesBrokers() = runTest {
        whenever(brokerJsonUpdater.update()).thenReturn(true)
        canRunPirFlow.value = true

        pirDataUpdateObserver.onCreate(lifecycleOwner)

        verify(brokerJsonUpdater).update()
        verify(pirWorkHandler, never()).cancelWork()
    }
}
