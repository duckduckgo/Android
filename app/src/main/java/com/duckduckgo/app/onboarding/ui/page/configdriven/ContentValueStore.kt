/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * VM-owned store of live working state for stateful screens.
 * One MutableStateFlow per screen, keyed by the config class; seeded from initialState() on first use.
 * Survives rotation with the VM; the engine's observation of it is bind-scoped.
 */
class ContentValueStore {
    private val states = mutableMapOf<KClass<*>, MutableStateFlow<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <S : Any> contentState(content: Stateful<S>): MutableStateFlow<S> =
        states.getOrPut(content::class) { MutableStateFlow(content.initialState()) } as MutableStateFlow<S>
}
