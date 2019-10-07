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

import com.duckduckgo.app.browser.rating.db.AppEnjoymentRepository
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*

@Suppress("RemoveExplicitTypeArguments")
class SecondaryPromptDeciderTest {

    private lateinit var testee: SecondaryPromptDecider

    private val mockAppDaysUsedRepository: AppDaysUsedRepository = mock()
    private val mockAppEnjoymentRepository: AppEnjoymentRepository = mock()

    @Before
    fun setup() = runBlocking<Unit> {
        testee = SecondaryPromptDecider(mockAppDaysUsedRepository, mockAppEnjoymentRepository)
        whenever(mockAppEnjoymentRepository.dateUserDismissedFirstPrompt()).thenReturn(Date())
        whenever(mockAppEnjoymentRepository.canUserBeShownSecondPrompt()).thenReturn(true)
    }

    @Test
    fun whenUserHasUsedTheAppForAWhileSinceSeeingFirstPromptThenTheySeeSecondPrompt() = runBlocking<Unit> {
        configureLotsOfAppUsage()
        assertTrue(testee.shouldShowPrompt())
    }

    @Test
    fun whenUserHasNotUsedTheAppMuchSinceSeeingFirstPromptThenTheyDoNotSeeSecondPrompt() = runBlocking<Unit> {
        whenever(mockAppEnjoymentRepository.canUserBeShownSecondPrompt()).thenReturn(true)
        configureNotALotOfAppUsage()
        assertFalse(testee.shouldShowPrompt())
    }

    @Test
    fun whenUserHasAlreadyRatedOrGaveFeedbackThenTheyDoNoSeeASecondPromptEvenAfterALotOfUsage() = runBlocking<Unit> {
        whenever(mockAppEnjoymentRepository.canUserBeShownSecondPrompt()).thenReturn(false)
        configureLotsOfAppUsage()
        assertFalse(testee.shouldShowPrompt())
    }

    private suspend fun configureLotsOfAppUsage() {
        whenever(mockAppDaysUsedRepository.getNumberOfDaysAppUsedSinceDate(any())).thenReturn(Long.MAX_VALUE)
    }

    private suspend fun configureNotALotOfAppUsage() {
        whenever(mockAppDaysUsedRepository.getNumberOfDaysAppUsedSinceDate(any())).thenReturn(0)
    }
}