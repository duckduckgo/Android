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

package com.duckduckgo.sync.impl.ui.setup

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.duckduckgo.sync.impl.auth.FakeDeviceAuthenticator
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.AnotherDevicePromptOption
import com.duckduckgo.sync.impl.ui.setup.SyncThisDeviceViewModel.Command.AbortSyncing
import com.duckduckgo.sync.impl.ui.setup.SyncThisDeviceViewModel.Command.FinishSyncing
import com.duckduckgo.sync.impl.ui.setup.SyncThisDeviceViewModel.Command.ShowAuthError
import com.duckduckgo.sync.impl.ui.setup.SyncThisDeviceViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.setup.SyncThisDeviceViewModel.Command.SyncWithAnotherDevice
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SyncThisDeviceViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val connectedDevice = ConnectedDevice(
        thisDevice = true,
        deviceName = "Device Name",
        deviceId = "device-id",
        deviceType = DeviceType(deviceFactor = "phone"),
    )

    private val syncAccountRepository = mock<SyncAccountRepository>()
    private val syncPixels = mock<SyncPixels>()
    private val syncSetupWideEvent = mock<SyncSetupWideEvent>()
    private val deviceAuthenticator = FakeDeviceAuthenticator()

    private fun createTestee(isAuthRequired: Boolean = true) = SyncThisDeviceViewModel(
        isAuthRequired = isAuthRequired,
        deviceAuthenticator = deviceAuthenticator,
        syncAccountRepository = syncAccountRepository,
        syncPixels = syncPixels,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        syncSetupWideEvent = syncSetupWideEvent,
    )

    @Before
    fun setup() {
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
    }

    @Test
    fun `when the user is already signed in then syncing finishes`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            assertIs<FinishSyncing>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when syncing finishes then the connected device is included`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            assertEquals(connectedDevice, (awaitItem() as FinishSyncing).device)

            cancel()
        }
    }

    @Test
    fun `when the user is already signed in then no account is created`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            verify(syncAccountRepository, never()).createAccount()
            verify(syncPixels, never()).fireSignupDirectPixel(anyOrNull())

            cancel()
        }
    }

    @Test
    fun `when the user is not signed in then an account is created and syncing finishes`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Success(true))

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            assertIs<FinishSyncing>(awaitItem())

            verify(syncAccountRepository).createAccount()
            verify(syncPixels).fireSignupDirectPixel(source = null)

            cancel()
        }
    }

    @Test
    fun `when the user is not signed in then the signup pixel is fired with the source`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Success(true))

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = "foo")
            skipItems(1)

            verify(syncPixels).fireSignupDirectPixel(source = "foo")

            cancel()
        }
    }

    @Test
    fun `when an account is created successfully then the account created event is tracked`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Success(true))

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            verify(syncSetupWideEvent).onAccountCreated()

            cancel()
        }
    }

    @Test
    fun `when syncing this device then the sync enabled event is tracked`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            verify(syncSetupWideEvent).onSyncEnabled()

            cancel()
        }
    }

    @Test
    fun `when account creation fails then the account creation failed event is tracked`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Error(1, ""))

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            verify(syncSetupWideEvent).onAccountCreationFailed()

            cancel()
        }
    }

    @Test
    fun `when account creation fails then an error is shown`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Error(1, ""))

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            assertIs<ShowError>(awaitItem())

            verify(syncPixels, never()).fireSignupDirectPixel(anyOrNull())

            cancel()
        }
    }

    @Test
    fun `when the connected device cannot be retrieved then an error is shown`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(null)

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            assertIs<ShowError>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the connected device cannot be retrieved then the account creation failed event is tracked`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(null)

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            verify(syncSetupWideEvent).onAccountCreationFailed()

            cancel()
        }
    }

    @Test
    fun `when the user chooses to sync with another device then that flow starts`() = runTest {
        val testee = createTestee()

        testee.commands.test {
            testee.onSyncWithAnotherDeviceClicked()
            assertIs<SyncWithAnotherDevice>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user closes the screen then syncing is aborted`() = runTest {
        val testee = createTestee()

        testee.commands.test {
            testee.onCloseClicked()
            assertIs<AbortSyncing>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user dismisses the error then syncing is aborted`() = runTest {
        val testee = createTestee()

        testee.commands.test {
            testee.onErrorDismissed()
            assertIs<AbortSyncing>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the view model is created then the another device prompt shown pixel is fired`() = runTest {
        createTestee()

        verify(syncPixels).fireSyncAnotherDevicePromptShown()
    }

    @Test
    fun `when the view model is created then the intro screen shown event is tracked`() = runTest {
        createTestee()

        verify(syncSetupWideEvent).onIntroScreenShown()
    }

    @Test
    fun `when syncing this device then the this device only option pixel is fired`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            verify(syncPixels).fireSyncAnotherDevicePromptOptionTapped(AnotherDevicePromptOption.THIS_DEVICE_ONLY)

            cancel()
        }
    }

    @Test
    fun `when the user chooses to sync with another device then the another device option pixel is fired`() = runTest {
        val testee = createTestee()

        testee.commands.test {
            testee.onSyncWithAnotherDeviceClicked()
            skipItems(1)

            verify(syncPixels).fireSyncAnotherDevicePromptOptionTapped(AnotherDevicePromptOption.WITH_ANOTHER_DEVICE)

            cancel()
        }
    }

    @Test
    fun `when auth is required and the user syncs this device then the user is asked to authenticate`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            assertEquals(1, deviceAuthenticator.requests.size)

            cancel()
        }
    }

    @Test
    fun `when auth is not required and the user syncs this device then the user is not asked to authenticate`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        val testee = createTestee(isAuthRequired = false)

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            assertIs<FinishSyncing>(awaitItem())

            assertTrue(deviceAuthenticator.requests.isEmpty())

            cancel()
        }
    }

    @Test
    fun `when the user dismisses verification while syncing this device then syncing does not start`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            expectNoEvents()

            verify(syncSetupWideEvent, never()).onSyncEnabled()
            verify(syncAccountRepository, never()).createAccount()

            cancel()
        }
    }

    @Test
    fun `when the user closes enrollment while syncing this device then syncing does not start`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.EnrollmentClosed

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            expectNoEvents()

            verify(syncSetupWideEvent, never()).onSyncEnabled()
            verify(syncAccountRepository, never()).createAccount()

            cancel()
        }
    }

    @Test
    fun `when the user dismisses verification while syncing this device then the syncing state is not shown`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed

        val testee = createTestee()

        testee.viewState.test {
            assertFalse(awaitItem().isSyncing)

            testee.syncThisDevice(launchSource = null)
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when authentication is pending while syncing this device then the syncing state is not shown`() = runTest {
        deviceAuthenticator.suspendResponse()

        val testee = createTestee()

        testee.viewState.test {
            assertFalse(awaitItem().isSyncing)

            testee.syncThisDevice(launchSource = null)
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when syncing this device is already in progress then another tap is ignored`() = runTest {
        deviceAuthenticator.suspendResponse()

        val testee = createTestee()

        testee.syncThisDevice(launchSource = null)
        testee.syncThisDevice(launchSource = null)

        assertEquals(1, deviceAuthenticator.requests.size)
        verify(syncPixels).fireSyncAnotherDevicePromptOptionTapped(AnotherDevicePromptOption.THIS_DEVICE_ONLY)
    }

    @Test
    fun `when syncing this device was cancelled then another tap starts it again`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed

        val testee = createTestee()

        testee.syncThisDevice(launchSource = null)
        testee.syncThisDevice(launchSource = null)

        assertEquals(2, deviceAuthenticator.requests.size)
    }

    @Test
    fun `when authentication fails while syncing this device then an auth error is shown`() = runTest {
        deviceAuthenticator.response = Response.Failed("reason")

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            assertIs<ShowAuthError>(awaitItem())

            verify(syncAccountRepository, never()).createAccount()

            cancel()
        }
    }

    @Test
    fun `when auth is required and the user chooses to sync with another device then the user is asked to authenticate`() = runTest {
        val testee = createTestee()

        testee.commands.test {
            testee.onSyncWithAnotherDeviceClicked()
            skipItems(1)

            assertEquals(1, deviceAuthenticator.requests.size)

            cancel()
        }
    }

    @Test
    fun `when auth is not required and the user chooses to sync with another device then the user is not asked to authenticate`() = runTest {
        val testee = createTestee(isAuthRequired = false)

        testee.commands.test {
            testee.onSyncWithAnotherDeviceClicked()
            assertIs<SyncWithAnotherDevice>(awaitItem())

            assertTrue(deviceAuthenticator.requests.isEmpty())

            cancel()
        }
    }

    @Test
    fun `when the user dismisses verification while choosing to sync with another device then that flow does not start`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed

        val testee = createTestee()

        testee.commands.test {
            testee.onSyncWithAnotherDeviceClicked()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when authentication fails while choosing to sync with another device then an auth error is shown`() = runTest {
        deviceAuthenticator.response = Response.Failed("reason")

        val testee = createTestee()

        testee.commands.test {
            testee.onSyncWithAnotherDeviceClicked()
            assertIs<ShowAuthError>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user authenticates then the user auth success event is tracked`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        deviceAuthenticator.response = Response.Allowed.UserAuthenticated

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            verify(syncSetupWideEvent).onUserAuthSuccess()

            cancel()
        }
    }

    @Test
    fun `when the user is within the grace period then the user auth success event is tracked`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        deviceAuthenticator.response = Response.Allowed.WithinGracePeriod

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            verify(syncSetupWideEvent).onUserAuthSuccess()

            cancel()
        }
    }

    @Test
    fun `when the user dismisses verification then no auth outcome event is tracked`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed

        val testee = createTestee()
        testee.syncThisDevice(launchSource = null)

        verify(syncSetupWideEvent, never()).onUserAuthCancelled()
        verify(syncSetupWideEvent, never()).onUserAuthSuccess()
    }

    @Test
    fun `when authentication fails then no auth outcome event is tracked`() = runTest {
        deviceAuthenticator.response = Response.Failed("reason")

        val testee = createTestee()

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            verify(syncSetupWideEvent, never()).onUserAuthCancelled()
            verify(syncSetupWideEvent, never()).onUserAuthSuccess()

            cancel()
        }
    }

    @Test
    fun `when the device has no screen lock then the device auth not enrolled event is tracked`() = runTest {
        deviceAuthenticator.events = listOf(Event.EnrollmentNeeded, Event.EnrollmentShown)
        deviceAuthenticator.response = Response.Cancelled.EnrollmentClosed

        val testee = createTestee()
        testee.syncThisDevice(launchSource = null)

        verify(syncSetupWideEvent).onDeviceAuthNotEnrolled()
        verify(syncSetupWideEvent).onEnrollDeviceAuthDialogShown()
    }

    @Test
    fun `when the user closes enrollment then no auth outcome event is tracked`() = runTest {
        deviceAuthenticator.events = listOf(Event.EnrollmentNeeded, Event.EnrollmentShown)
        deviceAuthenticator.response = Response.Cancelled.EnrollmentClosed

        val testee = createTestee()
        testee.syncThisDevice(launchSource = null)

        verify(syncSetupWideEvent, never()).onUserAuthSuccess()
        verify(syncSetupWideEvent, never()).onUserAuthCancelled()
    }

    @Test
    fun `when the user chooses to sync with another device and authenticates then the user auth success event is tracked`() = runTest {
        val testee = createTestee()

        testee.commands.test {
            testee.onSyncWithAnotherDeviceClicked()
            skipItems(1)

            verify(syncSetupWideEvent).onUserAuthSuccess()

            cancel()
        }
    }

    @Test
    fun `when auth is not required then no auth events are tracked`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        val testee = createTestee(isAuthRequired = false)

        testee.commands.test {
            testee.syncThisDevice(launchSource = null)
            skipItems(1)

            verify(syncSetupWideEvent, never()).onUserAuthSuccess()
            verify(syncSetupWideEvent, never()).onUserAuthCancelled()

            cancel()
        }
    }
}

private inline fun <reified T> assertIs(value: Any?) {
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
