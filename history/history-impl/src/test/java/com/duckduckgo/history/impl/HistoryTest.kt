/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.history.impl

import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HistoryTest {

    private val mockHistoryRepository: HistoryRepository = mock()
    private val mockDuckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val testScope = TestScope()

    val testee = History(mockHistoryRepository, mockDuckDuckGoUrlDetector, testScope)

    @Test
    fun whenUrlIsSerpThenSaveToHistoryWithQueryAndSerpIsTrue() {
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockDuckDuckGoUrlDetector.extractQuery(any())).thenReturn("query")

        testScope.launch {
            testee.saveToHistory("url", "title")

            verify(mockHistoryRepository).saveToHistory(eq("url"), eq("title"), eq("query"), eq(true))
        }
    }

    @Test
    fun whenSerpUrlDoesNotHaveQueryThenSaveToHistoryWithQueryAndSerpIsTrue() {
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockDuckDuckGoUrlDetector.extractQuery(any())).thenReturn(null)

        testScope.launch {
            testee.saveToHistory("url", "title")

            verify(mockHistoryRepository).saveToHistory(eq("url"), eq("title"), eq(null), eq(false))
        }
    }

    @Test
    fun whenNotSerpUrlThenSaveToHistoryWithoutQueryAndSerpIsFalse() {
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(false)

        testScope.launch {
            testee.saveToHistory("url", "title")

            verify(mockHistoryRepository).saveToHistory(eq("url"), eq("title"), eq(null), eq(false))
        }
    }
}
