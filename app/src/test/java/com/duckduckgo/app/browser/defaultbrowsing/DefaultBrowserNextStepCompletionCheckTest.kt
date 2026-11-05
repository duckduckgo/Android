/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultbrowsing

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultBrowserNextStepCompletionCheckTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val defaultBrowserDetector: DefaultBrowserDetector = mock()
    private val testee = DefaultBrowserNextStepCompletionCheck(defaultBrowserDetector)

    @Test
    fun whenItemIdRequestedThenMatchesConfig() {
        assertEquals("setup_default_browser", testee.itemId)
    }

    @Test
    fun whenDefaultBrowserThenComplete() = runTest {
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(true)

        assertTrue(testee.isComplete())
    }

    @Test
    fun whenNotDefaultBrowserThenIncomplete() = runTest {
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        assertFalse(testee.isComplete())
    }
}
