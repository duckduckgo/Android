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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Tells the browser whether its first frame after an idle return must wait for the launch pipeline,
 * so the previously selected tab is never drawn before the landing tab replaces it.
 */
@SingleIn(AppScope::class)
class IdleReturnLandingGate @Inject constructor() {

    sealed interface State {
        data object Open : State

        /** The landing is still being decided. */
        data object Pending : State

        /** The landing is a new tab; hold until it is displayed with its return hatch settled. */
        data class AwaitingTab(val tabId: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Open)
    val state: StateFlow<State> = _state.asStateFlow()

    fun hold() {
        _state.value = State.Pending
    }

    /**
     * Waits for [tabId] when the landing created one, otherwise opens. Ignored once the gate has opened,
     * e.g. after the first frame gave up waiting, so a late landing can't hold a later frame.
     */
    fun resolve(tabId: String?) {
        _state.compareAndSet(State.Pending, tabId?.let { State.AwaitingTab(it) } ?: State.Open)
    }

    fun open() {
        _state.value = State.Open
    }

    /** Opens only if the landing is still undecided, leaving a resolved landing in place. */
    fun openIfPending() {
        _state.compareAndSet(State.Pending, State.Open)
    }
}
