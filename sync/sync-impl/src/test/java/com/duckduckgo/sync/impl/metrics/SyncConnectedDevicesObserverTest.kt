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

package com.duckduckgo.sync.impl.metrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.TestSyncFixtures.connectedDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SyncConnectedDevicesObserverTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var observer: SyncConnectedDevicesObserver

    @Before
    fun setup() {
        observer = SyncConnectedDevicesObserver(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenNoDevicesUpdatedThenEmitsZero() = runTest {
        observer.observeConnectedDevicesCount().test {
            assertEquals(0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDevicesUpdatedThenEmitsCorrectCount() = runTest {
        val devices = listOf(
            connectedDevice.copy(deviceId = "device1", thisDevice = true),
            connectedDevice.copy(deviceId = "device2"),
        )

        observer.observeConnectedDevicesCount().test {
            assertEquals(0, awaitItem())
            observer.onDevicesUpdated(devices)
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDevicesUpdatedMultipleTimesThenEmitsLatestCount() = runTest {
        val devices1 = listOf(connectedDevice)

        val devices2 = listOf(
            connectedDevice.copy(deviceId = "device1", thisDevice = true),
            connectedDevice.copy(deviceId = "device2"),
            connectedDevice.copy(deviceId = "device3"),
        )

        observer.observeConnectedDevicesCount().test {
            assertEquals(0, awaitItem())

            observer.onDevicesUpdated(devices1)
            assertEquals(1, awaitItem())

            observer.onDevicesUpdated(devices2)
            assertEquals(3, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
