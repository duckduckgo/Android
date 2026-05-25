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

package com.duckduckgo.onboarding.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LinearOnboardingOrchestratorImpl @Inject constructor() : LinearOnboardingOrchestrator {

    private val _state = MutableStateFlow<LinearOnboardingState>(LinearOnboardingState.NotStarted)
    override val state: StateFlow<LinearOnboardingState> = _state.asStateFlow()

    private val mutex = Mutex()

    // Frame stack: bottom = main plan, top = currently executing plan. Each frame
    // records the step index at which its plan is paused (top frame's index ==
    // the currently-executing step). SwitchTo pushes; Return pops.
    private val frameStack = ArrayDeque<Frame>()

    override suspend fun startPlan(plan: LinearOnboardingPlan) {
        mutex.withLock {
            if (_state.value !is LinearOnboardingState.NotStarted) return
            pushAndAdvance(plan)
        }
    }

    override suspend fun onEvent(event: LinearOnboardingEvent) {
        mutex.withLock {
            val top = frameStack.lastOrNull() ?: return
            when (val transition = top.plan.steps[top.index].transition(event)) {
                LinearOnboardingTransition.Advance -> advanceTopFrame(fromIndex = top.index + 1)
                is LinearOnboardingTransition.SwitchTo -> pushAndAdvance(transition.plan)
                LinearOnboardingTransition.Return -> popAndAdvance()
                LinearOnboardingTransition.AbortPlan -> terminateSkipped()
                LinearOnboardingTransition.Stay -> Unit
            }
        }
    }

    private suspend fun pushAndAdvance(plan: LinearOnboardingPlan) {
        frameStack.addLast(Frame(plan, index = 0))
        advanceTopFrame(fromIndex = 0)
    }

    private suspend fun popAndAdvance() {
        // Returning from the root frame is a programming error; treat as no-op.
        if (frameStack.size <= 1) return
        frameStack.removeLast()
        val caller = frameStack.last()
        advanceTopFrame(fromIndex = caller.index + 1)
    }

    // Main-plan onCompleted runs before the Completed terminal state is emitted, so
    // any state the callback writes (e.g. AppStage advancement) is visible to
    // listeners that route off Completed.
    private suspend fun advanceTopFrame(fromIndex: Int) {
        val top = frameStack.last()
        val plan = top.plan
        var index = fromIndex
        while (index < plan.steps.size && !plan.steps[index].precondition()) {
            index++
        }
        if (index >= plan.steps.size) {
            terminateCompleted()
        } else {
            frameStack[frameStack.size - 1] = top.copy(index = index)
            _state.value = LinearOnboardingState.InProgress(plan, index)
        }
    }

    private suspend fun terminateCompleted() {
        val mainPlan = frameStack.first().plan
        frameStack.clear()
        mainPlan.onCompleted()
        _state.value = LinearOnboardingState.Completed
    }

    private suspend fun terminateSkipped() {
        val mainPlan = frameStack.first().plan
        frameStack.clear()
        mainPlan.onSkipped()
        _state.value = LinearOnboardingState.Skipped
    }

    private data class Frame(val plan: LinearOnboardingPlan, val index: Int)
}
