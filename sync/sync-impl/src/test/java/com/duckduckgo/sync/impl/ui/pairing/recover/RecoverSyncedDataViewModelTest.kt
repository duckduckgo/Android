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

package com.duckduckgo.sync.impl.ui.pairing.recover

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.duckduckgo.sync.impl.auth.FakeDeviceAuthenticator
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.pairing.recover.RecoverSyncedDataViewModel.Command.ReadSyncCode
import com.duckduckgo.sync.impl.ui.pairing.recover.RecoverSyncedDataViewModel.Command.ShowAuthError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RecoverSyncedDataViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val deviceAuthenticator = FakeDeviceAuthenticator()
    private val syncPixels = mock<SyncPixels>()

    private fun createTestee(isAuthRequired: Boolean = true) = RecoverSyncedDataViewModel(
        isAuthRequired = isAuthRequired,
        deviceAuthenticator = deviceAuthenticator,
        syncPixels = syncPixels,
    )

    @Test
    fun `when the user taps recover then the recover confirmed pixel is fired`() = runTest {
        val testee = createTestee()

        testee.commands.test {
            testee.onRecoverDataClicked()
            skipItems(1)

            verify(syncPixels).fireRecoverSyncDataConfirmed()

            cancel()
        }
    }

    @Test
    fun `when auth is not required then the sync code is read`() = runTest {
        val testee = createTestee(isAuthRequired = false)

        testee.commands.test {
            testee.onRecoverDataClicked()
            assertEquals(ReadSyncCode, awaitItem())

            cancel()
        }
    }

    @Test
    fun `when auth is not required then the user is not asked to authenticate`() = runTest {
        val testee = createTestee(isAuthRequired = false)

        testee.commands.test {
            testee.onRecoverDataClicked()
            skipItems(1)

            assertTrue(deviceAuthenticator.requests.isEmpty())

            cancel()
        }
    }

    @Test
    fun `when auth is required then the user is asked to authenticate`() = runTest {
        val testee = createTestee()

        testee.commands.test {
            testee.onRecoverDataClicked()
            skipItems(1)

            assertEquals(1, deviceAuthenticator.requests.size)

            cancel()
        }
    }

    @Test
    fun `when the user authenticates then the sync code is read`() = runTest {
        deviceAuthenticator.response = Response.Allowed.UserAuthenticated
        val testee = createTestee()

        testee.commands.test {
            testee.onRecoverDataClicked()
            assertEquals(ReadSyncCode, awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user is within the grace period then the sync code is read`() = runTest {
        deviceAuthenticator.response = Response.Allowed.WithinGracePeriod
        val testee = createTestee()

        testee.commands.test {
            testee.onRecoverDataClicked()
            assertEquals(ReadSyncCode, awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user dismisses verification then nothing happens`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed
        val testee = createTestee()

        testee.commands.test {
            testee.onRecoverDataClicked()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the user closes enrollment then nothing happens`() = runTest {
        deviceAuthenticator.response = Response.Cancelled.EnrollmentClosed
        val testee = createTestee()

        testee.commands.test {
            testee.onRecoverDataClicked()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when authentication fails then an auth error is shown`() = runTest {
        deviceAuthenticator.response = Response.Failed("reason")
        val testee = createTestee()

        testee.commands.test {
            testee.onRecoverDataClicked()
            assertEquals(ShowAuthError, awaitItem())

            cancel()
        }
    }
}
