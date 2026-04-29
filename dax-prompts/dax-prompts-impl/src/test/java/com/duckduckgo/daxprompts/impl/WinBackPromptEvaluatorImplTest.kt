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

package com.duckduckgo.daxprompts.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.daxprompts.api.DaxPromptBrowserComparisonNoParams
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class WinBackPromptEvaluatorImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockApplicationContext: Context = mock()
    private val mockUserBrowserProperties: UserBrowserProperties = mock()
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()
    private val mockDaxPromptsRepository: DaxPromptsRepository = mock()
    private val mockGlobalActivityStarter: GlobalActivityStarter = mock()
    private val fakeReactivateUsersToggles = FakeFeatureToggleFactory.create(ReactivateUsersToggles::class.java)
    private val mockIntent: Intent = mock()

    private val testee = WinBackPromptEvaluatorImpl(
        appCoroutineScope = coroutinesTestRule.testScope,
        applicationContext = mockApplicationContext,
        userBrowserProperties = mockUserBrowserProperties,
        defaultBrowserDetector = mockDefaultBrowserDetector,
        daxPromptsRepository = mockDaxPromptsRepository,
        globalActivityStarter = mockGlobalActivityStarter,
        dispatchers = coroutinesTestRule.testDispatcherProvider,
        reactivateUsersToggles = fakeReactivateUsersToggles,
    )

    @Test
    fun whenSelfToggleIsDisabledThenEvaluationIsSkipped() = runTest {
        fakeReactivateUsersToggles.self().setRawStoredState(State(false))
        fakeReactivateUsersToggles.defaultBrowserWinBackPrompt().setRawStoredState(State(true))

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockGlobalActivityStarter, never()).startIntent(any(), any<GlobalActivityStarter.ActivityParams>())
    }

    @Test
    fun whenWinBackPromptToggleIsDisabledThenEvaluationIsSkipped() = runTest {
        fakeReactivateUsersToggles.self().setRawStoredState(State(true))
        fakeReactivateUsersToggles.defaultBrowserWinBackPrompt().setRawStoredState(State(false))

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockGlobalActivityStarter, never()).startIntent(any(), any<GlobalActivityStarter.ActivityParams>())
    }

    @Test
    fun whenUserWasNeverDefaultBrowserThenEvaluationIsSkipped() = runTest {
        givenTogglesEnabled()
        whenever(mockUserBrowserProperties.wasEverDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockGlobalActivityStarter, never()).startIntent(any(), any<GlobalActivityStarter.ActivityParams>())
    }

    @Test
    fun whenUserIsCurrentlyDefaultBrowserThenEvaluationIsSkipped() = runTest {
        givenTogglesEnabled()
        whenever(mockUserBrowserProperties.wasEverDefaultBrowser()).thenReturn(true)
        whenever(mockDaxPromptsRepository.getDaxPromptsBrowserComparisonShown()).thenReturn(false)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockGlobalActivityStarter, never()).startIntent(any(), any<GlobalActivityStarter.ActivityParams>())
    }

    @Test
    fun whenBrowserComparisonAlreadyShownThenEvaluationIsSkipped() = runTest {
        givenTogglesEnabled()
        givenUserIsEligible()
        whenever(mockDaxPromptsRepository.getDaxPromptsBrowserComparisonShown()).thenReturn(true)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockGlobalActivityStarter, never()).startIntent(any(), any<GlobalActivityStarter.ActivityParams>())
    }

    @Test
    fun whenAllConditionsMetButIntentIsNullThenEvaluationIsSkipped() = runTest {
        givenTogglesEnabled()
        givenUserIsEligible()
        whenever(mockDaxPromptsRepository.getDaxPromptsBrowserComparisonShown()).thenReturn(false)
        whenever(mockGlobalActivityStarter.startIntent(mockApplicationContext, DaxPromptBrowserComparisonNoParams)).thenReturn(null)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockApplicationContext, never()).startActivity(any())
    }

    @Test
    fun whenAllConditionsMetAndIntentAvailableThenModalShownAndActivityLaunched() = runTest {
        givenTogglesEnabled()
        givenUserIsEligible()
        whenever(mockDaxPromptsRepository.getDaxPromptsBrowserComparisonShown()).thenReturn(false)
        whenever(mockGlobalActivityStarter.startIntent(mockApplicationContext, DaxPromptBrowserComparisonNoParams)).thenReturn(mockIntent)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, result)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(mockApplicationContext).startActivity(mockIntent)
        verify(mockIntent).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    @Test
    fun evaluatorHasCorrectPriority() {
        assertEquals(1, testee.priority)
    }

    @Test
    fun evaluatorHasCorrectId() {
        assertEquals("win_back_prompt", testee.evaluatorId)
    }

    private fun givenTogglesEnabled() {
        fakeReactivateUsersToggles.self().setRawStoredState(State(true))
        fakeReactivateUsersToggles.defaultBrowserWinBackPrompt().setRawStoredState(State(true))
    }

    private suspend fun givenUserIsEligible() {
        whenever(mockUserBrowserProperties.wasEverDefaultBrowser()).thenReturn(true)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
    }
}
