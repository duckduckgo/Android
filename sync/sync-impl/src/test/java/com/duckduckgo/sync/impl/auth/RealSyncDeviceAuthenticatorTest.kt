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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Request
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.AuthenticationResult
import com.duckduckgo.sync.impl.pixels.SyncPixels.AuthenticationSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class RealSyncDeviceAuthenticatorTest {
    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val deviceAuthenticator = FakeDeviceAuthenticator()
    private val syncPixels = mock<SyncPixels>()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    private val testee = RealSyncDeviceAuthenticator(
        authenticator = deviceAuthenticator,
        syncPixels = syncPixels,
        syncFeature = syncFeature,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun setUp() {
        syncFeature.canUseImprovedSyncFlow().setRawStoredState(State(enable = true))
    }

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
        deviceAuthenticator.response = Response.Failed("error")

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
    fun `when improved sync flow is disabled then do not fire pixel`() = runTest {
        syncFeature.canUseImprovedSyncFlow().setRawStoredState(State(enable = false))
        deviceAuthenticator.response = Response.Allowed.UserAuthenticated

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
        val expected = Response.Failed("error")
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
}
