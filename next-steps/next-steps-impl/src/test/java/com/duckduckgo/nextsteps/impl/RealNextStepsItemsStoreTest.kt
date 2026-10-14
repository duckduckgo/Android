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

import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealNextStepsItemsStoreTest {

    private val preferences = InMemorySharedPreferences()
    private val preferencesProvider: SharedPreferencesProvider = mock {
        whenever(it.getSharedPreferences(any(), any(), any())).thenReturn(preferences)
    }
    private val testee = RealNextStepsItemsStore(preferencesProvider)

    @Test
    fun `when nothing is stored then order and dismissed ids are empty`() {
        assertEquals(emptyList<String>(), testee.itemOrder())
        assertEquals(emptySet<String>(), testee.dismissedItemIds())
        assertEquals(0, testee.frontImpressions("a"))
    }

    @Test
    fun `when order is saved then a new instance reads it back`() {
        testee.saveItemOrder(listOf("b", "a", "c"))

        assertEquals(listOf("b", "a", "c"), RealNextStepsItemsStore(preferencesProvider).itemOrder())
    }

    @Test
    fun `when ids are dismissed then a new instance reads them back`() {
        testee.addDismissedItemId("a")
        testee.addDismissedItemId("b")

        assertEquals(setOf("a", "b"), RealNextStepsItemsStore(preferencesProvider).dismissedItemIds())
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
}
