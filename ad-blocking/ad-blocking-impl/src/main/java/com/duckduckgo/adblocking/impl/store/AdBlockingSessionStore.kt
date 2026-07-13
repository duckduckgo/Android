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

package com.duckduckgo.adblocking.impl.store

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * In-memory store for the "Disable Until Relaunch" ad-blocking override. It is deliberately not
 * persisted, so it resets on process death — the disable therefore lasts only for the current app
 * session ("until relaunch").
 */
interface AdBlockingSessionStore {
    fun isDisabledUntilRelaunch(): Boolean
    fun observe(): Flow<Boolean>
    fun setDisabledUntilRelaunch()
    fun clear()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAdBlockingSessionStore @Inject constructor() : AdBlockingSessionStore {

    private val disabled = MutableStateFlow(false)

    override fun isDisabledUntilRelaunch(): Boolean = disabled.value

    override fun observe(): Flow<Boolean> = disabled.asStateFlow()

    override fun setDisabledUntilRelaunch() {
        this.disabled.value = true
    }

    override fun clear() {
        disabled.value = false
    }
}
