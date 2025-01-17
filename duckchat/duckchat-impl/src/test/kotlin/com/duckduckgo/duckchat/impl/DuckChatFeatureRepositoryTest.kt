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

package com.duckduckgo.duckchat.impl

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckChatFeatureRepositoryTest {
    private val mockDatabase: DuckChatDataStore = mock()

    private val testee = RealDuckChatFeatureRepository(mockDatabase)

    @Test
    fun whenSetShowInBrowserMenuThenSetInDatabase() = runTest {
        testee.setShowInBrowserMenu(true)

        verify(mockDatabase).setShowInBrowserMenu(true)
    }

    @Test
    fun whenObserveShowInBrowserMenuThenObserveDatabase() = runTest {
        whenever(mockDatabase.observeShowInBrowserMenu()).thenReturn(flowOf(true, false))

        val results = testee.observeShowInBrowserMenu().take(2).toList()
        assertTrue(results[0])
        assertFalse(results[1])
    }

    @Test
    fun whenShouldShowInBrowserMenuThenGetFromDatabase() {
        whenever(mockDatabase.getShowInBrowserMenu()).thenReturn(true)

        assertTrue(testee.shouldShowInBrowserMenu())
    }
}
