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

package com.duckduckgo.browsermode.impl.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.Profile
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class RealWebViewProfileManagerTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private val fireModeAvailability: FireModeAvailability = mock()
    private val dataStore: WebViewProfileDataStore = mock()
    private val migrationManager: WebViewProfileMigrationManager = mock()

    private fun newManager() = RealWebViewProfileManager(
        fireModeAvailability,
        dataStore,
        migrationManager,
        coroutineRule.testDispatcherProvider,
        coroutineRule.testScope,
    )

    @Before
    fun setUp() {
        // Default to "not available" — tests that need different behaviour override.
        fireModeAvailability.stub { onBlocking { isAvailable() }.thenReturn(false) }
    }

    @Test
    fun `getProfileName suspends until initialize completes`() = runTest {
        val testee = newManager()

        val pending = async { testee.getProfileName(BrowserMode.REGULAR) }
        assertFalse(pending.isCompleted)

        testee.initialize()
        assertEquals(Profile.DEFAULT_PROFILE_NAME, pending.await())
    }

    @Test
    fun `getWebStorage suspends until initialize completes`() = runTest {
        val testee = newManager()

        val pending = async { testee.getWebStorage(BrowserMode.REGULAR) }
        assertFalse(pending.isCompleted)

        testee.initialize()
        assertNotNull(pending.await())
    }

    @Test
    fun `getCookieManager suspends until initialize completes`() = runTest {
        val testee = newManager()

        val pending = async { testee.getCookieManager(BrowserMode.REGULAR) }
        assertFalse(pending.isCompleted)

        testee.initialize()
        assertNotNull(pending.await())
    }

    @Test
    fun `initialize with multi-profile unavailable returns Default profile name`() = runTest {
        val testee = newManager()

        testee.initialize()

        assertEquals(Profile.DEFAULT_PROFILE_NAME, testee.getProfileName(BrowserMode.REGULAR))
        assertEquals(Profile.DEFAULT_PROFILE_NAME, testee.getProfileName(BrowserMode.FIRE))
    }

    @Test
    fun `initialize does not touch DataStore when multi-profile unavailable`() = runTest {
        val testee = newManager()

        testee.initialize()

        verify(dataStore, never()).getProfileIndex(BrowserMode.REGULAR)
        verify(dataStore, never()).getProfileIndex(BrowserMode.FIRE)
    }

    @Test
    fun `clear returns false when multi-profile unavailable`() = runTest {
        val testee = newManager()
        testee.initialize()

        assertFalse(testee.clearAndRotateProfile(BrowserMode.FIRE))
        assertFalse(testee.clearAndRotateProfile(BrowserMode.REGULAR))
    }

    @Test
    fun `clear does not consult DataStore or migrationManager when unavailable`() = runTest {
        val testee = newManager()
        testee.initialize()

        testee.clearAndRotateProfile(BrowserMode.FIRE)

        verify(dataStore, never()).incrementProfileIndex(BrowserMode.FIRE)
        verify(migrationManager, never()).migrate(anyProfile(), anyProfile())
    }

    @Test
    fun `cleanupStaleProfiles is a no-op when unavailable`() = runTest {
        val testee = newManager()
        testee.initialize()

        testee.cleanupStaleProfiles() // should not throw
    }

    @Test
    fun `initialize completes latch even when initialization throws so callers do not hang`() = runTest {
        val testee = newManager()
        fireModeAvailability.stub { onBlocking { isAvailable() }.thenThrow(RuntimeException("boom")) }

        try {
            testee.initialize()
            fail("Expected initialize() to rethrow")
        } catch (expected: RuntimeException) {
            // Latch must be completed in `finally` so awaiters do not suspend forever.
        }

        assertEquals(Profile.DEFAULT_PROFILE_NAME, testee.getProfileName(BrowserMode.REGULAR))
        assertNotNull(testee.getWebStorage(BrowserMode.REGULAR))
        assertNotNull(testee.getCookieManager(BrowserMode.REGULAR))
    }
}

private fun anyProfile(): Profile = any()
