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

package com.duckduckgo.eventhub.impl.webevents

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class WebEventsFeaturePluginTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private val dataStore: WebEventsDataStore = mock()

    private val plugin = WebEventsFeaturePlugin(
        webEventsDataStore = dataStore,
        appCoroutineScope = coroutineTestRule.testScope,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `store returns true and calls setWebEventsConfigJson for matching feature name`() {
        val jsonString = """{"key":"value"}"""
        val result = plugin.store("webEvents", jsonString)

        assertTrue(result)
        runTest {
            verify(dataStore).setWebEventsConfigJson(jsonString)
        }
    }

    @Test
    fun `store returns false for non-matching feature name`() {
        val result = plugin.store("otherFeature", """{}""")

        assertFalse(result)
    }

    @Test
    fun `store does not call setWebEventsConfigJson for non-matching feature name`() = runTest {
        plugin.store("otherFeature", """{}""")

        verify(dataStore, never()).setWebEventsConfigJson(any())
    }

    @Test
    fun `featureName is webEvents`() {
        org.junit.Assert.assertEquals("webEvents", plugin.featureName)
    }
}
