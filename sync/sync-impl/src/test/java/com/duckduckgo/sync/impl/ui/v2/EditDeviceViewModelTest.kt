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

package com.duckduckgo.sync.impl.ui.v2

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.Close
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditDeviceViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val device = ConnectedDevice(
        thisDevice = true,
        deviceName = "Device Name",
        deviceId = "device-id",
        deviceType = DeviceType(deviceFactor = "phone"),
    )

    private val testee = EditDeviceViewModel(device)

    @Test
    fun `when the view state is observed then it emits the device it was created with`() = runTest {
        testee.viewState.test {
            assertEquals(device, awaitItem().device)

            cancel()
        }
    }

    @Test
    fun `when the user closes the screen then the close command is sent`() = runTest {
        testee.commands.test {
            testee.onCloseClicked()
            assertIs<Close>(awaitItem())

            cancel()
        }
    }
}

private inline fun <reified T> assertIs(value: Any?) {
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
