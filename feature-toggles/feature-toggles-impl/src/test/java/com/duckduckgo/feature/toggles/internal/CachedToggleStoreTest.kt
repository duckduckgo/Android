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

package com.duckduckgo.feature.toggles.internal

import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.internal.CachedToggleStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedToggleStoreTest {
    private val store = FakeToggleStore()
    private val cachedToggleStore = CachedToggleStore(store)

    @Test
    fun `calling set sets the value in the backing store`() {
        val expected = Toggle.State(remoteEnableState = true, settings = "")
        cachedToggleStore.set("test", expected)
        assertEquals(expected, store.get("test"))
        assertEquals(expected, cachedToggleStore.get("test"))
    }

    @Test
    fun `calling get gets the value from the backing store`() {
        val expected = Toggle.State(remoteEnableState = true, settings = "")
        store.set("test", expected)
        assertEquals(expected, cachedToggleStore.get("test"))
    }

    @Test
    fun `calling get gets null value when backing store doesn't have such value`() {
        assertNull(cachedToggleStore.get("test"))
    }

    @Test
    fun `negative caching occurs when first access is a miss, then backing store is updated directly`() {
        val expected = Toggle.State(remoteEnableState = true, settings = "")
        // this call to cachedToggleStore.get() might trigger negative caching.
        assertEquals(null, cachedToggleStore.get("test"))
        store.set("test", expected)
        val actual = cachedToggleStore.get("test")
        assertEquals(expected, actual)
    }

    @Test
    fun `no negative caching when write goes through wrapper`() {
        val expected = Toggle.State(remoteEnableState = true, settings = "")
        // this call to cachedToggleStore.get() might trigger negative caching.
        assertEquals(null, cachedToggleStore.get("test"))
        cachedToggleStore.set("test", expected)
        val actual = cachedToggleStore.get("test")
        assertEquals(expected, actual)
    }
}
