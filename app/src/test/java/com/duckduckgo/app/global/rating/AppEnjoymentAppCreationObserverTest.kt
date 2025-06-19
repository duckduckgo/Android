/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.global.rating

import android.annotation.SuppressLint
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class AppEnjoymentAppCreationObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val feature = FakeFeatureToggleFactory.create(PreventFeedbackDialogQueuingFeature::class.java)

    private lateinit var testee: AppEnjoymentAppCreationObserver
    private val mockAppEnjoymentPromptEmitter: AppEnjoymentPromptEmitter = mock()
    private val mockPromptTypeDecider: PromptTypeDecider = mock()

    private val testScope = TestScope()
    private val promptTypeLiveData = MutableLiveData<AppEnjoymentPromptOptions>()

    @Before
    fun setup() = runTest {
        whenever(mockAppEnjoymentPromptEmitter.promptType).thenReturn(promptTypeLiveData)
        whenever(mockPromptTypeDecider.determineInitialPromptType()).thenReturn(AppEnjoymentPromptOptions.ShowEnjoymentPrompt(PromptCount.first()))

        testee = AppEnjoymentAppCreationObserver(
            appEnjoymentPromptEmitter = mockAppEnjoymentPromptEmitter,
            promptTypeDecider = mockPromptTypeDecider,
            appCoroutineScope = testScope,
            preventDialogQueuingFeature = feature,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenFeatureEnabledAndCurrentPromptTypeIsNullThenShouldDetermineInitialPromptType() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        promptTypeLiveData.value = null

        testee.onStart(mock())

        verify(mockPromptTypeDecider).determineInitialPromptType()
    }

    @Test
    fun whenFeatureEnabledAndCurrentPromptTypeIsShowNothingThenShouldDetermineInitialPromptType() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        promptTypeLiveData.value = AppEnjoymentPromptOptions.ShowNothing

        testee.onStart(mock())

        verify(mockPromptTypeDecider).determineInitialPromptType()
    }

    @Test
    fun whenFeatureEnabledAndCurrentPromptTypeIsShowEnjoymentPromptThenShouldNotDetermineInitialPromptType() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        promptTypeLiveData.value = AppEnjoymentPromptOptions.ShowEnjoymentPrompt(PromptCount.first())

        testee.onStart(mock())

        verify(mockPromptTypeDecider, never()).determineInitialPromptType()
    }

    @Test
    fun whenFeatureEnabledAndCurrentPromptTypeIsShowRatingPromptThenShouldNotDetermineInitialPromptType() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        promptTypeLiveData.value = AppEnjoymentPromptOptions.ShowRatingPrompt(PromptCount.first())

        testee.onStart(mock())

        verify(mockPromptTypeDecider, never()).determineInitialPromptType()
    }

    @Test
    fun whenFeatureEnabledAndCurrentPromptTypeIsShowFeedbackPromptThenShouldNotDetermineInitialPromptType() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        promptTypeLiveData.value = AppEnjoymentPromptOptions.ShowFeedbackPrompt(PromptCount.first())

        testee.onStart(mock())

        verify(mockPromptTypeDecider, never()).determineInitialPromptType()
    }

    @Test
    fun whenFeatureDisabledAndCurrentPromptTypeIsNullThenShouldDetermineInitialPromptType() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = false))
        promptTypeLiveData.value = null

        testee.onStart(mock())

        verify(mockPromptTypeDecider).determineInitialPromptType()
    }

    @Test
    fun whenFeatureDisabledAndCurrentPromptTypeIsShowNothingThenShouldDetermineInitialPromptType() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = false))
        promptTypeLiveData.value = AppEnjoymentPromptOptions.ShowNothing

        testee.onStart(mock())

        verify(mockPromptTypeDecider).determineInitialPromptType()
    }

    @Test
    fun whenFeatureDisabledAndCurrentPromptTypeIsShowEnjoymentPromptThenShouldDetermineInitialPromptType() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = false))
        promptTypeLiveData.value = AppEnjoymentPromptOptions.ShowEnjoymentPrompt(PromptCount.first())

        testee.onStart(mock())

        verify(mockPromptTypeDecider).determineInitialPromptType()
    }

    @Test
    fun whenFeatureDisabledAndCurrentPromptTypeIsShowRatingPromptThenShouldDetermineInitialPromptType() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = false))
        promptTypeLiveData.value = AppEnjoymentPromptOptions.ShowRatingPrompt(PromptCount.first())

        testee.onStart(mock())

        verify(mockPromptTypeDecider).determineInitialPromptType()
    }

    @Test
    fun whenFeatureDisabledAndCurrentPromptTypeIsShowFeedbackPromptThenShouldDetermineInitialPromptType() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = false))
        promptTypeLiveData.value = AppEnjoymentPromptOptions.ShowFeedbackPrompt(PromptCount.first())

        testee.onStart(mock())

        verify(mockPromptTypeDecider).determineInitialPromptType()
    }

    @Test
    fun whenOnStartCalledMultipleTimesThenShouldCheckFeatureToggleEachTime() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = false))
        promptTypeLiveData.value = AppEnjoymentPromptOptions.ShowEnjoymentPrompt(PromptCount.first())

        testee.onStart(mock())
        testee.onStart(mock())

        verify(mockPromptTypeDecider, times(2)).determineInitialPromptType()
    }
}
