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

package com.duckduckgo.duckchat.impl.messaging

import org.junit.Assert.*
import org.junit.Test

class InMemoryStandaloneStoreTest {
    private val store = InMemoryStandaloneStore()

    @Test
    fun `when storeMigrationItem is called, message is stored`() {
        val item1 = "Blob1"
        val item2 = "Blob2"
        store.storeMigrationItem(item1)
        store.storeMigrationItem(item2)

        assertEquals(2, store.getMigrationItemCount())
        assertEquals(item1, store.getMigrationItemByIndex(0))
        assertEquals(item2, store.getMigrationItemByIndex(1))
    }

    @Test
    fun `when clearMigrationItems is called, all messages are cleared`() {
        val item1 = "Blob1"
        store.storeMigrationItem(item1)
        store.clearMigrationItems()

        assertEquals(0, store.getMigrationItemCount())
        assertNull(store.getMigrationItemByIndex(0))
    }
}
