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
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
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

@Suppress("DenyListedApi")
@OptIn(ExperimentalCoroutinesApi::class)
class RealBrowserMenuDisplayRepositoryTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val settingsDataStore = mock<SettingsDataStore>()
    private val browserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private lateinit var testee: BrowserMenuDisplayRepository

    @Before
    fun setup() {
        testee = RealBrowserMenuDisplayRepository(
            browserMenuStore = settingsDataStore,
            browserConfigFeature = browserConfigFeature,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when feature flag enabled and setting enabled then hasOption true and isEnabled true`() = runTest {
        // Given
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
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
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
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
    fun `when feature flag disabled and setting enabled then hasOption false and isEnabled false`() = runTest {
        // Given: Experimental flag off — user pref is ignored because isActivate gates it
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)

        // When
        testee.browserMenuState.test {
            val state = awaitItem()

            // Then: hasOption=false (no toggle shown), isEnabled=false (pref ignored when flag is off)
            assertFalse(state.hasOption)
            assertFalse(state.isEnabled)

            cancel()
        }
    }

    @Test
    fun `when feature flag disabled and setting disabled then hasOption false and isEnabled false`() = runTest {
        // Given
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
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
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
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
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
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
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
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
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
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
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
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

    @Test
    fun `when rollout flag enabled and user pref false then hasOption false and isEnabled true`() = runTest {
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        browserConfigFeature.rolloutBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        testee.browserMenuState.test {
            val state = awaitItem()
            assertFalse(state.hasOption)
            assertTrue(state.isEnabled)
            cancel()
        }
    }

    @Test
    fun `when rollout flag enabled and experimental flag enabled then hasOption false and isEnabled true`() = runTest {
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
        browserConfigFeature.rolloutBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        testee.browserMenuState.test {
            val state = awaitItem()
            assertFalse(state.hasOption)
            assertTrue(state.isEnabled)
            cancel()
        }
    }

    @Test
    fun `when rollout flag disabled and experimental flag disabled and user pref true then isEnabled false`() = runTest {
        // Given: Both flags off — user pref is gated by experimentalBrowsingMenu being on,
        // so with both flags off the pref alone cannot enable the menu
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        browserConfigFeature.rolloutBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)

        testee.browserMenuState.test {
            val state = awaitItem()
            assertFalse(state.isEnabled)
            cancel()
        }
    }

    @Test
    fun `when rollout flag disabled and experimental flag enabled and user pref true then isEnabled true`() = runTest {
        // Given: Experimental flag on + user pref on → menu enabled even without rollout
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
        browserConfigFeature.rolloutBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)

        testee.browserMenuState.test {
            val state = awaitItem()
            assertTrue(state.isEnabled)
            cancel()
        }
    }

    @Test
    fun `when rollout flag disabled and user pref false then isEnabled false`() = runTest {
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        browserConfigFeature.rolloutBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        testee.browserMenuState.test {
            val state = awaitItem()
            assertFalse(state.isEnabled)
            cancel()
        }
    }

    @Test
    fun `isBottomSheetMenuEnabled returns true when rollout flag enabled`() {
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        browserConfigFeature.rolloutBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        assertTrue(testee.isBottomSheetMenuEnabled())
    }

    @Test
    fun `isBottomSheetMenuEnabled returns true when experimental flag enabled and user pref enabled`() {
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
        browserConfigFeature.rolloutBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)

        assertTrue(testee.isBottomSheetMenuEnabled())
    }

    @Test
    fun `isBottomSheetMenuEnabled returns false when experimental flag enabled but user pref disabled`() {
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = true))
        browserConfigFeature.rolloutBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        assertFalse(testee.isBottomSheetMenuEnabled())
    }

    @Test
    fun `isBottomSheetMenuEnabled returns false when both flags disabled even if user pref enabled`() {
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        browserConfigFeature.rolloutBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(true)

        assertFalse(testee.isBottomSheetMenuEnabled())
    }

    @Test
    fun `isBottomSheetMenuEnabled returns false when both flags disabled and user pref disabled`() {
        browserConfigFeature.experimentalBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        browserConfigFeature.rolloutBrowsingMenu().setRawStoredState(Toggle.State(enable = false))
        whenever(settingsDataStore.useBottomSheetMenu).thenReturn(false)

        assertFalse(testee.isBottomSheetMenuEnabled())
    }
}
