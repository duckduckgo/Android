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

package com.duckduckgo.app.browser.menu

import android.content.SharedPreferences
import app.cash.turbine.test
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.downloads.api.NewDownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DownloadMenuHighlightPluginTest {

    private val hasNewDownloadFlow = MutableStateFlow(false)
    private val newDownloadState: NewDownloadState = mock()

    private val hydrationScheduler = TestCoroutineScheduler()
    private val hydrationDispatcher = StandardTestDispatcher(hydrationScheduler)

    @Test
    fun `when a new download completes then the icon dot is raised`() = runTest {
        val testee = createTestee()
        hydrationScheduler.advanceUntilIdle()
        hasNewDownloadFlow.value = true

        testee.isHighlighted(BrowserViewMode.Browser).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when mode is CustomTab then the icon dot is not raised`() = runTest {
        val testee = createTestee()
        hydrationScheduler.advanceUntilIdle()
        hasNewDownloadFlow.value = true

        testee.isHighlighted(BrowserViewMode.CustomTab).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when the download is acknowledged then the icon dot is suppressed`() = runTest {
        val testee = createTestee()
        val acknowledgement = RealBrowserMenuAcknowledgement(testee)
        hydrationScheduler.advanceUntilIdle()
        hasNewDownloadFlow.value = true

        testee.isHighlighted(BrowserViewMode.Browser).test {
            assertTrue(awaitItem())
            acknowledgement.onBrowserMenuViewed(BrowserViewMode.Browser)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when acknowledged from a custom tab then the icon dot is not suppressed`() = runTest {
        val testee = createTestee()
        val acknowledgement = RealBrowserMenuAcknowledgement(testee)
        hydrationScheduler.advanceUntilIdle()
        hasNewDownloadFlow.value = true

        testee.isHighlighted(BrowserViewMode.Browser).test {
            assertTrue(awaitItem())
            acknowledgement.onBrowserMenuViewed(BrowserViewMode.CustomTab)
            cancelAndIgnoreRemainingEvents()
        }

        // a fresh subscription, rather than awaiting another event on the one above, proves the custom tab
        // acknowledgement never touched the seen state at all
        testee.isHighlighted(BrowserViewMode.Browser).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when a subsequent download completes after the previous one was acknowledged then the icon dot is raised again`() = runTest {
        val testee = createTestee()
        val acknowledgement = RealBrowserMenuAcknowledgement(testee)
        hydrationScheduler.advanceUntilIdle()
        testee.completeDownload()
        acknowledgement.onBrowserMenuViewed(BrowserViewMode.Browser)

        testee.isHighlighted(BrowserViewMode.Browser).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        testee.completeDownload()

        testee.isHighlighted(BrowserViewMode.Browser).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when three downloads complete with a menu open between each then the icon dot returns every time`() = runTest {
        val testee = createTestee()
        val acknowledgement = RealBrowserMenuAcknowledgement(testee)
        hydrationScheduler.advanceUntilIdle()

        repeat(3) {
            testee.completeDownload()

            testee.isHighlighted(BrowserViewMode.Browser).test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            acknowledgement.onBrowserMenuViewed(BrowserViewMode.Browser)

            testee.isHighlighted(BrowserViewMode.Browser).test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `when a restart happens while the download is still unacknowledged then a later download raises the icon dot`() = runTest {
        val preferences = InMemorySharedPreferences()
        val firstInstance = createTestee(preferences)
        val acknowledgement = RealBrowserMenuAcknowledgement(firstInstance)
        hydrationScheduler.advanceUntilIdle()
        firstInstance.completeDownload()
        acknowledgement.onBrowserMenuViewed(BrowserViewMode.Browser)

        // simulates a cold start: a fresh instance over the same persisted preferences, with the download still there
        val secondInstance = createTestee(preferences)
        hydrationScheduler.advanceUntilIdle()

        secondInstance.isHighlighted(BrowserViewMode.Browser).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        secondInstance.completeDownload()

        secondInstance.isHighlighted(BrowserViewMode.Browser).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun DownloadMenuHighlightPlugin.completeDownload() {
        hasNewDownloadFlow.value = true
        onFileDownloaded()
    }

    private fun createTestee(preferences: SharedPreferences = InMemorySharedPreferences()): DownloadMenuHighlightPlugin {
        whenever(newDownloadState.hasNewDownloadFlow).thenReturn(hasNewDownloadFlow)
        return DownloadMenuHighlightPlugin(
            newDownloadState = newDownloadState,
            sharedPreferencesProvider = FakeSharedPreferencesProvider(preferences),
            appCoroutineScope = CoroutineScope(hydrationDispatcher),
            dispatcherProvider = object : DispatcherProvider {
                override fun io() = hydrationDispatcher
            },
        )
    }

    private class FakeSharedPreferencesProvider(private val preferences: SharedPreferences) : SharedPreferencesProvider {
        override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean) = preferences
        override fun getEncryptedSharedPreferences(name: String, multiprocess: Boolean) = preferences
        override suspend fun getMigratedEncryptedSharedPreferences(name: String) = preferences
        override suspend fun getMigratedEncryptedSharedPreferences(origin: SharedPreferences, name: String) = preferences
    }
}
