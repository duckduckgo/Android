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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Live working state for stateful screens: one flow per step, seeded on first use. Owned by the view model, so
 * an in-progress selection survives rotation while the engine's observation of it is only bind-scoped.
 */
class ContentValueStore {

    private val states = mutableMapOf<LinearOnboardingStepId, MutableStateFlow<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <S : Any> contentState(
        stepId: LinearOnboardingStepId,
        content: Stateful<S>,
    ): MutableStateFlow<S> = states.getOrPut(stepId) { MutableStateFlow(content.initialState()) } as MutableStateFlow<S>
}
