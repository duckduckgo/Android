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

package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.api.ImportPasswords.ImportPasswordsEvent
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * Progress and outcomes of password imports, for whoever is collecting at the time they happen.
 *
 * Nothing is replayed, so a collector never sees an event from an import that happened before it subscribed.
 */
@SingleIn(AppScope::class)
class PasswordImportEvents @Inject constructor() {

    private val _events = MutableSharedFlow<ImportPasswordsEvent>(extraBufferCapacity = 1)

    val events: Flow<ImportPasswordsEvent> = _events.asSharedFlow()

    suspend fun emit(event: ImportPasswordsEvent) {
        _events.emit(event)
    }
}
