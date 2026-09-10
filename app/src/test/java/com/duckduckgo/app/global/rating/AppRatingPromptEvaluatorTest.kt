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

package com.duckduckgo.app.global.rating

import android.annotation.SuppressLint
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.promptscoordinator.api.ModalEvaluator
import com.duckduckgo.promptscoordinator.api.ModalTrigger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class AppRatingPromptEvaluatorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val promptTypeDecider: PromptTypeDecider = mock()
    private val appEnjoymentPromptEmitter: AppEnjoymentPromptEmitter = mock()
    private val appRatingPromptModalFeature = FakeFeatureToggleFactory.create(AppRatingPromptModalFeature::class.java)

    private val promptTypeLiveData = MutableLiveData<AppEnjoymentPromptOptions>()

    private lateinit var testee: AppRatingPromptEvaluator

    @Before
    fun before() {
        whenever(appEnjoymentPromptEmitter.promptType).thenReturn(promptTypeLiveData)

        testee = AppRatingPromptEvaluator(
            promptTypeDecider = promptTypeDecider,
            appEnjoymentPromptEmitter = appEnjoymentPromptEmitter,
            appRatingPromptModalFeature = appRatingPromptModalFeature,
            dispatchers = coroutineRule.testDispatcherProvider,
        )

        appRatingPromptModalFeature.self().setRawStoredState(Toggle.State(enable = true))
    }

    @Test
    fun triggerIsNtpRender() {
        assertEquals(ModalTrigger.NTP_RENDER, testee.trigger)
    }

    @Test
    fun priorityIsSix() {
        assertEquals(6, testee.priority)
    }

    @Test
    fun evaluatorIdIsAppRatingPrompt() {
        assertEquals("app_rating_prompt", testee.evaluatorId)
    }

    @Test
    fun featureDefaultsToEnabled() {
        val unsetFeature = FakeFeatureToggleFactory.create(AppRatingPromptModalFeature::class.java)

        assertTrue(unsetFeature.self().isEnabled())
    }

    @Test
    fun whenFeatureDisabledThenSkippedWithoutDeciding() = runTest {
        appRatingPromptModalFeature.self().setRawStoredState(Toggle.State(enable = false))

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
        verify(promptTypeDecider, never()).determineInitialPromptType()
        assertNull(promptTypeLiveData.value)
    }

    @Test
    fun whenDeciderSelectsShowNothingThenSkippedAndNothingEmitted() = runTest {
        whenever(promptTypeDecider.determineInitialPromptType()).thenReturn(AppEnjoymentPromptOptions.ShowNothing)

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
        assertNull(promptTypeLiveData.value)
    }

    @Test
    fun whenDeciderSelectsEnjoymentPromptThenModalShownAndPromptEmitted() = runTest {
        val expected = AppEnjoymentPromptOptions.ShowEnjoymentPrompt(PromptCount.first())
        whenever(promptTypeDecider.determineInitialPromptType()).thenReturn(expected)

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, testee.evaluate())
        assertEquals(expected, promptTypeLiveData.value)
    }

    @Test
    fun whenDeciderSelectsRatingPromptThenModalShownAndPromptEmitted() = runTest {
        val expected = AppEnjoymentPromptOptions.ShowRatingPrompt(PromptCount.second())
        whenever(promptTypeDecider.determineInitialPromptType()).thenReturn(expected)

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, testee.evaluate())
        assertEquals(expected, promptTypeLiveData.value)
    }
}
