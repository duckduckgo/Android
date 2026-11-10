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

package com.duckduckgo.app.browser.state

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * Tells the browser whether its first frame after an idle return must wait for the launch pipeline,
 * so the previously selected tab isn't drawn before the landing tab replaces it.
 */
@SingleIn(AppScope::class)
class IdleReturnLandingGate @Inject constructor() {

    sealed interface State {
        data object Open : State
        data object Pending : State
        data class AwaitingTab(val tabId: String, val awaitHatch: Boolean = false, val hatchSettled: Boolean = false) : State {
            val isReady: Boolean get() = !awaitHatch || hatchSettled
        }
    }

    private val _state = AtomicReference<State>(State.Open)
    val state: State get() = _state.get()

    // A restored NTP's hatch can settle before the landing resolves, so settles reported while pending are kept for resolve().
    private val settledHatchTabIds = ConcurrentHashMap.newKeySet<String>()

    fun hold() {
        settledHatchTabIds.clear()
        _state.set(State.Pending)
    }

    /**
     * Waits for [tabId] when the landing created one, otherwise opens. Ignored unless still undecided: once
     * opened (e.g. the first frame gave up waiting) a late landing can't hold a later frame, and a repeated
     * resolve(null) leaves an awaited tab in place.
     */
    fun resolve(tabId: String? = null, awaitHatch: Boolean = false) {
        val resolved = tabId?.let { State.AwaitingTab(it, awaitHatch) } ?: State.Open
        if (_state.compareAndSet(State.Pending, resolved) && resolved is State.AwaitingTab && resolved.tabId in settledHatchTabIds) {
            // Compared by identity, so a resolve delayed past a later hold() can't mark that return's hatch settled.
            _state.compareAndSet(resolved, resolved.copy(hatchSettled = true))
        }
    }

    fun onHatchSettled(tabId: String) {
        if (_state.get() == State.Open) return
        settledHatchTabIds += tabId
        markHatchSettled(tabId)
    }

    fun open() {
        settledHatchTabIds.clear()
        _state.set(State.Open)
    }

    private fun markHatchSettled(tabId: String) {
        _state.updateAndGet { state -> if (state is State.AwaitingTab && state.tabId == tabId) state.copy(hatchSettled = true) else state }
    }
}
