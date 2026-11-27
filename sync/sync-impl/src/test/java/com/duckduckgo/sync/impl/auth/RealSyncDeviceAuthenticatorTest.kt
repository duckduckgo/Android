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

package com.duckduckgo.sync.impl.auth

import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Denial
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Denial.Reason
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.FailureReason
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Request
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.AuthenticationResult
import com.duckduckgo.sync.impl.pixels.SyncPixels.AuthenticationSource
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupMode
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupStep
import com.duckduckgo.sync.impl.wideevents.SetupFields
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class RealSyncDeviceAuthenticatorTest {
    private val deviceAuthenticator = FakeDeviceAuthenticator()
    private val syncPixels = mock<SyncPixels>()
    private val syncSetupWideEvent = mock<SyncSetupWideEvent>()

    private val testee = RealSyncDeviceAuthenticator(
        authenticator = deviceAuthenticator,
        syncPixels = syncPixels,
        syncSetupWideEvent = syncSetupWideEvent,
    )

    @Test
    fun `when user authenticates then fire succeeded pixel`() = runTest {
        deviceAuthenticator.response = Response.Allowed.UserAuthenticated

        testee.authenticate(AuthenticationSource.SETTINGS_NOT_SYNCED, Request())

        verify(syncPixels).fireSetupAuthenticationResult(AuthenticationResult.SUCCEEDED, AuthenticationSource.SETTINGS_NOT_SYNCED)
    }

    @Test
    fun `when user dismisses verification then fire cancelled pixel`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed

        testee.authenticate(AuthenticationSource.SETTINGS_NOT_SYNCED, Request())

        verify(syncPixels).fireSetupAuthenticationResult(AuthenticationResult.CANCELLED, AuthenticationSource.SETTINGS_NOT_SYNCED)
    }

    @Test
    fun `when verification fails then fire failed pixel`() = runTest {
        deviceAuthenticator.response = Response.Failed(FailureReason.HwUnavailable, "error")

        testee.authenticate(AuthenticationSource.SETTINGS_NOT_SYNCED, Request())

        verify(syncPixels).fireSetupAuthenticationResult(AuthenticationResult.FAILED, AuthenticationSource.SETTINGS_NOT_SYNCED)
    }

    @Test
    fun `when user closes enrollment then fire unconfigured pixel`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.EnrollmentClosed

        testee.authenticate(AuthenticationSource.SETTINGS_NOT_SYNCED, Request())

        verify(syncPixels).fireSetupAuthenticationResult(AuthenticationResult.UNCONFIGURED, AuthenticationSource.SETTINGS_NOT_SYNCED)
    }

    @Test
    fun `when within grace period then do not fire pixel`() = runTest {
        deviceAuthenticator.response = Response.Allowed.WithinGracePeriod

        testee.authenticate(AuthenticationSource.SETTINGS_NOT_SYNCED, Request())

        verifyNoInteractions(syncPixels)
    }

    @Test
    fun `when auth is not required for build then do not fire pixel`() = runTest {
        deviceAuthenticator.response = Response.Allowed.NotRequiredForBuild

        testee.authenticate(AuthenticationSource.SETTINGS_NOT_SYNCED, Request())

        verifyNoInteractions(syncPixels)
    }

    @Test
    fun `when authenticating then fire pixel with given source`() = runTest {
        AuthenticationSource.entries.forEach { source ->
            testee.authenticate(source, Request())

            verify(syncPixels).fireSetupAuthenticationResult(AuthenticationResult.SUCCEEDED, source)
        }
    }

    @Test
    fun `when authenticating then return device authenticator response`() = runTest {
        val expected = Response.Failed(FailureReason.HwUnavailable, "error")
        deviceAuthenticator.response = expected

        val response = testee.authenticate(AuthenticationSource.SETTINGS_NOT_SYNCED, Request())

        assertSame(expected, response)
    }

    @Test
    fun `when authenticating then forward request and events`() = runTest {
        val request = Request(verifyPromptTitle = 1)
        deviceAuthenticator.events = listOf(Event.VerificationShown)
        val events = mutableListOf<Event>()

        testee.authenticate(AuthenticationSource.SETTINGS_NOT_SYNCED, request, onEvent = { events += it })

        assertEquals(listOf(request), deviceAuthenticator.requests)
        assertEquals(listOf(Event.VerificationShown), events)
    }

    @Test
    fun `when verification prompt is shown then record device auth step`() = runTest {
        deviceAuthenticator.events = listOf(Event.VerificationShown)

        testee.authenticate(AuthenticationSource.THIS_DEVICE, Request())

        verify(syncSetupWideEvent).onStep(SetupStep.DEVICE_AUTH)
        assertTrue(reportedFields().deviceAuthShown)
    }

    @Test
    fun `when enrollment prompt is shown then do not record device auth step`() = runTest {
        deviceAuthenticator.events = listOf(Event.EnrollmentNeeded, Event.EnrollmentShown)
        deviceAuthenticator.response = Response.Cancelled.EnrollmentClosed

        testee.authenticate(AuthenticationSource.THIS_DEVICE, Request())

        verify(syncSetupWideEvent, never()).onStep(any())
        assertFalse(reportedFields().deviceAuthShown)
    }

    @Test
    fun `when user is allowed then clear device auth denial`() = runTest {
        assertNull(denialFor(Response.Allowed.UserAuthenticated))
    }

    @Test
    fun `when user is denied then report the denial`() = runTest {
        assertEquals(Denial(Reason.Unavailable, "lockout"), denialFor(Response.Failed(FailureReason.Lockout, "reason")))
    }

    private suspend fun denialFor(response: Response): Denial? {
        clearInvocations(syncSetupWideEvent)
        deviceAuthenticator.response = response

        testee.authenticate(AuthenticationSource.THIS_DEVICE, Request())

        return reportedFields().deviceAuthDenial
    }

    private fun reportedFields(): SetupFields {
        val changes = argumentCaptor<(SetupFields) -> SetupFields>()
        verify(syncSetupWideEvent, atLeastOnce()).onUpdate(changes.capture())
        // We start from a denial so a test can tell a cleared denial from one that was never reported.
        val initialFields = SetupFields(
            setupMode = SetupMode.SINGLE_DEVICE,
            flowVersion = ExchangeProtocolVersion.V2_1,
            deviceAuthDenial = Denial(Reason.Canceled, "user_canceled"),
        )
        return changes.allValues.fold(initialFields) { fields, change -> change(fields) }
    }
}
