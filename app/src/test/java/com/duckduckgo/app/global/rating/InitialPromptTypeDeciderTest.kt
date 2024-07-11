/*
 * Copyright (c) 2022 DuckDuckGo
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

import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("RemoveExplicitTypeArguments")
class InitialPromptTypeDeciderTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: InitialPromptTypeDecider

    private val mockPlayStoreUtils: PlayStoreUtils = mock()
    private val mockSearchCountDao: SearchCountDao = mock()
    private val mockInitialPromptDecider: ShowPromptDecider = mock()
    private val mockSecondaryPromptDecider: ShowPromptDecider = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()

    @Before
    fun setup() = runTest {
        testee = InitialPromptTypeDecider(
            playStoreUtils = mockPlayStoreUtils,
            searchCountDao = mockSearchCountDao,
            initialPromptDecider = mockInitialPromptDecider,
            secondaryPromptDecider = mockSecondaryPromptDecider,
            appBuildConfig = mockAppBuildConfig,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )

        whenever(mockPlayStoreUtils.isPlayStoreInstalled()).thenReturn(true)
        whenever(mockPlayStoreUtils.installedFromPlayStore()).thenReturn(true)
        whenever(mockSearchCountDao.getSearchesMade()).thenReturn(Long.MAX_VALUE)
        whenever(mockAppBuildConfig.isDebug).thenReturn(true)
    }

    @Test
    fun whenPlayNotInstalledThenNoPromptShown() = runTest {
        whenever(mockPlayStoreUtils.isPlayStoreInstalled()).thenReturn(false)
        assertPromptNotShown(testee.determineInitialPromptType())
    }

    @Test
    fun whenNotEnoughSearchesMadeThenNoPromptShown() = runTest {
        whenever(mockSearchCountDao.getSearchesMade()).thenReturn(0)
        assertPromptNotShown(testee.determineInitialPromptType())
    }

    @Test
    fun whenEnoughSearchesMadeAndFirstPromptNotShownBeforeThenShouldShowFirstPrompt() = runTest {
        whenever(mockInitialPromptDecider.shouldShowPrompt()).thenReturn(true)
        whenever(mockSearchCountDao.getSearchesMade()).thenReturn(Long.MAX_VALUE)
        val type = testee.determineInitialPromptType() as AppEnjoymentPromptOptions.ShowEnjoymentPrompt
        assertFirstPrompt(type.promptCount)
    }

    private fun assertPromptNotShown(prompt: AppEnjoymentPromptOptions) {
        assertTrue(prompt == AppEnjoymentPromptOptions.ShowNothing)
    }

    private fun assertFirstPrompt(promptCount: PromptCount) {
        assertEquals(PromptCount.first().value, promptCount.value)
    }
}
