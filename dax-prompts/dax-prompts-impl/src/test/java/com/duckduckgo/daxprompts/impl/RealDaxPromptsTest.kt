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

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.daxprompts.api.DaxPrompts.ActionType
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealDaxPromptsTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: RealDaxPrompts

    private val mockRepository: DaxPromptsRepository = mock()
    private val mockReactivateUsersExperiment: ReactivateUsersExperiment = mock()
    private val mockUserBrowserProperties: UserBrowserProperties = mock()
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()
    private val mockDefaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()

    @Before
    fun setup() {
        testee = RealDaxPrompts(
            mockRepository,
            mockReactivateUsersExperiment,
            mockUserBrowserProperties,
            mockDefaultBrowserDetector,
            mockDefaultRoleBrowserDialog,
            coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenUserIsNotEligibleThenReturnNone() = runTest {
        mockUserIsNotEligible()

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenUserIsEligibleAndInControlGroupThenReturnShowControl() = runTest {
        mockUserIsEligible()
        whenever(mockReactivateUsersExperiment.isControl()).thenReturn(true)

        val result = testee.evaluate()

        assertEquals(ActionType.SHOW_CONTROL, result)
    }

    @Test
    fun whenUserIsEligibleAndInBrowserComparisonGroupAndShouldShowPromptThenReturnShowVariant2() = runTest {
        mockUserIsEligible()
        whenever(mockReactivateUsersExperiment.isControl()).thenReturn(false)
        whenever(mockReactivateUsersExperiment.isDuckPlayerPrompt()).thenReturn(false)
        whenever(mockReactivateUsersExperiment.isBrowserComparisonPrompt()).thenReturn(true)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        whenever(mockRepository.getDaxPromptsShowBrowserComparison()).thenReturn(true)

        val result = testee.evaluate()

        assertEquals(ActionType.SHOW_VARIANT_BROWSER_COMPARISON, result)
    }

    @Test
    fun whenUserIsEligibleAndInBrowserComparisonGroupAndShouldNotShowPromptThenReturnNone() = runTest {
        mockUserIsEligible()
        whenever(mockReactivateUsersExperiment.isControl()).thenReturn(false)
        whenever(mockReactivateUsersExperiment.isDuckPlayerPrompt()).thenReturn(false)
        whenever(mockReactivateUsersExperiment.isBrowserComparisonPrompt()).thenReturn(true)
        whenever(mockRepository.getDaxPromptsShowBrowserComparison()).thenReturn(false)

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenUserIsEligibleButNotInAnyExperimentGroupThenReturnNone() = runTest {
        mockUserIsEligible()
        whenever(mockReactivateUsersExperiment.isControl()).thenReturn(false)
        whenever(mockReactivateUsersExperiment.isDuckPlayerPrompt()).thenReturn(false)
        whenever(mockReactivateUsersExperiment.isBrowserComparisonPrompt()).thenReturn(false)

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenDaysSinceInstalledLessThanThresholdThenUserIsNotEligible() = runTest {
        whenever(mockUserBrowserProperties.daysSinceInstalled()).thenReturn(27)
        whenever(mockUserBrowserProperties.daysUsedSince(any())).thenReturn(0)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenUserHasUsedAppInLast7DaysThenUserIsNotEligible() = runTest {
        whenever(mockUserBrowserProperties.daysSinceInstalled()).thenReturn(30)
        whenever(mockUserBrowserProperties.daysUsedSince(any())).thenReturn(1)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenIsDefaultBrowserThenUserIsNotEligible() = runTest {
        whenever(mockUserBrowserProperties.daysSinceInstalled()).thenReturn(30)
        whenever(mockUserBrowserProperties.daysUsedSince(any())).thenReturn(0)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
    }

    @Test
    fun whenShouldShowDialogIsFalseThenUserIsNotEligible() = runTest {
        whenever(mockUserBrowserProperties.daysSinceInstalled()).thenReturn(30)
        whenever(mockUserBrowserProperties.daysUsedSince(any())).thenReturn(0)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)

        val result = testee.evaluate()

        assertEquals(ActionType.NONE, result)
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
}
