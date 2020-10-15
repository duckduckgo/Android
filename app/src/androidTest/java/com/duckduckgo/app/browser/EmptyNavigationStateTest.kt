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

package com.duckduckgo.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EmptyNavigationStateTest {

    @Test
    fun whenEmptyNavigationStateFromNavigationStateThenBrowserPropertiesAreTheSame() {
        val previousState = buildState("originalUrl", "currentUrl", "titlle")
        val emptyNavigationState = EmptyNavigationState(previousState)

        assertEquals(emptyNavigationState.currentUrl, previousState.currentUrl)
        assertEquals(emptyNavigationState.originalUrl, previousState.originalUrl)
        assertEquals(emptyNavigationState.title, previousState.title)
    }

    @Test
    fun whenEmptyNavigationStateFromNavigationStateThenNavigationPropertiesAreCleared() {
        val emptyNavigationState = EmptyNavigationState(buildState("originalUrl", "currentUrl", "titlle"))

        assertEquals(emptyNavigationState.stepsToPreviousPage, 0)
        assertFalse(emptyNavigationState.canGoBack)
        assertFalse(emptyNavigationState.canGoForward)
        assertFalse(emptyNavigationState.hasNavigationHistory)
    }

    private fun buildState(originalUrl: String?, currentUrl: String?, title: String? = null): WebNavigationState {
        return TestNavigationState(
            originalUrl = originalUrl,
            currentUrl = currentUrl,
            title = title,
            stepsToPreviousPage = 1,
            canGoBack = true,
            canGoForward = true,
            hasNavigationHistory = true,
            progress = null
        )
    }
}
