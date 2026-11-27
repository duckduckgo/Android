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

package com.duckduckgo.app.widget

import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WidgetNextStepCompletionCheckTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val widgetCapabilities: WidgetCapabilities = mock()
    private val testee = WidgetNextStepCompletionCheck(widgetCapabilities)

    @Test
    fun whenItemIdRequestedThenMatchesConfig() {
        assertEquals("setup_add_widget", testee.itemId)
    }

    @Test
    fun whenWidgetInstalledThenComplete() = runTest {
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(true)

        assertTrue(testee.isComplete())
    }

    @Test
    fun whenNoWidgetInstalledThenIncomplete() = runTest {
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(false)

        assertFalse(testee.isComplete())
    }
}
