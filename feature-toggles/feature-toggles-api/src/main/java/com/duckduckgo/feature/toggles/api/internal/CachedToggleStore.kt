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

package com.duckduckgo.feature.toggles.api.internal

import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.jetbrains.annotations.TestOnly
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class CachedToggleStore constructor(
    private val store: Toggle.Store,
) : Toggle.Store {
    @Volatile
    private var listener: Listener? = null

    private val cache: LoadingCache<String, Optional<State>> =
        CacheBuilder
            .newBuilder()
            .maximumSize(50)
            .build(
                object : CacheLoader<String, Optional<State>>() {
                    override fun load(key: String): Optional<State> = Optional.ofNullable(store.get(key))
                },
            )

    override fun set(
        key: String,
        state: State,
    ) {
        cache.asMap().compute(key) { k, _ ->
            store.set(k, state)
            Optional.of(state)
        }
        // Notify AFTER compute() to avoid deadlocks or re-entrancy into the cache/store.
        // If the store.set() above throws, this never runs (which is what we want).
        // Swallow listener exceptions so they don't break writes.
        listener?.runCatching { onToggleStored(state) }
    }

    /**
     * Registers a [Listener] to observe changes in toggle states stored by this [CachedToggleStore].
     *
     * Only a single listener is supported at a time. When a new listener is set, it replaces any
     * previously registered listener. To avoid memory leaks, callers should always invoke the returned
     * unsubscribe function when the listener is no longer needed (for example, when the collector
     * of a [kotlinx.coroutines.flow.callbackFlow] is closed).
     *
     * Example usage:
     * ```
     * val unsubscribe = cachedToggleStore.setListener(object : CachedToggleStore.Listener {
     *     override fun onToggleStored(newValue: Toggle.State, oldValue: Toggle.State?) {
     *         // React to state change
     *     }
     * })
     *
     * // Later, when no longer interested:
     * unsubscribe()
     * ```
     *
     * @param listener the [Listener] instance that will receive callbacks for each `set` operation.
     * @return a function that removes the listener when invoked. The returned function is safe to call
     *         multiple times and will only clear the listener if it is the same instance that was
     *         originally registered.
     */
    fun setListener(listener: Listener?): () -> Unit {
        this.listener = listener

        return { if (this.listener === listener) this.listener = null }
    }

    /**
     * DO NOT USE outside tests
     */
    @TestOnly
    fun invalidateAll() {
        cache.invalidateAll()
    }

    override fun get(key: String): State? {
        val value = cache.get(key).getOrNull()
        if (value == null) {
            // avoid negative caching
            cache.invalidate(key)
        }

        return value
    }

    interface Listener {
        fun onToggleStored(newValue: State)
    }
}
