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

package com.duckduckgo.app.browser

import android.webkit.WebBackForwardList
import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewNavigationUserPreferenceFeatureStateTest {

    private val stack: TestBackForwardList = TestBackForwardList()

    @Test
    fun whenWebHistoryIsEmptyThenNavigationHistoryIsEmpty() {
        val stack = webViewState(stack).navigationHistory
        assertEquals(0, stack.size)
    }

    @Test
    fun whenWebHistoryHasSinglePageThenNavigationHistoryRetrieved() {
        stack.addPageToHistory("example.com".toHistoryItem())
        val stack = webViewState(stack).navigationHistory
        assertEquals(1, stack.size)
        assertEquals("example.com", stack[0].url)
    }

    @Test
    fun whenWebHistoryHasMultiplePagesThenNavigationHistoryRetrievedInCorrectOrder() {
        stack.addPageToHistory("a".toHistoryItem())
        stack.addPageToHistory("b".toHistoryItem())
        stack.addPageToHistory("c".toHistoryItem())

        val stack = webViewState(stack).navigationHistory

        assertEquals(3, stack.size)
        assertEquals("c", stack[0].url)
        assertEquals("b", stack[1].url)
        assertEquals("a", stack[2].url)
    }

    private fun webViewState(stack: WebBackForwardList): WebViewNavigationState {
        return WebViewNavigationState(stack = stack, progress = null)
    }
}
