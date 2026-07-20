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
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.AskRemoveDevice
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.ResetTurnOffSyncToggle
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.SetRemoveDeviceResult
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.SetTurnOffSyncResult
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.ShowError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class EditDeviceViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val device = ConnectedDevice(
        thisDevice = true,
        deviceName = "Device Name",
        deviceId = "device-id",
        deviceType = DeviceType(deviceFactor = "phone"),
    )

    private val syncAccountRepository = mock<SyncAccountRepository>()

    private val testee = EditDeviceViewModel(
        device = device,
        syncAccountRepository = syncAccountRepository,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when the view state is observed then it emits the device it was created with`() = runTest {
        testee.viewState.test {
            assertEquals(device, awaitItem().device)

            cancel()
        }
    }

    @Test
    fun `when the user edits the device name then the edit dialog is requested`() = runTest {
        testee.commands.test {
            testee.onEditDeviceName()
            assertIs<AskEditDevice>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when a new device name is confirmed then the device is renamed in the repository`() = runTest {
        whenever(syncAccountRepository.renameDevice(any())).thenReturn(Result.Success(true))

        testee.confirmNewDeviceName("New Device Name")

        verify(syncAccountRepository).renameDevice(device.copy(deviceName = "New Device Name"))
    }

    @Test
    fun `when renaming the device succeeds then the view state reflects the new name`() = runTest {
        whenever(syncAccountRepository.renameDevice(any())).thenReturn(Result.Success(true))

        testee.confirmNewDeviceName("New Device Name")

        assertEquals("New Device Name", testee.viewState.value.device.deviceName)
    }

    @Test
    fun `when renaming the device fails then the toggle is reset and an error is shown`() = runTest {
        whenever(syncAccountRepository.renameDevice(any())).thenReturn(Result.Error(reason = "boom"))

        testee.commands.test {
            testee.confirmNewDeviceName("New Device Name")

            val command = awaitItem()
            assertIs<ShowError>(command)
            assertEquals(R.string.sync_edit_device_error, command.message)
            assertEquals("boom", command.reason)

            cancel()
        }
    }

    @Test
    fun `when renaming the device fails then the device name is not updated`() = runTest {
        whenever(syncAccountRepository.renameDevice(any())).thenReturn(Result.Error(reason = "boom"))

        testee.confirmNewDeviceName("New Device Name")

        assertEquals(device, testee.viewState.value.device)
    }

    @Test
    fun `when the user turns off sync then confirmation is requested`() = runTest {
        testee.commands.test {
            testee.onTurnOffSync()
            assertIs<AskTurnOffSync>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when turning off sync is confirmed then the result is set and the screen closes`() = runTest {
        testee.commands.test {
            testee.onTurnOffSyncConfirmed()
            assertIs<SetTurnOffSyncResult>(awaitItem())
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when turning off sync is canceled then the toggle is reset`() = runTest {
        testee.commands.test {
            testee.onTurnOffSyncCanceled()
            assertIs<ResetTurnOffSyncToggle>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user removes the device then confirmation is requested`() = runTest {
        testee.commands.test {
            testee.onRemoveDevice()
            assertIs<AskRemoveDevice>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when removing the device is confirmed then the result is set and the screen closes`() = runTest {
        testee.commands.test {
            testee.onRemoveDeviceConfirmed()
            assertIs<SetRemoveDeviceResult>(awaitItem())
            assertIs<Close>(awaitItem())

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

@OptIn(ExperimentalContracts::class)
private inline fun <reified T> assertIs(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
