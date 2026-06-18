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

package com.duckduckgo.app.onboarding.orchestrator

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Write-once, thread-safe suspend memoizer. The first [invoke] runs [compute] and caches the
 * result (including null); later calls return the cached value without recomputing.
 */
class SuspendMemo<T>(private val compute: suspend () -> T) {

    private val mutex = Mutex()

    @Volatile
    private var holder: Holder<T>? = null

    suspend operator fun invoke(): T {
        holder?.let { return it.value }
        return mutex.withLock {
            holder?.let { return it.value }
            compute().also { holder = Holder(it) }
        }
    }

    private class Holder<T>(val value: T)
}
