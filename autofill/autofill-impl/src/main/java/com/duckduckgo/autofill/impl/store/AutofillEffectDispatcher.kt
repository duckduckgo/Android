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

package com.duckduckgo.autofill.impl.store

import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

interface AutofillEffectDispatcher {
    val effects: SharedFlow<AutofillEffect>
    fun emit(intent: AutofillEffect)
}

sealed class AutofillEffect {
    data class LaunchImportPasswords(val source: AutofillImportLaunchSource) : AutofillEffect()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DefaultAutofillEffectDispatcher @Inject constructor() : AutofillEffectDispatcher {
    private val _effects = MutableSharedFlow<AutofillEffect>(extraBufferCapacity = 1)
    override val effects: SharedFlow<AutofillEffect> = _effects

    override fun emit(effect: AutofillEffect) {
        _effects.tryEmit(effect)
    }
}
