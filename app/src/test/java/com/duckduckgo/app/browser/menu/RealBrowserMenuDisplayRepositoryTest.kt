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

import app.cash.turbine.test
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealBrowserMenuDisplayRepositoryTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val settingsDataStore = mock<SettingsDataStore>()
    private val browserConfigFeature = mock<AndroidBrowserConfigFeature>()
    private val experimentalMenuToggle = mock<Toggle>()
    private lateinit var testee: BrowserMenuDisplayRepository

    @Before
    fun setup() {
        whenever(browserConfigFeature.experimentalBrowsingMenu()).thenReturn(experimentalMenuToggle)

        testee = RealBrowserMenuDisplayRepository(
            browserMenuStore = settingsDataStore,
            browserConfigFeature = browserConfigFeature,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when feature flag enabled and setting enabled then hasOption true and isEnabled true`() = runTest {
        // Given
        whenever(experimentalMenuToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)

        // When
        testee.browserMenuState.test {
            val state = awaitItem()

            // Then
            assertTrue(state.hasOption)
            assertTrue(state.isEnabled)

            cancel()
        }
    }

    @Test
    fun `when feature flag enabled and setting disabled then hasOption true and isEnabled false`() = runTest {
        // Given
        whenever(experimentalMenuToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        // When
        testee.browserMenuState.test {
            val state = awaitItem()

            // Then
            assertTrue(state.hasOption)
            assertFalse(state.isEnabled)

            cancel()
        }
    }

    @Test
    fun `when feature flag disabled and setting enabled then hasOption false and isEnabled true`() = runTest {
        // Given: Feature flag disabled but user has preference stored
        whenever(experimentalMenuToggle.isEnabled()).thenReturn(false)
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)

        // When
        testee.browserMenuState.test {
            val state = awaitItem()

            // Then: hasOption=false means toggle won't show in settings, but preference persists
            assertFalse(state.hasOption)
            assertTrue(state.isEnabled)

            cancel()
        }
    }

    @Test
    fun `when feature flag disabled and setting disabled then hasOption false and isEnabled false`() = runTest {
        // Given
        whenever(experimentalMenuToggle.isEnabled()).thenReturn(false)
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        // When
        testee.browserMenuState.test {
            val state = awaitItem()

            // Then
            assertFalse(state.hasOption)
            assertFalse(state.isEnabled)

            cancel()
        }
    }

    @Test
    fun `when setExperimentalMenuEnabled called with true then persist setting and emit new state`() = runTest {
        // Given: Initial state - feature enabled, setting disabled
        whenever(experimentalMenuToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        testee.browserMenuState.test {
            // Initial emission
            val initialState = awaitItem()
            assertTrue(initialState.hasOption)
            assertFalse(initialState.isEnabled)

            // When: User enables experimental menu
            whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)
            testee.setExperimentalMenuEnabled(true)

            // Then: Setting persisted and new state emitted
            verify(settingsDataStore).useBottomSheetMenu = true
            val newState = awaitItem()
            assertTrue(newState.hasOption)
            assertTrue(newState.isEnabled)

            cancel()
        }
    }

    @Test
    fun `when setExperimentalMenuEnabled called with false then persist setting and emit new state`() = runTest {
        // Given: Initial state - feature enabled, setting enabled
        whenever(experimentalMenuToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)

        testee.browserMenuState.test {
            // Initial emission
            val initialState = awaitItem()
            assertTrue(initialState.hasOption)
            assertTrue(initialState.isEnabled)

            // When: User disables experimental menu
            whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)
            testee.setExperimentalMenuEnabled(false)

            // Then: Setting persisted and new state emitted
            verify(settingsDataStore).useBottomSheetMenu = false
            val newState = awaitItem()
            assertTrue(newState.hasOption)
            assertFalse(newState.isEnabled)

            cancel()
        }
    }

    @Test
    fun `when same value set multiple times then distinctUntilChanged prevents reemission`() = runTest {
        // Given
        whenever(experimentalMenuToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)

        testee.browserMenuState.test {
            // First emission
            awaitItem()

            // When: Set same value multiple times
            testee.setExperimentalMenuEnabled(true)
            testee.setExperimentalMenuEnabled(true)
            testee.setExperimentalMenuEnabled(true)

            // Then: No new emissions (distinctUntilChanged filters them)
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when value changed multiple times then flow emits all distinct changes`() = runTest {
        // Given
        whenever(experimentalMenuToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        testee.browserMenuState.test {
            // Initial state
            val initial = awaitItem()
            assertFalse(initial.isEnabled)

            // When: Toggle on
            whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)
            testee.setExperimentalMenuEnabled(true)
            val enabled = awaitItem()
            assertTrue(enabled.isEnabled)

            // When: Toggle off
            whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)
            testee.setExperimentalMenuEnabled(false)
            val disabled = awaitItem()
            assertFalse(disabled.isEnabled)

            // When: Toggle on again
            whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)
            testee.setExperimentalMenuEnabled(true)
            val enabledAgain = awaitItem()
            assertTrue(enabledAgain.isEnabled)

            cancel()
        }
    }

    @Test
    fun `when flow collected multiple times then each gets cached latest value`() = runTest {
        // Given: Initial state
        whenever(experimentalMenuToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        // When: First collector
        testee.browserMenuState.test {
            val initialState = awaitItem()
            assertFalse(initialState.isEnabled)

            // When: Change value
            whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)
            testee.setExperimentalMenuEnabled(true)

            val updatedState = awaitItem()
            assertTrue(updatedState.isEnabled)

            cancelAndIgnoreRemainingEvents()
        }

        // When: Second collector starts after change
        testee.browserMenuState.test {
            // Then: Gets cached latest value immediately (replay=1)
            val cachedState = awaitItem()
            assertTrue(cachedState.isEnabled)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
