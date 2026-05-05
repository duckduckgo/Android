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
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.PirFeatureDataCleaner
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PirDataUpdateObserverTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var lifecycleOwner: TestLifecycleOwner
    private val brokerJsonUpdater: BrokerJsonUpdater = mock()
    private val pirWorkHandler: PirWorkHandler = mock()
    private val pirFeatureDataCleaner: PirFeatureDataCleaner = mock()
    private val pirRepository: PirRepository = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val pirPixelSender: PirPixelSender = mock()
    private val canRunPirFlow = MutableStateFlow(false)

    private lateinit var pirDataUpdateObserver: PirDataUpdateObserver

    @Before
    fun setUp() = runTest {
        lifecycleOwner = TestLifecycleOwner(initialState = INITIALIZED)
        whenever(pirWorkHandler.canRunPir()).thenReturn(canRunPirFlow)
        whenever(pirRepository.getFeatureReceivedMs()).thenReturn(0L)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1000L)

        pirDataUpdateObserver = PirDataUpdateObserver(
            coroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokerJsonUpdater = brokerJsonUpdater,
            pirWorkHandler = pirWorkHandler,
            pirFeatureDataCleaner = pirFeatureDataCleaner,
            pirRepository = pirRepository,
            currentTimeProvider = currentTimeProvider,
            pirPixelSender = pirPixelSender,
        )
    }

    @Test
    fun whenOnCreateAndPIRWasNeverEnabledAndIsNotEnabledThenDoNotCancelWork() = runTest {
        pirDataUpdateObserver.onCreate(lifecycleOwner)
        canRunPirFlow.value = false

        verify(pirWorkHandler, never()).cancelWork()
        verify(pirFeatureDataCleaner, never()).removeAllData()
        verify(brokerJsonUpdater, never()).update()
    }

    @Test
    fun whenOnCreateAndPIRStateChangesFromEnabledToDisabledThenCancelsWork() = runTest {
        whenever(brokerJsonUpdater.update()).thenReturn(true)
        // First call (enabled): 0L so featureReceivedMs gets set; second call (disabled): non-zero to trigger cleanup
        whenever(pirRepository.getFeatureReceivedMs()).thenReturn(0L, 1000L)

        canRunPirFlow.value = true
        pirDataUpdateObserver.onCreate(lifecycleOwner)
        verify(brokerJsonUpdater).update()

        canRunPirFlow.value = false
        verify(pirWorkHandler).cancelWork()
        verify(pirFeatureDataCleaner).removeAllData()
    }

    @Test
    fun whenOnCreateAndPIRStateChangesFromDisabledToEnabledThenUpdatesBrokerData() = runTest {
        whenever(brokerJsonUpdater.update()).thenReturn(true)

        pirDataUpdateObserver.onCreate(lifecycleOwner)

        // PIR starts disabled — featureReceivedMs is 0L so no cleanup should happen
        canRunPirFlow.value = false
        verify(pirWorkHandler, never()).cancelWork()
        verify(pirFeatureDataCleaner, never()).removeAllData()

        // Then enable PIR
        canRunPirFlow.value = true
        verify(brokerJsonUpdater).update()
    }

    @Test
    fun whenOnCreateCalledMultipleTimesAndPIREnabledThenUpdatesBrokerOnce() = runTest {
        whenever(brokerJsonUpdater.update()).thenReturn(true)
        canRunPirFlow.value = true

        pirDataUpdateObserver.onCreate(lifecycleOwner)

        // MutableStateFlow deduplicates same values, so no additional emissions
        canRunPirFlow.value = true
        canRunPirFlow.value = true
        canRunPirFlow.value = true

        verify(brokerJsonUpdater, times(1)).update()
        verifyNoInteractions(pirFeatureDataCleaner)
    }

    @Test
    fun whenOnCreateWithPIRNeverEnabledAndDisabledThenDoNotCancelWork() = runTest {
        pirDataUpdateObserver.onCreate(lifecycleOwner)

        verify(pirWorkHandler, never()).cancelWork()
        verify(brokerJsonUpdater, never()).update()
        verify(pirFeatureDataCleaner, never()).removeAllData()
    }

    @Test
    fun whenOnCreateWithPIREnabledThenUpdatesBrokers() = runTest {
        whenever(brokerJsonUpdater.update()).thenReturn(true)
        canRunPirFlow.value = true

        pirDataUpdateObserver.onCreate(lifecycleOwner)

        verify(brokerJsonUpdater).update()
        verify(pirWorkHandler, never()).cancelWork()
        verifyNoInteractions(pirFeatureDataCleaner)
    }

    @Test
    fun whenPirEnabledForFirstTimeThenSetsFeatureReceivedMs() = runTest {
        whenever(brokerJsonUpdater.update()).thenReturn(true)
        whenever(pirRepository.getFeatureReceivedMs()).thenReturn(0L)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(12345L)

        canRunPirFlow.value = true
        pirDataUpdateObserver.onCreate(lifecycleOwner)

        verify(pirRepository).setFeatureReceivedMs(12345L)
    }

    @Test
    fun whenPirAlreadyEnabledPreviouslyThenDoesNotOverwriteFeatureReceivedMs() = runTest {
        whenever(brokerJsonUpdater.update()).thenReturn(true)
        // featureReceivedMs already set from a previous session
        whenever(pirRepository.getFeatureReceivedMs()).thenReturn(5000L)

        canRunPirFlow.value = true
        pirDataUpdateObserver.onCreate(lifecycleOwner)

        verify(pirRepository, never()).setFeatureReceivedMs(any())
    }

    @Test
    fun whenPirDisabledAndFeatureReceivedMsIsZeroButEtagPresentThenCleansUp() = runTest {
        // Existing user upgrading from a version before featureReceivedMs was introduced:
        // featureReceivedMs is 0L but an etag exists, meaning broker data was previously downloaded
        whenever(pirRepository.getFeatureReceivedMs()).thenReturn(0L)
        whenever(pirRepository.getCurrentMainEtag()).thenReturn("some-etag")

        pirDataUpdateObserver.onCreate(lifecycleOwner)

        verify(pirWorkHandler).cancelWork()
        verify(pirFeatureDataCleaner).removeAllData()
    }

    @Test
    fun whenPirDisabledAndBothFeatureReceivedMsAndEtagAreUnsetThenDoesNotCleanUp() = runTest {
        // Fresh install: no featureReceivedMs and no etag — nothing to clean up
        whenever(pirRepository.getFeatureReceivedMs()).thenReturn(0L)
        whenever(pirRepository.getCurrentMainEtag()).thenReturn(null)

        pirDataUpdateObserver.onCreate(lifecycleOwner)

        verify(pirWorkHandler, never()).cancelWork()
        verify(pirFeatureDataCleaner, never()).removeAllData()
    }
}
