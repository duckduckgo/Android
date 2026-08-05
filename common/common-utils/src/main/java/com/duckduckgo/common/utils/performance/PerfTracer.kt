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

package com.duckduckgo.common.utils.performance

/**
 * Emits atrace sections readable in a Perfetto trace. Only the internal flavor has a real
 * implementation; Play and F-Droid bind a no-op and never link the tracing library.
 *
 * Implementations must be app-scoped singletons: [beginAsyncSection] cookies have to be unique
 * across concurrently loading tabs, or trace_processor mispairs begin/end events between tabs and
 * emits negative-duration slices.
 */
interface PerfTracer {
    /**
     * Whether a trace is actually being recorded. Guard work that exists ONLY to produce trace data
     * and that would otherwise cost something in a shipped build — not the section calls themselves,
     * which are already free once the no-op is bound.
     */
    fun isEnabled(): Boolean

    fun beginSection(name: String)

    fun endSection()

    /** Returns a process-unique cookie that must be passed back to [endAsyncSection]. */
    fun beginAsyncSection(name: String): Int

    fun endAsyncSection(
        name: String,
        cookie: Int,
    )

    /**
     * Records a point-in-time value on a named counter track.
     *
     * Use this instead of a section when the thing being measured spans a thread boundary. Sections
     * on either side of a hand-off cannot be paired in `trace_processor` — cross-thread slices carry
     * no parent/child relationship, and a timestamp-containment join silently mixes concurrent
     * requests together. A counter emitted from inside the measured call stack is attributed
     * correctly by construction.
     */
    fun counter(
        name: String,
        value: Int,
    )
}

inline fun <T> PerfTracer.trace(
    name: String,
    block: () -> T,
): T {
    beginSection(name)
    try {
        return block()
    } finally {
        endSection()
    }
}
