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

package com.duckduckgo.pagetranslation.impl.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Runs translate-kit calls one at a time on [dispatcher], which must run one task at a time. Whenever a call finishes, the next one is the
 * oldest waiting call whose `isVisible` returns true, or the oldest waiting call if none does. `isVisible` is read on
 * [dispatcher].
 *
 * A caller that's canceled while its call waits drops it. A call that's already running finishes and its result is dropped.
 */
class TranslationPriorityQueue(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    // callers add and remove their calls from their own threads
    private val pendingCalls = mutableListOf<PendingCall<*>>()

    suspend fun <T> run(isVisible: () -> Boolean, block: () -> T): T {
        val call = PendingCall(currentCoroutineContext().job, isVisible, block)
        synchronized(pendingCalls) { pendingCalls += call }

        // each launch runs the call that should go next at that moment, so every call gets its turn
        scope.launch(dispatcher) { runNextCall() }

        try {
            return call.result.await()
        } finally {
            synchronized(pendingCalls) { pendingCalls -= call }
        }
    }

    private fun runNextCall() {
        val call = synchronized(pendingCalls) {
            // a canceled caller only removes its call once it resumes, which can be after this
            pendingCalls.removeAll { !it.caller.isActive }
            (pendingCalls.firstOrNull { it.isVisible() } ?: pendingCalls.firstOrNull())?.also { pendingCalls -= it }
        } ?: return

        call.run()
    }

    private class PendingCall<T>(val caller: Job, val isVisible: () -> Boolean, private val block: () -> T) {
        val result = CompletableDeferred<T>()

        fun run() {
            result.completeWith(runCatching(block))
        }
    }
}
