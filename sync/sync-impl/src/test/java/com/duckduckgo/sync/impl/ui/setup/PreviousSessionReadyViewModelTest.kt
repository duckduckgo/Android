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
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.duckduckgo.sync.impl.auth.FakeSyncDeviceAuthenticator
import com.duckduckgo.sync.impl.autorestore.RestorePayload
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.AuthenticationSource
import com.duckduckgo.sync.impl.ui.setup.PreviousSessionReadyViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.PreviousSessionReadyViewModel.Command.SetContinueSetupResult
import com.duckduckgo.sync.impl.ui.setup.PreviousSessionReadyViewModel.Command.SetResumeResult
import com.duckduckgo.sync.impl.ui.setup.PreviousSessionReadyViewModel.Command.ShowError
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PreviousSessionReadyViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val restorePayload = RestorePayload(
        recoveryCode = "recovery-code",
        deviceId = "device-id",
    )

    private val syncAutoRestoreManager = mock<SyncAutoRestoreManager>()
    private val deviceAuthenticator = FakeSyncDeviceAuthenticator()
    private val syncPixels = mock<SyncPixels>()
    private val syncSetupWideEvent = mock<SyncSetupWideEvent>()

    private val testee = createTestee(isAuthRequired = true)

    private fun createTestee(isAuthRequired: Boolean) = PreviousSessionReadyViewModel(
        "sync_backup",
        isAuthRequired,
        syncAutoRestoreManager,
        deviceAuthenticator,
        syncPixels,
        syncSetupWideEvent,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when the screen is shown then the ready shown pixel is fired with the source`() = runTest {
        testee.onScreenShown()

        verify(syncPixels).fireAutoRestoreSettingsReadyShown(source = "sync_backup")
    }

    @Test
    fun `when resume is clicked and a payload exists then the resume result is set and the screen closes`() = runTest {
        whenever(syncAutoRestoreManager.retrieveRecoveryPayload()).thenReturn(restorePayload)

        testee.commands.test {
            testee.onResumeClicked()
            assertEquals("recovery-code", (awaitItem() as SetResumeResult).recoveryCode)
            assertIs<Close>(awaitItem())

            cancel()
        }

        verify(syncPixels).fireAutoRestoreSettingsRestoreTapped(source = "sync_backup")
        verify(syncSetupWideEvent).onSyncRestoreStarted()
    }

    @Test
    fun `when resume is clicked and the payload is missing then an error is shown`() = runTest {
        whenever(syncAutoRestoreManager.retrieveRecoveryPayload()).thenReturn(null)

        testee.commands.test {
            testee.onResumeClicked()
            assertIs<ShowError>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when continue setup is clicked then the continue setup result is set and the screen closes`() = runTest {
        testee.commands.test {
            testee.onContinueSetupClicked()
            assertIs<SetContinueSetupResult>(awaitItem())
            assertIs<Close>(awaitItem())

            cancel()
        }

        verify(syncPixels).fireAutoRestoreSettingsSkipRestoreTapped(source = "sync_backup")
    }

    @Test
    fun `when close is clicked then the screen closes`() = runTest {
        testee.commands.test {
            testee.onCloseClicked()
            assertIs<Close>(awaitItem())

            cancel()
        }

        verify(syncPixels).fireAutoRestoreSettingsCancelled(source = "sync_backup")
    }

    @Test
    fun `when resume is clicked then the user is asked to authenticate`() = runTest {
        whenever(syncAutoRestoreManager.retrieveRecoveryPayload()).thenReturn(restorePayload)

        testee.commands.test {
            testee.onResumeClicked()
            skipItems(2)

            assertEquals(1, deviceAuthenticator.requests.size)

            cancel()
        }
    }

    @Test
    fun `when continue setup is clicked then the user is asked to authenticate`() = runTest {
        testee.commands.test {
            testee.onContinueSetupClicked()
            skipItems(2)

            assertEquals(1, deviceAuthenticator.requests.size)

            cancel()
        }
    }

    @Test
    fun `when the user is asked to authenticate then the session restore source is used`() = runTest {
        testee.commands.test {
            testee.onContinueSetupClicked()
            skipItems(2)

            assertEquals(listOf(AuthenticationSource.SESSION_RESTORE), deviceAuthenticator.sources)

            cancel()
        }
    }

    @Test
    fun `when auth is not required and resume is clicked then the user is not asked to authenticate`() = runTest {
        whenever(syncAutoRestoreManager.retrieveRecoveryPayload()).thenReturn(restorePayload)
        val testee = createTestee(isAuthRequired = false)

        testee.commands.test {
            testee.onResumeClicked()
            skipItems(2)

            assertTrue(deviceAuthenticator.requests.isEmpty())

            cancel()
        }
    }

    @Test
    fun `when auth is not required and continue setup is clicked then the user is not asked to authenticate`() = runTest {
        val testee = createTestee(isAuthRequired = false)

        testee.commands.test {
            testee.onContinueSetupClicked()
            skipItems(2)

            assertTrue(deviceAuthenticator.requests.isEmpty())
            verify(syncSetupWideEvent, never()).onUserAuthSuccess()

            cancel()
        }
    }

    @Test
    fun `when the user dismisses verification on resume then the restore does not start`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed

        testee.commands.test {
            testee.onResumeClicked()
            expectNoEvents()

            cancel()
        }

        verify(syncSetupWideEvent, never()).onSyncRestoreStarted()
    }

    @Test
    fun `when the user dismisses verification on continue setup then the setup does not continue`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed

        testee.commands.test {
            testee.onContinueSetupClicked()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the user closes enrollment on continue setup then the setup does not continue`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.EnrollmentClosed

        testee.commands.test {
            testee.onContinueSetupClicked()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when authentication fails on continue setup then an error is shown`() = runTest {
        deviceAuthenticator.response = Response.Failed("reason")

        testee.commands.test {
            testee.onContinueSetupClicked()
            assertIs<ShowError>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when authentication is pending then another tap is ignored`() = runTest {
        deviceAuthenticator.suspendResponse()

        testee.onContinueSetupClicked()
        testee.onResumeClicked()

        assertEquals(1, deviceAuthenticator.requests.size)
    }

    @Test
    fun `when the user authenticates then the user auth success event is tracked`() = runTest {
        deviceAuthenticator.response = Response.Allowed.UserAuthenticated

        testee.commands.test {
            testee.onContinueSetupClicked()
            skipItems(2)

            verify(syncSetupWideEvent).onUserAuthSuccess()

            cancel()
        }
    }

    @Test
    fun `when the user is within the grace period then the user auth success event is tracked`() = runTest {
        deviceAuthenticator.response = Response.Allowed.WithinGracePeriod

        testee.commands.test {
            testee.onContinueSetupClicked()
            skipItems(2)

            verify(syncSetupWideEvent).onUserAuthSuccess()

            cancel()
        }
    }

    @Test
    fun `when the user dismisses verification then no auth outcome event is tracked`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed

        testee.onContinueSetupClicked()

        verify(syncSetupWideEvent, never()).onUserAuthCancelled()
        verify(syncSetupWideEvent, never()).onUserAuthSuccess()
    }

    @Test
    fun `when authentication fails then no auth outcome event is tracked`() = runTest {
        deviceAuthenticator.response = Response.Failed("reason")

        testee.commands.test {
            testee.onContinueSetupClicked()
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

        testee.onContinueSetupClicked()

        verify(syncSetupWideEvent).onDeviceAuthNotEnrolled()
    }

    @Test
    fun `when the enroll dialog is shown then the setup flow is not aborted`() = runTest {
        deviceAuthenticator.events = listOf(Event.EnrollmentNeeded, Event.EnrollmentShown)
        deviceAuthenticator.response = Response.Cancelled.EnrollmentClosed

        testee.onContinueSetupClicked()

        verify(syncSetupWideEvent, never()).onEnrollDeviceAuthDialogShown()
    }
}

private inline fun <reified T> assertIs(value: Any?) {
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
