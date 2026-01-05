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
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.api.internal.CachedToggleStore
import org.junit.Assert.*
import org.junit.Test

class CachedToggleStoreListenerTest {

    private val store = FakeToggleStore()
    private val cached = CachedToggleStore(store)

    private class RecordingListener : CachedToggleStore.Listener {
        val calls = mutableListOf<State>()
        override fun onToggleStored(newValue: State) {
            calls += newValue
        }
    }

    @Test
    fun `setting a listener notifies it when set is called`() {
        val listener = RecordingListener()
        cached.setListener(listener)

        val s1 = State(remoteEnableState = true, settings = "a")
        cached.set("k", s1)

        assertEquals(listOf(s1), listener.calls)
    }

    @Test
    fun `unsubscribing a listener stops further notifications`() {
        val listener = RecordingListener()
        val unsubscribe = cached.setListener(listener)

        val s1 = State(remoteEnableState = true, settings = "a")
        cached.set("k", s1)
        assertEquals(1, listener.calls.size)

        // unsubscribe and set again
        unsubscribe()
        val s2 = State(remoteEnableState = true, settings = "b")
        cached.set("k", s2)

        // still only the first call
        assertEquals(1, listener.calls.size)
        assertEquals(s1, listener.calls[0])
    }

    @Test
    fun `multiple listeners all receive updates and unsubscribe independently`() {
        val l1 = RecordingListener()
        val unsub1 = cached.setListener(l1)
        val l2 = RecordingListener()
        val unsub2 = cached.setListener(l2)

        val s1 = State(remoteEnableState = true, settings = "a")
        cached.set("k", s1)
        assertEquals(listOf(s1), l1.calls)
        assertEquals(listOf(s1), l2.calls)

        // Unsubscribe first listener only
        unsub1()
        val s2 = State(remoteEnableState = true, settings = "b")
        cached.set("k", s2)
        assertEquals(listOf(s1), l1.calls)
        assertEquals(listOf(s1, s2), l2.calls)

        // Now unsubscribe the second listener
        unsub2()
        val s3 = State(remoteEnableState = true, settings = "c")
        cached.set("k", s3)
        assertEquals(listOf(s1), l1.calls)
        assertEquals(listOf(s1, s2), l2.calls)
    }

    @Test
    fun `listener exceptions are swallowed and do not break writes`() {
        val throwing = object : CachedToggleStore.Listener {
            override fun onToggleStored(newValue: State) {
                throw RuntimeException("boom")
            }
        }
        val recording = RecordingListener()
        cached.setListener(throwing)
        cached.setListener(recording)

        val s = State(remoteEnableState = true, settings = "x")

        // Should not throw
        cached.set("k", s)

        // And the value must still be written to the backing store and visible via cache
        assertEquals(s, store.get("k"))
        assertEquals(s, cached.get("k"))
        // Non-throwing listeners still receive notifications
        assertEquals(listOf(s), recording.calls)
    }

    @Test
    fun `setting listener to null clears notifications`() {
        val l1 = RecordingListener()
        val l2 = RecordingListener()
        cached.setListener(l1)
        cached.setListener(l2)

        val s1 = State(remoteEnableState = true, settings = "a")
        cached.set("k", s1)
        assertEquals(1, l1.calls.size)
        assertEquals(1, l2.calls.size)

        // Clear listener
        val unsub = cached.setListener(null)

        val s2 = State(remoteEnableState = true, settings = "b")
        cached.set("k", s2)

        // No new calls after clearing
        assertEquals(1, l1.calls.size)
        assertEquals(1, l2.calls.size)

        // Calling the returned unsubscribe is a no-op but should be safe
        unsub()
        val s3 = State(remoteEnableState = true, settings = "c")
        cached.set("k", s3)
        assertEquals(1, l1.calls.size)
        assertEquals(1, l2.calls.size)
    }

    @Test
    fun `unsubscribe is idempotent`() {
        val listener = RecordingListener()
        val unsubscribe = cached.setListener(listener)

        val s1 = State(remoteEnableState = true, settings = "a")
        cached.set("k", s1)
        assertEquals(listOf(s1), listener.calls)

        unsubscribe()
        unsubscribe()

        val s2 = State(remoteEnableState = true, settings = "b")
        cached.set("k", s2)
        assertEquals(listOf(s1), listener.calls)
    }

    @Test
    fun `adding the same listener twice does not double notify`() {
        val listener = RecordingListener()
        cached.setListener(listener)
        cached.setListener(listener)

        val s1 = State(remoteEnableState = true, settings = "a")
        cached.set("k", s1)

        assertEquals(listOf(s1), listener.calls)
    }

    @Test
    fun `listener can remove itself during notification`() {
        lateinit var unsubscribe: () -> Unit
        val listener = object : CachedToggleStore.Listener {
            var calls = 0
            override fun onToggleStored(newValue: State) {
                calls++
                unsubscribe()
            }
        }
        unsubscribe = cached.setListener(listener)

        val s1 = State(remoteEnableState = true, settings = "a")
        cached.set("k", s1)
        assertEquals(1, listener.calls)

        val s2 = State(remoteEnableState = true, settings = "b")
        cached.set("k", s2)
        assertEquals(1, listener.calls)
    }

    @Test
    fun `listener can remove another listener during notification`() {
        val l1 = RecordingListener()
        lateinit var unsub2: () -> Unit
        val l2 = object : CachedToggleStore.Listener {
            override fun onToggleStored(newValue: State) {
                unsub2()
            }
        }
        cached.setListener(l1)
        unsub2 = cached.setListener(l2)

        val s1 = State(remoteEnableState = true, settings = "a")
        cached.set("k", s1)

        assertEquals(listOf(s1), l1.calls)

        val s2 = State(remoteEnableState = true, settings = "b")
        cached.set("k", s2)

        assertEquals(listOf(s1, s2), l1.calls)
    }

    @Test
    fun `listener removal during notification does not break other listeners`() {
        val l1 = RecordingListener()
        val l2 = RecordingListener()
        lateinit var unsub1: () -> Unit
        val remover = object : CachedToggleStore.Listener {
            override fun onToggleStored(newValue: State) {
                unsub1()
            }
        }

        unsub1 = cached.setListener(l1)
        cached.setListener(remover)
        cached.setListener(l2)

        val s1 = State(remoteEnableState = true, settings = "a")
        cached.set("k", s1)

        assertEquals(listOf(s1), l1.calls)
        assertEquals(listOf(s1), l2.calls)

        val s2 = State(remoteEnableState = true, settings = "b")
        cached.set("k", s2)

        assertEquals(listOf(s1), l1.calls)
        assertEquals(listOf(s1, s2), l2.calls)
    }
}
