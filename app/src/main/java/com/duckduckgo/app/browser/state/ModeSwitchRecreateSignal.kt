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
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * One-shot signal that a programmatic browser-mode switch is about to recreate the browser activity.
 *
 * A `recreate()` fires onClose+onOpen as if the app reopened; launch-time handling consumes this to
 * tell that recreate apart from a real app launch or resume, so it doesn't re-run launch behaviour.
 */
@SingleInstanceIn(AppScope::class)
class ModeSwitchRecreateSignal @Inject constructor() {
    private val pending = AtomicBoolean(false)

    /** Mark that the next browser-activity (re)start is a mode-switch recreate, not a launch/resume. */
    fun markPending() {
        pending.set(true)
    }

    /** Returns true once if a mode-switch recreate is pending, then clears the flag. */
    fun consumePending(): Boolean = pending.getAndSet(false)
}
