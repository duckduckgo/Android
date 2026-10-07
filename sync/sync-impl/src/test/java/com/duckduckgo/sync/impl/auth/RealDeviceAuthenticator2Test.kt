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

import com.duckduckgo.sync.impl.SyncBuildConfig
import com.duckduckgo.sync.impl.auth.AuthPrompt.Enroll
import com.duckduckgo.sync.impl.auth.AuthPrompt.Verify
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator2.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator2.Request
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator2.Response
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Suppress("DeferredResultUnused")
@OptIn(ExperimentalCoroutinesApi::class)
class RealDeviceAuthenticator2Test {

    private var supportsStrongAuthentication = true
    private var supportsLegacyAuthentication = true
    private val deviceAuthChecker = mock<SupportedDeviceAuthChecker> {
        on { supportsStrongAuthentication() } doAnswer { supportsStrongAuthentication }
        on { supportsLegacyAuthentication() } doAnswer { supportsLegacyAuthentication }
    }

    private var currentTimeMillis = 0L
    private val gracePeriod = TimeBasedDeviceAuthorizationGracePeriod(
        timeProvider = mock {
            on { currentTimeMillis() } doAnswer { currentTimeMillis }
        },
    )

    private var isAuthRequired = true
    private var sdkInt = 34
    private val buildConfig = mock<SyncBuildConfig> {
        on { isAuthRequired } doAnswer { isAuthRequired }
        on { sdkInt } doAnswer { sdkInt }
    }

    private val authEvents = mutableListOf<Event>()

    private val testee = RealDeviceAuthenticator2(
        deviceAuthChecker = deviceAuthChecker,
        gracePeriod = gracePeriod,
        buildConfig = buildConfig,
    )

    @Test
    fun `when device has no screen lock then show enroll prompt`() = runTest {
        supportsStrongAuthentication = false

        authenticateAsync()

        assertIs<Enroll>(testee.currentPrompt.value)
    }

    @Test
    fun `when enroll prompt is shown then report enrollment shown at most once`() = runTest {
        supportsStrongAuthentication = false

        authenticateAsync()
        withPrompt<Enroll> {
            onShown()
            onShown()
        }

        assertEquals(listOf(Event.EnrollmentNeeded, Event.EnrollmentShown), authEvents)
    }

    @Test
    fun `when enroll prompt is closed then respond enrollment closed at most once`() = runTest {
        supportsStrongAuthentication = false

        val response = authenticateAsync()
        withPrompt<Enroll> {
            onClosed()
            onClosed()
        }

        assertEquals(Response.Cancelled.EnrollmentClosed, response.await())
        assertNull(testee.currentPrompt.value)
    }

    @Test
    fun `when device has screen lock then show verify prompt`() = runTest {
        authenticateAsync()

        assertIs<Verify>(testee.currentPrompt.value)
    }

    @Test
    fun `when verify prompt is shown then report verification shown at most once`() = runTest {
        authenticateAsync()

        withPrompt<Verify> {
            onShown()
            onShown()
        }

        assertEquals(listOf(Event.VerificationShown), authEvents)
    }

    @Test
    fun `when user is verified then allow with authentication at most once`() = runTest {
        val response = authenticateAsync()

        withPrompt<Verify> {
            onVerified()
            onVerified()
        }

        assertEquals(Response.Allowed.UserAuthenticated, response.await())
        assertNull(testee.currentPrompt.value)
    }

    @Test
    fun `when user cancels verification then respond cancelled at most once`() = runTest {
        val response = authenticateAsync()

        withPrompt<Verify> {
            onCancelled()
            onCancelled()
        }

        assertEquals(Response.Cancelled.VerificationDismissed, response.await())
        assertNull(testee.currentPrompt.value)
    }

    @Test
    fun `when verification fails then respond failed at most once`() = runTest {
        val response = authenticateAsync()

        withPrompt<Verify> {
            onError("reason")
            onError("other reason")
        }

        assertEquals(Response.Failed("reason"), response.await())
        assertNull(testee.currentPrompt.value)
    }

    @Test
    fun `when outside grace period then show verify prompt`() = runTest {
        gracePeriod.recordSuccessfulAuthorization()
        currentTimeMillis += 15_001

        authenticateAsync()

        assertIs<Verify>(testee.currentPrompt.value)
    }

    @Test
    fun `when within grace period then allow without prompt`() = runTest {
        gracePeriod.recordSuccessfulAuthorization()

        val response = testee.authenticate(Request())

        assertEquals(Response.Allowed.WithinGracePeriod, response)
        assertNull(testee.currentPrompt.value)
    }

    @Test
    fun `when user is verified then start grace period`() = runTest {
        authenticateAsync()

        withPrompt<Verify> { onVerified() }

        assertFalse(gracePeriod.isAuthRequired())
    }

    @Test
    fun `when user cancels verification then do not start grace period`() = runTest {
        authenticateAsync()

        withPrompt<Verify> { onCancelled() }

        assertTrue(gracePeriod.isAuthRequired())
    }

    @Test
    fun `when verification fails then do not start grace period`() = runTest {
        authenticateAsync()

        withPrompt<Verify> { onError("reason") }

        assertTrue(gracePeriod.isAuthRequired())
    }

    @Test
    fun `when prompt is answered then keep the first answer`() = runTest {
        val response = authenticateAsync()
        withPrompt<Verify> {
            onCancelled()
            onVerified()
        }

        assertEquals(Response.Cancelled.VerificationDismissed, response.await())
        assertTrue(gracePeriod.isAuthRequired())
    }

    @Test
    fun `when authentication is cancelled then clear prompt`() = runTest {
        val response = authenticateAsync()

        response.cancel()
        runCurrent()

        assertNull(testee.currentPrompt.value)
    }

    @Test
    fun `when authentication is pending then next authentication waits for it`() = runTest {
        val first = authenticateAsync()
        val firstPrompt = testee.currentPrompt.value
        val second = authenticateAsync()

        assertEquals(firstPrompt, testee.currentPrompt.value)
        withPrompt<Verify> { onCancelled() }
        assertEquals(Response.Cancelled.VerificationDismissed, first.await())

        assertNotEquals(firstPrompt, testee.currentPrompt.value)
        withPrompt<Verify> { onVerified() }
        assertEquals(Response.Allowed.UserAuthenticated, second.await())
    }

    @Test
    fun `when on API 28 then use legacy authentication check`() = runTest {
        sdkInt = 28
        supportsStrongAuthentication = true
        supportsLegacyAuthentication = false

        authenticateAsync()

        assertIs<Enroll>(testee.currentPrompt.value)
    }

    @Test
    fun `when on API 29 then use legacy authentication check`() = runTest {
        sdkInt = 29
        supportsStrongAuthentication = true
        supportsLegacyAuthentication = false

        authenticateAsync()

        assertIs<Enroll>(testee.currentPrompt.value)
    }

    @Test
    fun `when on API other than 28 or 29 then use strong authentication check`() = runTest {
        sdkInt = 30
        supportsStrongAuthentication = false
        supportsLegacyAuthentication = true

        authenticateAsync()

        assertIs<Enroll>(testee.currentPrompt.value)
    }

    @Test
    fun `when auth is not required then allow without authentication`() = runTest {
        isAuthRequired = false

        val response = testee.authenticate(Request()) { authEvents += it }

        assertEquals(Response.Allowed.NotRequiredForBuild, response)
        assertNull(testee.currentPrompt.value)
        assertTrue(authEvents.isEmpty())
    }

    private fun TestScope.authenticateAsync(): Deferred<Response> {
        val response = backgroundScope.async { testee.authenticate(Request()) { authEvents += it } }
        runCurrent()
        return response
    }

    private inline fun <reified T : AuthPrompt> TestScope.withPrompt(block: T.() -> Unit) {
        val prompt = testee.currentPrompt.value
        assertIs<T>(prompt)
        prompt.block()
        runCurrent()
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <reified T> assertIs(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
