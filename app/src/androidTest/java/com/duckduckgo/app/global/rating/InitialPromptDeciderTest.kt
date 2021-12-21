/*
 * Copyright (c) 2019 DuckDuckGo
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

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.rating.db.AppEnjoymentRepository
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
@Suppress("RemoveExplicitTypeArguments", "PrivatePropertyName")
class InitialPromptDeciderTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: InitialPromptDecider

    private val mockAppDaysUsedRepository: AppDaysUsedRepository = mock()
    private val mockAppEnjoymentRepository: AppEnjoymentRepository = mock()

    private val NOT_ENOUGH_DAYS = (MINIMUM_DAYS_USAGE_BEFORE_FIRST_PROMPT - 1).toLong()
    private val EXACT_NUMBER_OF_DAYS = MINIMUM_DAYS_USAGE_BEFORE_FIRST_PROMPT.toLong()
    private val MORE_THAN_ENOUGH_DAYS = (MINIMUM_DAYS_USAGE_BEFORE_FIRST_PROMPT + 1).toLong()

    @Before
    fun setup() {
        testee = InitialPromptDecider(mockAppDaysUsedRepository, mockAppEnjoymentRepository)
    }

    @Test
    fun whenUserHasNotSeenPromptBeforeAndNotUsedTheAppEnoughThenShouldNotSeePrompt() = runTest {
        whenever(mockAppDaysUsedRepository.getNumberOfDaysAppUsed()).thenReturn(NOT_ENOUGH_DAYS)
        whenever(mockAppEnjoymentRepository.canUserBeShownFirstPrompt()).thenReturn(true)
        assertFalse(testee.shouldShowPrompt())
    }

    @Test
    fun whenUserHasNotSeenPromptBeforeAndUsedTheAppExactEnoughDaysThenShouldSeePrompt() = runTest {
        whenever(mockAppDaysUsedRepository.getNumberOfDaysAppUsed()).thenReturn(EXACT_NUMBER_OF_DAYS)
        whenever(mockAppEnjoymentRepository.canUserBeShownFirstPrompt()).thenReturn(true)
        assertTrue(testee.shouldShowPrompt())
    }

    @Test
    fun whenUserHasNotSeenPromptBeforeAndUsedTheAppMoreThanEnoughDaysThenShouldSeePrompt() = runTest {
        whenever(mockAppDaysUsedRepository.getNumberOfDaysAppUsed()).thenReturn(MORE_THAN_ENOUGH_DAYS)
        whenever(mockAppEnjoymentRepository.canUserBeShownFirstPrompt()).thenReturn(true)
        assertTrue(testee.shouldShowPrompt())
    }

    @Test
    fun whenUserHasSeenPromptBeforeAndNotUsedTheAppEnoughThenShouldNotSeePrompt() = runTest {
        whenever(mockAppDaysUsedRepository.getNumberOfDaysAppUsed()).thenReturn(NOT_ENOUGH_DAYS)
        whenever(mockAppEnjoymentRepository.canUserBeShownFirstPrompt()).thenReturn(false)
        assertFalse(testee.shouldShowPrompt())
    }

    @Test
    fun whenUserHasSeenPromptBeforeAndUsedTheAppExactEnoughDaysThenShouldNotSeePrompt() = runTest {
        whenever(mockAppDaysUsedRepository.getNumberOfDaysAppUsed()).thenReturn(EXACT_NUMBER_OF_DAYS)
        whenever(mockAppEnjoymentRepository.canUserBeShownFirstPrompt()).thenReturn(false)
        assertFalse(testee.shouldShowPrompt())
    }

    @Test
    fun whenUserHasSeenPromptBeforeAndUsedTheAppMoreThanEnoughDaysThenShouldNotSeePrompt() = runTest {
        whenever(mockAppDaysUsedRepository.getNumberOfDaysAppUsed()).thenReturn(MORE_THAN_ENOUGH_DAYS)
        whenever(mockAppEnjoymentRepository.canUserBeShownFirstPrompt()).thenReturn(false)
        assertFalse(testee.shouldShowPrompt())
    }

}
