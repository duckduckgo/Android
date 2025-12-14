/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.urldisplay

import app.cash.turbine.test
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealUrlDisplayRepositoryTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val settingsDataStore = mock<SettingsDataStore>()
    private val browserConfigFeature = mock<AndroidBrowserConfigFeature>()
    private val shorterUrlToggle = mock<Toggle>()
    private val appBuildConfig = mock<AppBuildConfig>()
    private lateinit var testee: UrlDisplayRepository

    @Before
    fun setup() {
        whenever(browserConfigFeature.shorterUrlDefault()).thenReturn(shorterUrlToggle)

        testee = RealUrlDisplayRepository(
            settingsDataStore = settingsDataStore,
            browserConfigFeature = browserConfigFeature,
            appBuildConfig = appBuildConfig,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when user manually set preference to true then return true and ignore everything else`() = runTest {
        // Given: User manually set preference to true
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(true)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(false)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: Return user's manual choice, ignore feature flag
        assertTrue(result)
        verify(settingsDataStore, atLeastOnce()).urlPreferenceSetByUser
        verify(settingsDataStore, atLeastOnce()).isFullUrlEnabled
        verify(shorterUrlToggle, never()).isEnabled()
        verify(settingsDataStore, never()).hasUrlPreferenceSet()
    }

    @Test
    fun `when user manually set preference to false then return false and ignore everything else`() = runTest {
        // Given: User manually set preference to false
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(true)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(false)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: Return user's manual choice, ignore feature flag
        assertFalse(result)
        verify(settingsDataStore, atLeastOnce()).urlPreferenceSetByUser
        verify(settingsDataStore, atLeastOnce()).isFullUrlEnabled
        verify(shorterUrlToggle, never()).isEnabled()
        verify(settingsDataStore, never()).hasUrlPreferenceSet()
    }

    @Test
    fun `when old app user with preference and not migrated then migrate to manual`() = runTest {
        // Given: Old app user who manually set shorter URL (before migration flag existed)
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(false)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(true)
        whenever(settingsDataStore.urlPreferenceMigrated).thenReturn(false)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: Migrates to manual preference
        assertFalse(result)
        verify(settingsDataStore).urlPreferenceSetByUser = true
        verify(settingsDataStore).urlPreferenceMigrated = true
        verify(settingsDataStore, atLeastOnce()).isFullUrlEnabled
    }

    @Test
    fun `when old app user with full url preference and not migrated then migrate to manual`() = runTest {
        // Given: Old app user who manually set full URL (before migration flag existed)
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(false)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(true)
        whenever(settingsDataStore.urlPreferenceMigrated).thenReturn(false)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: Migrates to manual preference
        assertTrue(result)
        verify(settingsDataStore).urlPreferenceSetByUser = true
        verify(settingsDataStore).urlPreferenceMigrated = true
    }

    @Test
    fun `when old app user migrated then rollback does not affect them`() = runTest {
        // Given: Old app user who was migrated to manual (chose shorter URL)
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(true)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)
        // Feature flag disabled (rollback)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(false)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: Manual preference wins over rollback
        assertFalse(result)
        verify(shorterUrlToggle, never()).isEnabled()
    }

    @Test
    fun `when preference exists and already migrated then skip migration`() = runTest {
        // Given: User has preference and already migrated
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(false)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(true)
        whenever(settingsDataStore.urlPreferenceMigrated).thenReturn(true)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(true)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: Skip migration, use stored value
        assertFalse(result)
        verify(settingsDataStore, never()).urlPreferenceSetByUser = true
        verify(settingsDataStore, never()).urlPreferenceMigrated = true
    }

    @Test
    fun `when new user with auto-assigned preference then not migrated to manual`() = runTest {
        // Given: New user (fresh install), feature enabled, no preference
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(false)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(false)
        whenever(appBuildConfig.isNewInstall()).thenReturn(true)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: Auto-assigned, NOT manual
        assertFalse(result)
        verify(settingsDataStore).urlPreferenceMigrated = true
        verify(settingsDataStore, never()).urlPreferenceSetByUser = true
    }

    @Test
    fun `when feature toggle disabled and no manual preference then return true for rollback`() = runTest {
        // Given: Feature toggle disabled (rollback mode), no manual preference
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(false)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(false)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(false)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: Return true (rollback to full URL)
        assertTrue(result)
        verify(settingsDataStore, atLeastOnce()).urlPreferenceSetByUser
        verify(shorterUrlToggle, atLeastOnce()).isEnabled()
    }

    @Test
    fun `when auto assigned preference exists and feature enabled then return stored value`() = runTest {
        // Given: Auto-assigned preference (not manual), feature enabled
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(false)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(true)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: Return stored auto-assigned value
        assertFalse(result)
        verify(settingsDataStore, atLeastOnce()).hasUrlPreferenceSet()
        verify(settingsDataStore, atLeastOnce()).isFullUrlEnabled
        verify(settingsDataStore, never()).isFullUrlEnabled = false
        verify(settingsDataStore, never()).isFullUrlEnabled = true
    }

    @Test
    fun `when existing user and feature enabled then set and return true`() = runTest {
        // Given: Existing user (app update), feature enabled, no preference
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(false)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(false)
        whenever(appBuildConfig.isNewInstall()).thenReturn(false)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: Existing user gets full URL (preserve legacy)
        assertTrue(result)
        verify(settingsDataStore, atLeastOnce()).isFullUrlEnabled = true
        verify(settingsDataStore).urlPreferenceMigrated = true // Auto-assigned, mark migrated
        verify(settingsDataStore, never()).urlPreferenceSetByUser = true
    }

    @Test
    fun `when new user and feature enabled then set and return false`() = runTest {
        // Given: New user (fresh install), feature enabled, no preference
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(false)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(false)
        whenever(appBuildConfig.isNewInstall()).thenReturn(true)

        // When
        val result = testee.isFullUrlEnabled()

        // Then: New user gets shorter URL
        assertFalse(result)
        verify(settingsDataStore, atLeastOnce()).isFullUrlEnabled = false
        verify(settingsDataStore).urlPreferenceMigrated = true // Auto-assigned, mark migrated
        verify(settingsDataStore, never()).urlPreferenceSetByUser = true
    }

    @Test
    fun `when set full url enabled to true then set manual flag and persist`() = runTest {
        // When
        testee.setFullUrlEnabled(true)

        // Then: All flags set
        verify(settingsDataStore).urlPreferenceSetByUser = true
        verify(settingsDataStore).urlPreferenceMigrated = true
        verify(settingsDataStore).isFullUrlEnabled = true
    }

    @Test
    fun `when set full url enabled to false then set manual flag and persist`() = runTest {
        // When
        testee.setFullUrlEnabled(false)

        // Then: All flags set
        verify(settingsDataStore).urlPreferenceSetByUser = true
        verify(settingsDataStore).urlPreferenceMigrated = true
        verify(settingsDataStore).isFullUrlEnabled = false
    }

    @Test
    fun `when flow collected first time then emit value from suspend function`() = runTest {
        // Given: User has manual preference
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(true)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)

        // When: Collect flow
        testee.isFullUrlEnabled.test {
            // Then: First emission from suspend function
            assertEquals(true, awaitItem())

            cancel()
        }
    }

    @Test
    fun `when flow collected and setter called then emit new value`() = runTest {
        // Given: Initial state - new user
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(false)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(false)
        whenever(appBuildConfig.isNewInstall()).thenReturn(true)

        testee.isFullUrlEnabled.test {
            // First emission: new user gets false
            assertEquals(false, awaitItem())

            // When: User manually changes preference
            whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(true)
            whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)
            testee.setFullUrlEnabled(true)

            // Then: Flow emits new value
            assertEquals(true, awaitItem())

            cancel()
        }
    }

    @Test
    fun `when flow collected multiple times then each gets latest value`() = runTest {
        // Given: Initial state
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(true)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)

        // When: First collector
        testee.isFullUrlEnabled.test {
            assertEquals(false, awaitItem())

            // When: Change value
            whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)
            testee.setFullUrlEnabled(true)

            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // When: Second collector starts after change
        testee.isFullUrlEnabled.test {
            // Then: Gets cached latest value immediately
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when same value set multiple times then distinct until changed prevents reemission`() = runTest {
        // Given
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(true)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)

        testee.isFullUrlEnabled.test {
            // First emission
            assertEquals(true, awaitItem())

            // When: Set same value multiple times
            testee.setFullUrlEnabled(true)
            testee.setFullUrlEnabled(true)

            // Then: No new emissions (distinctUntilChanged filters them)
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when user manually changes from auto-assigned then manual flag locks preference`() = runTest {
        // Given: User starts with auto-assigned preference
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(false)
        whenever(settingsDataStore.urlPreferenceMigrated).thenReturn(true)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(true)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)

        // Initial value is auto-assigned
        val initialResult = testee.isFullUrlEnabled()
        assertFalse(initialResult)

        // When: User manually changes it
        whenever(settingsDataStore.urlPreferenceSetByUser).thenReturn(true)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)
        testee.setFullUrlEnabled(true)

        // Then: Manual flag is set
        verify(settingsDataStore).urlPreferenceSetByUser = true

        // And: Future calls honor manual preference even if feature flag changes
        whenever(shorterUrlToggle.isEnabled()).thenReturn(false)
        val manualResult = testee.isFullUrlEnabled()
        assertTrue(manualResult)
    }
}
