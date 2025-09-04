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

package com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.FeatureEnabled
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.FullyConverted
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.OmnibarInteracted
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.PromptSubmitted
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.SearchSubmitted
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.SettingsSeen
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_ENABLED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_INTERACTION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_PROMPT_SUBMISSION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SEARCH_SUBMISSION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SETTINGS_VIEWED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class InputScreenDiscoveryFunnelImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private lateinit var testDataStore: DataStore<Preferences>
    private val mockPixel: Pixel = mock()
    private val mockDuckAiFeatureState: DuckAiFeatureState = mock()

    private val showInputScreenFlow = MutableStateFlow(true)

    private lateinit var testee: InputScreenDiscoveryFunnelImpl

    @Before
    fun setUp() {
        testDataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("input_screen_discovery_funnel_test") },
        )
        whenever(mockDuckAiFeatureState.showInputScreen).thenReturn(showInputScreenFlow)

        testee = InputScreenDiscoveryFunnelImpl(
            coroutineScope = coroutineRule.testScope,
            dataStore = testDataStore,
            pixel = mockPixel,
            duckAiFeatureState = mockDuckAiFeatureState,
        )
    }

    @Test
    fun `when onDuckAiSettingsSeen called and input screen is disabled then settings seen step is processed`() = runTest {
        // Given input screen is disabled
        showInputScreenFlow.value = false

        // When
        testee.onDuckAiSettingsSeen()
        advanceUntilIdle()

        // Then
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SETTINGS_VIEWED)
        // Verify the preference was set
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[SettingsSeen.prefKey] == true)
    }

    @Test
    fun `when onDuckAiSettingsSeen called and input screen is enabled then settings seen step is not processed`() = runTest {
        // Given input screen is enabled
        showInputScreenFlow.value = true

        // When
        testee.onDuckAiSettingsSeen()
        advanceUntilIdle()

        // Then
        verifyNoInteractions(mockPixel)
    }

    @Test
    fun `when onInputScreenEnabled called and settings seen dependency is satisfied then feature enabled step is processed`() = runTest {
        // Given settings seen step is completed
        testDataStore.edit { prefs ->
            prefs[SettingsSeen.prefKey] = true
        }

        // When
        testee.onInputScreenEnabled()
        advanceUntilIdle()

        // Then
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_ENABLED)
        // Verify the preference was set
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[FeatureEnabled.prefKey] == true)
    }

    @Test
    fun `when onInputScreenEnabled called and settings seen dependency is not satisfied then feature enabled step is not processed`() = runTest {
        // Given settings seen step is not completed (DataStore is empty by default)

        // When
        testee.onInputScreenEnabled()
        advanceUntilIdle()

        // Then
        verify(mockPixel, never()).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_ENABLED)
        // Verify the preference was not set
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[FeatureEnabled.prefKey] != true)
    }

    @Test
    fun `when onInputScreenEnabled called and step already reached then feature enabled step is not processed again`() = runTest {
        // Given feature enabled step is already completed
        testDataStore.edit { prefs ->
            prefs[SettingsSeen.prefKey] = true
            prefs[FeatureEnabled.prefKey] = true
        }

        // When
        testee.onInputScreenEnabled()
        advanceUntilIdle()

        // Then
        verify(mockPixel, never()).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_ENABLED)
    }

    @Test
    fun `when onInputScreenOpened called and feature enabled dependency is satisfied then omnibar interacted step is processed`() = runTest {
        // Given feature enabled step is completed
        testDataStore.edit { prefs ->
            prefs[FeatureEnabled.prefKey] = true
        }

        // When
        testee.onInputScreenOpened()
        advanceUntilIdle()

        // Then
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_INTERACTION)
        // Verify the preference was set
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[OmnibarInteracted.prefKey] == true)
    }

    @Test
    fun `when onSearchSubmitted called and omnibar interacted dependency is satisfied then search submitted step is processed`() = runTest {
        // Given omnibar interacted step is completed
        testDataStore.edit { prefs ->
            prefs[OmnibarInteracted.prefKey] = true
        }

        // When
        testee.onSearchSubmitted()
        advanceUntilIdle()

        // Then
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SEARCH_SUBMISSION)
        // Verify the preference was set
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[SearchSubmitted.prefKey] == true)
    }

    @Test
    fun `when onPromptSubmitted called and omnibar interacted dependency is satisfied then prompt submitted step is processed`() = runTest {
        // Given omnibar interacted step is completed
        testDataStore.edit { prefs ->
            prefs[OmnibarInteracted.prefKey] = true
        }

        // When
        testee.onPromptSubmitted()
        advanceUntilIdle()

        // Then
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_PROMPT_SUBMISSION)
        // Verify the preference was set
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[PromptSubmitted.prefKey] == true)
    }

    @Test
    fun `when both search and prompt submitted then full conversion step is processed`() = runTest {
        // Given both search and prompt dependencies are satisfied
        testDataStore.edit { prefs ->
            prefs[OmnibarInteracted.prefKey] = true
            prefs[SearchSubmitted.prefKey] = true
        }

        // When prompt submitted (this should trigger full conversion)
        testee.onPromptSubmitted()
        advanceUntilIdle()

        // Then both prompt submission and full conversion pixels should be fired
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_PROMPT_SUBMISSION)
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER)

        // Verify both preferences were set
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[PromptSubmitted.prefKey] == true)
        assert(preferences[FullyConverted.prefKey] == true)
    }

    @Test
    fun `when only search submitted but not prompt then full conversion step is not processed`() = runTest {
        // Given only omnibar interacted step is completed
        testDataStore.edit { prefs ->
            prefs[OmnibarInteracted.prefKey] = true
        }

        // When search submitted
        testee.onSearchSubmitted()
        advanceUntilIdle()

        // Then search submission pixel should be fired but not full conversion
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SEARCH_SUBMISSION)
        verify(mockPixel, never()).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER)

        // Verify only search submitted was set, not full conversion
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[SearchSubmitted.prefKey] == true)
        assert(preferences[FullyConverted.prefKey] != true)
    }

    @Test
    fun `when only prompt submitted but not search then full conversion step is not processed`() = runTest {
        // Given only omnibar interacted step is completed
        testDataStore.edit { prefs ->
            prefs[OmnibarInteracted.prefKey] = true
        }

        // When prompt submitted
        testee.onPromptSubmitted()
        advanceUntilIdle()

        // Then prompt submission pixel should be fired but not full conversion
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_PROMPT_SUBMISSION)
        verify(mockPixel, never()).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER)

        // Verify only prompt submitted was set, not full conversion
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[PromptSubmitted.prefKey] == true)
        assert(preferences[FullyConverted.prefKey] != true)
    }

    @Test
    fun `when full conversion already reached then full conversion step is not processed again`() = runTest {
        // Given full conversion is already completed
        testDataStore.edit { prefs ->
            prefs[OmnibarInteracted.prefKey] = true
            prefs[SearchSubmitted.prefKey] = true
            prefs[PromptSubmitted.prefKey] = true
            prefs[FullyConverted.prefKey] = true
        }

        // When search submitted
        testee.onSearchSubmitted()
        advanceUntilIdle()

        // Then search submission pixel should not be fired again (already reached)
        // and full conversion pixel should not be fired again
        verify(mockPixel, never()).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SEARCH_SUBMISSION)
        verify(mockPixel, never()).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER)
    }

    @Test
    fun `when onInputScreenDisabled called then funnel is reset`() = runTest {
        // Given some preferences are set
        testDataStore.edit { prefs ->
            prefs[SettingsSeen.prefKey] = true
            prefs[FeatureEnabled.prefKey] = true
            prefs[OmnibarInteracted.prefKey] = true
            prefs[SearchSubmitted.prefKey] = true
            prefs[PromptSubmitted.prefKey] = true
            prefs[FullyConverted.prefKey] = true
        }

        // When
        testee.onInputScreenDisabled()
        advanceUntilIdle()

        // Then all preferences should be reset to false
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[SettingsSeen.prefKey] == false)
        assert(preferences[FeatureEnabled.prefKey] == false)
        assert(preferences[OmnibarInteracted.prefKey] == false)
        assert(preferences[SearchSubmitted.prefKey] == false)
        assert(preferences[PromptSubmitted.prefKey] == false)
        assert(preferences[FullyConverted.prefKey] == false)
    }

    @Test
    fun `when complete funnel flow is executed then all individual step pixels are fired in correct order`() = runTest {
        // Set input screen as disabled initially
        showInputScreenFlow.value = false

        // Step 1: Settings seen
        testee.onDuckAiSettingsSeen()
        advanceUntilIdle()

        // Step 2: Feature enabled
        testee.onInputScreenEnabled()
        advanceUntilIdle()

        // Step 3: Omnibar interacted
        testee.onInputScreenOpened()
        advanceUntilIdle()

        // Step 4: Search submitted
        testee.onSearchSubmitted()
        advanceUntilIdle()

        // Step 5: Prompt submitted
        testee.onPromptSubmitted()
        advanceUntilIdle()

        // Then all individual step pixels should be fired in correct order
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SETTINGS_VIEWED)
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_ENABLED)
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_INTERACTION)
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SEARCH_SUBMISSION)
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_PROMPT_SUBMISSION)
        verify(mockPixel).fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER)

        // Verify all preferences were set correctly
        val preferences = testDataStore.data.firstOrNull()!!
        assert(preferences[SettingsSeen.prefKey] == true)
        assert(preferences[FeatureEnabled.prefKey] == true)
        assert(preferences[OmnibarInteracted.prefKey] == true)
        assert(preferences[SearchSubmitted.prefKey] == true)
        assert(preferences[PromptSubmitted.prefKey] == true)
        assert(preferences[FullyConverted.prefKey] == true)
    }
}
