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
package com.duckduckgo.duckchat.impl.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckAiWidgetPreferencesTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val sharedPreferencesProvider: SharedPreferencesProvider = mock<SharedPreferencesProvider>().also {
        whenever(it.getSharedPreferences(any(), any(), any())).thenReturn(InMemorySharedPreferences())
    }

    private val testee = SharedPreferencesDuckAiWidgetPreferences(sharedPreferencesProvider, coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenNothingStoredThenShowRecentChatsDefaultsToTrue() = runTest {
        assertTrue(testee.showRecentChats(1))
    }

    @Test
    fun whenStoredThenValueIsPerWidgetId() = runTest {
        testee.setShowRecentChats(2, false)

        assertFalse(testee.showRecentChats(2))
        assertTrue(testee.showRecentChats(3))
    }

    @Test
    fun whenRemovedThenOnlyThoseIdsFallBackToDefault() = runTest {
        testee.setShowRecentChats(4, false)
        testee.setShowRecentChats(5, false)
        testee.setShowRecentChats(6, false)

        testee.remove(intArrayOf(4, 5))

        assertTrue(testee.showRecentChats(4))
        assertTrue(testee.showRecentChats(5))
        assertFalse(testee.showRecentChats(6))
    }
}
