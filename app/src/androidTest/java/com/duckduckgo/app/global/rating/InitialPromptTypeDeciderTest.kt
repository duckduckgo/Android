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

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.usage.search.SearchCountDao
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@Suppress("RemoveExplicitTypeArguments")
class InitialPromptTypeDeciderTest {

    private lateinit var testee: InitialPromptTypeDecider

    private val mockPlayStoreUtils: PlayStoreUtils = mock()
    private val mockSearchCountDao: SearchCountDao = mock()
    private val mockInitialPromptDecider: ShowPromptDecider = mock()
    private val mockSecondaryPromptDecider: ShowPromptDecider = mock()

    @Before
    fun setup() = runBlocking<Unit> {

        testee = InitialPromptTypeDecider(
            mockPlayStoreUtils,
            mockSearchCountDao,
            mockInitialPromptDecider,
            mockSecondaryPromptDecider,
            InstrumentationRegistry.getInstrumentation().targetContext
        )

        whenever(mockPlayStoreUtils.isPlayStoreInstalled()).thenReturn(true)
        whenever(mockPlayStoreUtils.installedFromPlayStore()).thenReturn(true)
        whenever(mockSearchCountDao.getSearchesMade()).thenReturn(Long.MAX_VALUE)
    }

    @Test
    fun whenPlayNotInstalledThenNoPromptShown() = runBlocking<Unit> {
        whenever(mockPlayStoreUtils.isPlayStoreInstalled()).thenReturn(false)
        assertPromptNotShown(testee.determineInitialPromptType())
    }

    @Test
    fun whenNotEnoughSearchesMadeThenNoPromptShown() = runBlocking<Unit> {
        whenever(mockSearchCountDao.getSearchesMade()).thenReturn(0)
        assertPromptNotShown(testee.determineInitialPromptType())
    }

    @Test
    fun whenEnoughSearchesMadeAndFirstPromptNotShownBeforeThenShouldShowFirstPrompt() = runBlocking<Unit> {
        whenever(mockInitialPromptDecider.shouldShowPrompt()).thenReturn(true)
        whenever(mockSearchCountDao.getSearchesMade()).thenReturn(Long.MAX_VALUE)
        val type = testee.determineInitialPromptType() as AppEnjoymentPromptOptions.ShowEnjoymentPrompt
        assertFirstPrompt(type.promptCount)
    }

    @Test
    fun whenEnoughSearchesMadeAndFirstPromptShownBeforeThenShouldShowSecondPrompt() = runBlocking<Unit> {
        whenever(mockInitialPromptDecider.shouldShowPrompt()).thenReturn(false)
        whenever(mockSecondaryPromptDecider.shouldShowPrompt()).thenReturn(true)
        whenever(mockSearchCountDao.getSearchesMade()).thenReturn(Long.MAX_VALUE)
        val type = testee.determineInitialPromptType() as AppEnjoymentPromptOptions.ShowEnjoymentPrompt
        assertSecondPrompt(type.promptCount)
    }

    private fun assertPromptNotShown(prompt: AppEnjoymentPromptOptions) {
        assertTrue(prompt == AppEnjoymentPromptOptions.ShowNothing)
    }

    private fun assertFirstPrompt(promptCount: PromptCount) {
        assertEquals(PromptCount.first().value, promptCount.value)
    }

    private fun assertSecondPrompt(promptCount: PromptCount) {
        assertEquals(PromptCount.second().value, promptCount.value)
    }
}
