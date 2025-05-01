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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealDaxPromptsTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: RealDaxPrompts

    private val mockRepository: DaxPromptsRepository = mock()

    @Before
    fun setup() {
        testee = RealDaxPrompts(mockRepository, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun whenShouldShowDuckPlayerPromptCalledThenReturnValueFromRepository() = runTest {
        whenever(mockRepository.getDaxPromptsShowDuckPlayer()).thenReturn(true)

        val result = testee.shouldShowDuckPlayerPrompt()

        assertEquals(true, result)
        verify(mockRepository).getDaxPromptsShowDuckPlayer()
    }

    @Test
    fun whenShouldShowDuckPlayerPromptCalledWithFalseValueThenReturnFalse() = runTest {
        whenever(mockRepository.getDaxPromptsShowDuckPlayer()).thenReturn(false)

        val result = testee.shouldShowDuckPlayerPrompt()

        assertEquals(false, result)
        verify(mockRepository).getDaxPromptsShowDuckPlayer()
    }
}
