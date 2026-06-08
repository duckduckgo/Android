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
import com.duckduckgo.onboarding.api.LinearOnboardingResult
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.logcat
import javax.inject.Inject

/**
 * Implementation of [LinearOnboardingOrchestrator].
 *
 * A run is a stack of plan frames ([backStack], bottom = root plan); [onEvent] applies the transition the top
 * step returns, pushing a side plan on [LinearOnboardingTransition.SwitchTo] and popping on
 * [LinearOnboardingTransition.Return]. Running off the end of a frame completes the whole run via the root
 * plan; only an explicit Return resumes a caller.
 *
 * [backStack] and [_state] are always written together (stack reassigned wholesale, never mutated in place),
 * so they never drift. A non-reentrant mutex serialises events; step and plan callbacks run while it is held,
 * so they must not call back into [startPlan] / [onEvent] (a reentrant call throws IllegalStateException
 * instead of deadlocking). Root-plan terminal callbacks run before the terminal state is emitted, so anything
 * they write (e.g. AppStage) is visible to listeners routing off Completed / Skipped.
 */
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LinearOnboardingOrchestratorImpl @Inject constructor() : LinearOnboardingOrchestrator {

    private val _state = MutableStateFlow<LinearOnboardingState>(LinearOnboardingState.NotStarted)
    override val state: StateFlow<LinearOnboardingState> = _state.asStateFlow()

    private val mutex = Mutex()

    // Frame stack (bottom = root plan, top = current).
    // Always reassigned wholesale and together with [_state], so the two can't drift.
    private var backStack: List<Frame> = emptyList()

    override suspend fun startPlan(plan: LinearOnboardingPlan) {
        // owner = current Job makes the mutex throw on a reentrant call instead of deadlocking; a genuinely
        // concurrent caller has a different Job and waits as usual.
        mutex.withLock(owner = currentCoroutineContext()[Job]) {
            // No-op while a run is in progress (safe to call from more than one place at app start); a new run
            // may begin before any run or from a terminal state.
            if (backStack.isNotEmpty()) {
                logcat { "startPlan ignored: a run is already in progress" }
                return@withLock
            }
            advance(listOf(Frame(plan, index = 0)), fromIndex = 0)
        }
    }

    override suspend fun onEvent(event: LinearOnboardingEvent) {
        mutex.withLock(owner = currentCoroutineContext()[Job]) {
            // No-op before start / after terminal.
            val frames = backStack
            if (frames.isEmpty()) return
            val top = frames.last()
            when (val transition = top.plan.steps[top.index].transition(event)) {
                LinearOnboardingTransition.Advance ->
                    advance(frames, fromIndex = top.index + 1)

                is LinearOnboardingTransition.SwitchTo ->
                    advance(frames + Frame(transition.plan, index = 0), fromIndex = 0)

                LinearOnboardingTransition.Return -> {
                    // Returning from the root frame is a programming error; treat as no-op.
                    if (frames.size <= 1) return
                    val caller = frames.dropLast(1)
                    advance(caller, fromIndex = caller.last().index + 1)
                }

                LinearOnboardingTransition.AbortPlan -> terminateSkipped(frames.first().plan)

                LinearOnboardingTransition.Stay -> Unit
            }
        }
    }

    // Advances the top frame from [fromIndex] past ineligible steps, then publishes the new position. Running
    // off the end of a frame completes the whole run via the root plan, whether that frame is the root or a
    // side plan; resuming a caller only happens on an explicit Return. [frames] is local, so a throwing
    // precondition leaves [backStack] / [_state] untouched.
    private suspend fun advance(frames: List<Frame>, fromIndex: Int) {
        val top = frames.last()
        var index = fromIndex
        while (index < top.plan.steps.size && !top.plan.steps[index].precondition()) {
            index++
        }
        if (index < top.plan.steps.size) {
            // Set backStack and _state together (no suspension between) so they can't diverge.
            backStack = frames.dropLast(1) + top.copy(index = index)
            _state.value = LinearOnboardingState.InProgress(
                rootPlanId = frames.first().plan.id,
                currentPlan = top.plan,
                currentStepIndex = index,
            )
        } else {
            terminateCompleted(frames.first().plan)
        }
    }

    // Clears the stack and emits the terminal state from a finally, so a throwing or cancelled callback still
    // ends the run consistently and the callback runs at most once. On success it runs before the state is
    // emitted, so its writes (e.g. AppStage) are visible to Completed / Skipped listeners.
    private suspend fun terminateCompleted(rootPlan: LinearOnboardingPlan) {
        var result: LinearOnboardingResult? = null
        try {
            rootPlan.onCompleted()
            result = rootPlan.result()
        } finally {
            backStack = emptyList()
            _state.value = LinearOnboardingState.Completed(rootPlanId = rootPlan.id, result = result)
        }
    }

    private suspend fun terminateSkipped(rootPlan: LinearOnboardingPlan) {
        try {
            rootPlan.onSkipped()
        } finally {
            backStack = emptyList()
            _state.value = LinearOnboardingState.Skipped(rootPlanId = rootPlan.id)
        }
    }

    private data class Frame(val plan: LinearOnboardingPlan, val index: Int)
}
