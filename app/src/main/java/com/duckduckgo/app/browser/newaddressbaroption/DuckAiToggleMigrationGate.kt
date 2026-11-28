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
package com.duckduckgo.app.browser.newaddressbaroption

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration

/**
 * Opens once the toggle migration has decided for this process. The address bar picker waits on it, so the picker never
 * asks a user the migration is about to flip, and a picker answer is never mistaken for the migration's decision.
 */
@SingleIn(AppScope::class)
class DuckAiToggleMigrationGate @Inject constructor() {
    private val decided = CompletableDeferred<Unit>()

    fun open() {
        decided.complete(Unit)
    }

    /** Returns false when the gate did not open within [timeout]. */
    suspend fun awaitOpen(timeout: Duration): Boolean = withTimeoutOrNull(timeout) { decided.await() } != null
}
