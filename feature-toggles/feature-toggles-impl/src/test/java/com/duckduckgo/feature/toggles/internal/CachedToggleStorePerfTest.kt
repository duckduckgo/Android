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

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.api.internal.CachedToggleStore
import com.squareup.moshi.Moshi
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class CachedToggleStorePerfTest {

    private lateinit var store: SharedPreferencesToggleStore
    private lateinit var cachedStore: CachedToggleStore

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        store = SharedPreferencesToggleStore(ctx)
        cachedStore = CachedToggleStore(store)
        // hard reset SP + cache so tests are independent
        store.clearForTests()
        cachedStore.invalidateAllForTests()
        forceGc()
    }

    @Test
    fun `cache hits are significantly faster than reading directly from the store`() {
        val state = State(remoteEnableState = true, settings = "enabled")
        val key = "feature_toggle_hit"
        val iterations = 50_000

        cachedStore.set(key, state)

        // warmup both paths
        repeat(10_000) { cachedStore.get(key) }
        repeat(10_000) { store.get(key) }

        val tCache = medianNanos {
            repeat(iterations) { cachedStore.get(key) }
        }
        val tStore = medianNanos {
            repeat(iterations) { store.get(key) }
        }

        assertFasterWithMargin(fast = tCache, slow = tStore, atLeastRatio = 1.5)
    }

    @Test
    fun `first load misses are not faster when using the cache`() {
        val state = State(remoteEnableState = true, settings = "enabled")
        val keys = List(500) { "k$it" } // small working set prevents OOM

        // preload store (single commit inside helper)
        store.bulkPut(keys, state)

        // warmup on unrelated keys to trigger JIT
        repeat(2_000) { store.get("x$it") }
        repeat(2_000) { cachedStore.get("y$it") }

        // ensure cache starts empty
        cachedStore.invalidateAllForTests()

        val tStore = medianNanos { keys.forEach { store.get(it) } }
        cachedStore.invalidateAllForTests()
        val tCache = medianNanos { keys.forEach { cachedStore.get(it) } } // compulsory loads

        // cache path should be same or slower; allow small slack
        val ratio = tCache.toDouble() / tStore.toDouble()
        assertTrue(
            "Compulsory cache loads should be ~same or slower. ratio=$ratio (cache=$tCache, store=$tStore)",
            ratio >= 0.90,
        )
    }

    @Test
    fun `writing once and reading many times is faster when using the cached store`() {
        val state = State(remoteEnableState = true, settings = "enabled")
        val key = "feature_toggle_wr"
        val readsPerWrite = 200_000

        // warmup both paths
        cachedStore.set(key, state)
        repeat(10_000) { cachedStore.get(key) }
        repeat(10_000) { store.get(key) }

        val tStore = medianNanos {
            store.set(key, state)
            repeat(readsPerWrite) { store.get(key) }
        }
        val tCache = medianNanos {
            cachedStore.set(key, state)
            repeat(readsPerWrite) { cachedStore.get(key) }
        }

        assertTrue("cache=$tCache, store=$tStore", tCache < tStore)
    }

    // ---- helpers ----

    private fun forceGc() {
        System.gc()
        Thread.sleep(50)
        System.runFinalization()
        Thread.sleep(50)
    }

    private inline fun medianNanos(runs: Int = 7, crossinline block: () -> Unit): Long {
        val samples = LongArray(runs)
        repeat(runs) { i ->
            val t = measureNanoTime { block() }
            samples[i] = t
            forceGc()
        }
        return samples.sorted()[runs / 2]
    }

    private fun assertFasterWithMargin(fast: Long, slow: Long, atLeastRatio: Double = 1.15) {
        val ratio = slow.toDouble() / fast.toDouble()
        assertTrue(
            "Expected ≥ ${"%.2f".format(atLeastRatio)}x speedup; got ${"%.2f".format(ratio)}x (fast=$fast ns, slow=$slow ns)",
            ratio >= atLeastRatio,
        )
    }
}

// --- SP-backed store with test helpers ---

@SuppressLint("DenyListedApi")
private class SharedPreferencesToggleStore(context: Context) : Toggle.Store {
    private val adapter = Moshi.Builder().build().adapter(State::class.java)
    private val sp: SharedPreferences =
        context.getSharedPreferences("toggle_store", Context.MODE_PRIVATE)

    override fun set(key: String, state: State) {
        sp.edit().putString(key, adapter.toJson(state)).commit() // commit in tests
    }

    override fun get(key: String): State? =
        sp.getString(key, null)?.let { adapter.fromJson(it) }

    fun clearForTests() {
        sp.edit().clear().commit()
    }

    fun bulkPut(keys: List<String>, state: State) {
        val json = adapter.toJson(state)
        sp.edit().apply {
            keys.forEach { putString(it, json) }
        }.commit()
    }
}

// expose cache clears for tests (or re-create the cached store in @Before)
private fun CachedToggleStore.invalidateAllForTests() {
    invalidateAll()
}
