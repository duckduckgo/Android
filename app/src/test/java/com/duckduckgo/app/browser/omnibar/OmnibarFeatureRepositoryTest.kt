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

package com.duckduckgo.app.browser.omnibar

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.FakeSettingsDataStore
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.ui.omnibar.OmnibarType
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class OmnibarFeatureRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val fakeSettingsDataStore: SettingsDataStore = FakeSettingsDataStore()
    private val browserFeatures = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val mockLifecycleOwner: LifecycleOwner = mock()

    private lateinit var testee: OmnibarFeatureRepository

    private fun createTestee() {
        testee = OmnibarFeatureRepository(
            settingsDataStore = fakeSettingsDataStore,
            browserFeatures = browserFeatures,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            coroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun whenOnStartThenFeatureFlagsAreUpdated() = runTest {
        browserFeatures.useUnifiedOmnibarLayout().setRawStoredState(Toggle.State(enable = true))
        browserFeatures.splitOmnibar().setRawStoredState(Toggle.State(enable = true))
        createTestee()

        testee.onStart(mockLifecycleOwner)

        assertTrue(testee.isUnifiedOmnibarFlagEnabled)
        assertTrue(testee.isSplitOmnibarAvailable)
    }

    @Test
    fun givenSplitOmnibarEnabledWhenSplitOmnibarIsDisabledThenOmnibarTypeIsReset() = runTest {
        fakeSettingsDataStore.omnibarType = OmnibarType.SPLIT
        browserFeatures.useUnifiedOmnibarLayout().setRawStoredState(Toggle.State(enable = true))
        browserFeatures.splitOmnibar().setRawStoredState(Toggle.State(enable = false))
        createTestee()

        testee.updateFeatureFlags()

        assertFalse(testee.isSplitOmnibarAvailable)
        assertEquals(OmnibarType.SINGLE_TOP, fakeSettingsDataStore.omnibarType)
    }

    @Test
    fun givenSplitOmnibarWasPreviouslySelectedWhenSplitOmnibarIsEnabledThenOmnibarTypeIsRestored() = runTest {
        fakeSettingsDataStore.omnibarType = OmnibarType.SINGLE_TOP
        fakeSettingsDataStore.isSplitOmnibarSelected = true
        browserFeatures.useUnifiedOmnibarLayout().setRawStoredState(Toggle.State(enable = true))
        browserFeatures.splitOmnibar().setRawStoredState(Toggle.State(enable = true))
        createTestee()

        testee.updateFeatureFlags()

        assertTrue(testee.isSplitOmnibarAvailable)
        assertEquals(OmnibarType.SPLIT, fakeSettingsDataStore.omnibarType)
    }

    @Test
    fun whenIsSplitOmnibarAvailableAndOmnibarTypeIsSplitThenIsSplitOmnibarEnabledIsTrue() = runTest {
        browserFeatures.useUnifiedOmnibarLayout().setRawStoredState(Toggle.State(enable = true))
        browserFeatures.splitOmnibar().setRawStoredState(Toggle.State(enable = true))
        fakeSettingsDataStore.omnibarType = OmnibarType.SPLIT
        createTestee()
        testee.updateFeatureFlags()

        assertTrue(testee.isSplitOmnibarEnabled)
    }

    @Test
    fun whenIsSplitOmnibarNotAvailableThenIsSplitOmnibarEnabledIsFalse() = runTest {
        browserFeatures.useUnifiedOmnibarLayout().setRawStoredState(Toggle.State(enable = false))
        browserFeatures.splitOmnibar().setRawStoredState(Toggle.State(enable = true))
        fakeSettingsDataStore.omnibarType = OmnibarType.SPLIT
        createTestee()
        testee.updateFeatureFlags()

        assertFalse(testee.isSplitOmnibarEnabled)
    }

    @Test
    fun whenOmnibarTypeIsNotSplitThenIsSplitOmnibarEnabledIsFalse() = runTest {
        browserFeatures.useUnifiedOmnibarLayout().setRawStoredState(Toggle.State(enable = true))
        browserFeatures.splitOmnibar().setRawStoredState(Toggle.State(enable = true))
        fakeSettingsDataStore.omnibarType = OmnibarType.SINGLE_TOP
        createTestee()
        testee.updateFeatureFlags()

        assertFalse(testee.isSplitOmnibarEnabled)
    }
}
