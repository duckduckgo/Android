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
package com.duckduckgo.nextsteps.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealNextStepsItemsStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val preferences = InMemorySharedPreferences()
    private val preferencesProvider: SharedPreferencesProvider = mock {
        whenever(it.getSharedPreferences(any(), any(), any())).thenReturn(preferences)
    }
    private val testee = aStore()

    private fun aStore() = RealNextStepsItemsStore(preferencesProvider, coroutineRule.testDispatcherProvider)

    @Test
    fun `when nothing is stored then order and dismissed ids are empty`() = runTest {
        assertEquals(emptyList<String>(), testee.itemOrder())
        assertEquals(emptySet<String>(), testee.dismissedItemIds().first())
        assertEquals(emptySet<String>(), testee.completedItemIds())
        assertEquals(0, testee.frontImpressions("a"))
    }

    @Test
    fun `when ids are completed then a new instance reads them back`() {
        testee.addCompletedItemIds(setOf("a"))
        testee.addCompletedItemIds(setOf("b", "c"))

        assertEquals(setOf("a", "b", "c"), aStore().completedItemIds())
    }

    @Test
    fun `when order is saved then a new instance reads it back`() {
        testee.saveItemOrder(listOf("b", "a", "c"))

        assertEquals(listOf("b", "a", "c"), aStore().itemOrder())
    }

    @Test
    fun `when ids are dismissed then a new instance reads them back`() = runTest {
        testee.addDismissedItemId("a")
        testee.addDismissedItemId("b")

        assertEquals(setOf("a", "b"), aStore().dismissedItemIds().first())
    }

    @Test
    fun `when an id is dismissed then the dismissed ids flow emits the new set`() = runTest {
        val emissions = mutableListOf<Set<String>>()
        val job = testee.dismissedItemIds().onEach { emissions.add(it) }.launchIn(this)
        advanceUntilIdle()

        testee.addDismissedItemId("a")
        advanceUntilIdle()

        assertEquals(listOf(emptySet(), setOf("a")), emissions)
        job.cancel()
    }

    @Test
    fun `when the front card is shown repeatedly then its impressions accumulate`() {
        repeat(3) { testee.incrementFrontImpressions("a") }

        assertEquals(3, testee.frontImpressions("a"))
    }

    @Test
    fun `when a different card becomes the front then its count starts from zero`() {
        repeat(3) { testee.incrementFrontImpressions("a") }

        assertEquals(0, testee.frontImpressions("b"))
        testee.incrementFrontImpressions("b")
        assertEquals(1, testee.frontImpressions("b"))
        assertEquals(0, testee.frontImpressions("a"))
    }

    @Test
    fun `when ids are dismissed and completed then they are kept regardless of the message`() = runTest {
        testee.addDismissedItemId("a")
        testee.addCompletedItemIds(setOf("b"))

        assertEquals(setOf("a"), aStore().dismissedItemIds().first())
        assertEquals(setOf("b"), aStore().completedItemIds())
    }
}
