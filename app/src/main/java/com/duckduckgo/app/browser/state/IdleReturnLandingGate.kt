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
        data class AwaitingTab(val tabId: String, val awaitHatch: Boolean = false) : State
    }

    private val _state = AtomicReference<State>(State.Open)
    val state: State get() = _state.get()

    fun hold() {
        _state.set(State.Pending)
    }

    /**
     * Waits for [tabId] when the landing created one, otherwise opens. Ignored unless still undecided: once
     * opened (e.g. the first frame gave up waiting) a late landing can't hold a later frame, and a repeated
     * resolve(null) leaves an awaited tab in place.
     */
    fun resolve(tabId: String? = null, awaitHatch: Boolean = false) {
        _state.compareAndSet(State.Pending, tabId?.let { State.AwaitingTab(it, awaitHatch) } ?: State.Open)
    }

    fun open() {
        _state.set(State.Open)
    }
}
