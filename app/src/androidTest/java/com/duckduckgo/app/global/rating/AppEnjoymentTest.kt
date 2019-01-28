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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.browser.rating.db.AppEnjoymentRepository
import com.duckduckgo.app.global.rating.AppEnjoyment.AppEnjoymentPromptOptions.ShowEnjoymentPrompt
import com.duckduckgo.app.global.rating.AppEnjoyment.AppEnjoymentPromptOptions.ShowNothing
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.app.usage.search.SearchCountDao
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@Suppress("RemoveExplicitTypeArguments")
class AppEnjoymentTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: AppEnjoyment

    private val mockPlayStoreUtils: PlayStoreUtils = mock()
    private val mockSearchCountDao: SearchCountDao = mock()
    private val mockAppDaysUsedRepo: AppDaysUsedRepository = mock()
    private val mockAppEnjoymentRepo: AppEnjoymentRepository = mock()

    @Before
    fun setup() = runBlocking {
        testee = AppEnjoyment(
            mockPlayStoreUtils,
            mockSearchCountDao,
            mockAppDaysUsedRepo,
            mockAppEnjoymentRepo,
            InstrumentationRegistry.getInstrumentation().targetContext
        )

        configureAllConditionsToAllowPrompt()
    }

    @Test
    fun whenPlayStoreInstalledAndEnoughAppUsageThenAppEnjoymentPromptShown() = runBlocking<Unit> {
        configureAllConditionsToAllowPrompt()
        simulateLifecycle()
        assertAppEnjoymentPromptShown()
    }

    @Test
    fun whenPlayStoreInstalledButNotEnoughSearchesMadeNoPromptShown() = runBlocking<Unit> {
        whenever(mockSearchCountDao.getSearchesMade()).thenReturn(0)
        simulateLifecycle()
        assertNoPromptShown()
    }

    @Test
    fun whenPlayStoreNotInstalledThenNoPromptShown() = runBlocking<Unit> {
        whenever(mockPlayStoreUtils.isPlayStoreInstalled(any())).thenReturn(false)
        simulateLifecycle()
        assertNoPromptShown()
    }

    @Test
    fun whenAppNotUsedEnoughDaysThenNoPromptShown() = runBlocking<Unit> {
        whenever(mockAppDaysUsedRepo.getNumberOfDaysAppUsed()).thenReturn(0)
        simulateLifecycle()
        assertNoPromptShown()
    }

    @Test
    fun whenUserHasRecentlyRespondedToPromptThenNoPromptShown() = runBlocking<Unit> {
        whenever(mockAppEnjoymentRepo.hasUserRecentlyRespondedToAppEnjoymentPrompt()).thenReturn(true)
        simulateLifecycle()
        assertNoPromptShown()
    }

    private fun assertAppEnjoymentPromptShown() {
        assertEquals(ShowEnjoymentPrompt, testee.promptType.blockingObserve())
    }

    private suspend fun configureAllConditionsToAllowPrompt() {
        whenever(mockPlayStoreUtils.isPlayStoreInstalled(any())).thenReturn(true)
        whenever(mockSearchCountDao.getSearchesMade()).thenReturn(Long.MAX_VALUE)
        whenever(mockAppDaysUsedRepo.getNumberOfDaysAppUsed()).thenReturn(Long.MAX_VALUE)
        whenever(mockAppEnjoymentRepo.hasUserRecentlyRespondedToAppEnjoymentPrompt()).thenReturn(false)
    }

    private fun assertNoPromptShown() {
        assertEquals(ShowNothing, testee.promptType.blockingObserve())
    }

    private suspend fun simulateLifecycle() {
        withContext(Dispatchers.Main) {
            testee.onAppCreation()
            testee.onAppStart()
        }
    }
}