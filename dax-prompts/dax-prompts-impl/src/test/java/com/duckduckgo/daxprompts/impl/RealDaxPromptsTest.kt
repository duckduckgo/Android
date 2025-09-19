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

package com.duckduckgo.daxprompts.impl

import android.annotation.SuppressLint
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.daxprompts.api.DaxPrompts.ActionType
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealDaxPromptsTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: RealDaxPrompts

    private val mockRepository: DaxPromptsRepository = mock()
    private val fakeReactivateUsersToggles = FakeFeatureToggleFactory.create(ReactivateUsersToggles::class.java)
    private val mockUserBrowserProperties: UserBrowserProperties = mock()
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()
    private val mockDefaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()

    @Before
    fun setup() {
        testee = RealDaxPrompts(
            mockRepository,
            fakeReactivateUsersToggles,
            mockUserBrowserProperties,
            mockDefaultBrowserDetector,
            mockDefaultRoleBrowserDialog,
            coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenFlagIsDisabledAndDialogNotShownInTheLast24HoursThenReturnNone() = runTest {
        mockFlagIsDisabled()
        mockDialogNotShownInTheLast24Hours()

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenUserIsNotEligibleAndDialogNotShownInTheLast24HoursThenReturnNone() = runTest {
        mockFlagIsEnabled()
        mockUserIsNotEligible()
        mockDialogNotShownInTheLast24Hours()

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenShouldNotShowBrowserComparisonAndDialogNotShownInTheLast24HoursThenReturnNone() = runTest {
        mockFlagIsEnabled()
        mockUserIsEligible()
        mockDialogNotShownInTheLast24Hours()
        mockShouldNotShowBrowserComparisonPrompt()

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenFlagIsEnabledAndUserIsEligibleAndShouldShowBrowserComparison() = runTest {
        mockFlagIsEnabled()
        mockUserIsEligible()
        mockShouldShowBrowserComparisonPrompt()

        val result = testee.evaluate()

        assertEquals(ActionType.SHOW_BROWSER_COMPARISON_PROMPT, result)
    }

    @Test
    fun whenDaysSinceInstalledLessThanThresholdThenUserIsNotEligible() = runTest {
        mockDialogNotShownInTheLast24Hours()
        whenever(mockUserBrowserProperties.daysSinceInstalled()).thenReturn(27)
        whenever(mockUserBrowserProperties.daysUsedSince(any())).thenReturn(0)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenUserHasUsedAppInLast7DaysThenUserIsNotEligible() = runTest {
        mockDialogNotShownInTheLast24Hours()
        whenever(mockUserBrowserProperties.daysSinceInstalled()).thenReturn(30)
        whenever(mockUserBrowserProperties.daysUsedSince(any())).thenReturn(1)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenIsDefaultBrowserThenUserIsNotEligible() = runTest {
        mockDialogNotShownInTheLast24Hours()
        whenever(mockUserBrowserProperties.daysSinceInstalled()).thenReturn(30)
        whenever(mockUserBrowserProperties.daysUsedSince(any())).thenReturn(0)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenShouldShowDialogIsFalseThenUserIsNotEligible() = runTest {
        mockDialogNotShownInTheLast24Hours()
        whenever(mockUserBrowserProperties.daysSinceInstalled()).thenReturn(30)
        whenever(mockUserBrowserProperties.daysUsedSince(any())).thenReturn(0)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenDialogShownInTheLast24HoursThenTooSoonToShowOtherPrompts() = runTest {
        mockFlagIsEnabled()
        mockUserIsEligible()
        mockShouldNotShowBrowserComparisonPrompt()
        mockDialogShownInTheLast24Hours()

        val result = testee.evaluate()

        assertEquals(ActionType.TOO_SOON_TO_SHOW_OTHER_PROMPTS, result)
    }

    private fun mockUserIsEligible() = runBlocking {
        whenever(mockUserBrowserProperties.daysSinceInstalled()).thenReturn(30)
        whenever(mockUserBrowserProperties.daysUsedSince(any())).thenReturn(0)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
    }

    private fun mockUserIsNotEligible() {
        whenever(mockUserBrowserProperties.daysSinceInstalled()).thenReturn(27)
    }

    private fun mockFlagIsEnabled() {
        fakeReactivateUsersToggles.self().setRawStoredState(Toggle.State(true))
        fakeReactivateUsersToggles.browserComparisonPrompt().setRawStoredState(Toggle.State(true))
    }

    private fun mockFlagIsDisabled() {
        fakeReactivateUsersToggles.self().setRawStoredState(Toggle.State(true))
        fakeReactivateUsersToggles.browserComparisonPrompt().setRawStoredState(Toggle.State(false))
    }

    private fun mockShouldShowBrowserComparisonPrompt() {
        runBlocking {
            whenever(mockRepository.getDaxPromptsBrowserComparisonShown()).thenReturn(false)
        }
    }

    private fun mockShouldNotShowBrowserComparisonPrompt() {
        runBlocking {
            whenever(mockRepository.getDaxPromptsBrowserComparisonShown()).thenReturn(true)
        }
    }

    private fun mockDialogShownInTheLast24Hours() {
        runBlocking {
            whenever(mockRepository.getDaxPromptsBrowserComparisonShownInTheLast24Hours()).thenReturn(true)
        }
    }

    private fun mockDialogNotShownInTheLast24Hours() {
        runBlocking {
            whenever(mockRepository.getDaxPromptsBrowserComparisonShownInTheLast24Hours()).thenReturn(false)
        }
    }
}
