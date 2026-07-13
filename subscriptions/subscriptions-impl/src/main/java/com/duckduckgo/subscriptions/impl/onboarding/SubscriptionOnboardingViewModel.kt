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

package com.duckduckgo.subscriptions.impl.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.forPlan
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingEvent.BackPressed
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingEvent.StepFinished
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingPlanProvider.Companion.SUBSCRIPTION_ONBOARDING_PLAN_ID
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStepStore
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the native subscription onboarding: starts the plugin-built plan on the shared
 * [LinearOnboardingOrchestrator] and translates its state (scoped to [SUBSCRIPTION_ONBOARDING_PLAN_ID]) into
 * [Command]s the activity renders. Step Fragments report back through the host, which calls [onStepFinished]
 * / [onExit].
 */
@ContributesViewModel(ActivityScope::class)
class SubscriptionOnboardingViewModel @Inject constructor(
    private val orchestrator: LinearOnboardingOrchestrator,
    private val planProvider: SubscriptionOnboardingPlanProvider,
    private val stepStore: SubscriptionOnboardingStepStore,
) : ViewModel() {

    sealed interface Command {
        data class ShowStep(val stepPlugin: SubscriptionOnboardingStepPlugin) : Command
        data object FinishToSettings : Command
        data object Finish : Command
    }

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private var started = false

    // Mirrors the current step's InProgress.canGoBack, so onBack() knows whether to go back or exit.
    private var canGoBack = false

    fun start() {
        if (started) return
        started = true

        viewModelScope.launch { orchestrator.startPlan(planProvider.buildPlan()) }

        orchestrator.state
            .forPlan(SUBSCRIPTION_ONBOARDING_PLAN_ID)
            .onEach { handleState(it) }
            .launchIn(viewModelScope)
    }

    private suspend fun handleState(state: LinearOnboardingState.Started) {
        when (state) {
            is LinearOnboardingState.InProgress -> {
                val step = state.currentStep
                if (step is SubscriptionOnboardingActivityStep) {
                    canGoBack = state.canGoBack
                    _commands.send(Command.ShowStep(step.stepPlugin))
                }
            }
            is LinearOnboardingState.Completed -> _commands.send(Command.FinishToSettings)
            is LinearOnboardingState.Skipped -> _commands.send(Command.Finish)
        }
    }

    fun onStepFinished(stepId: String, outcome: SubscriptionOnboardingStepOutcome) {
        if (outcome == SubscriptionOnboardingStepOutcome.COMPLETED) {
            stepStore.setCompleted(stepId)
        }
        viewModelScope.launch { orchestrator.onEvent(StepFinished(stepId, outcome)) }
    }

    fun onBack() {
        viewModelScope.launch {
            if (canGoBack) {
                orchestrator.onEvent(BackPressed)
            } else {
                // Nothing earlier in the flow: back on the first step leaves onboarding.
                _commands.send(Command.Finish)
            }
        }
    }

    fun onExit() {
        viewModelScope.launch { _commands.send(Command.Finish) }
    }
}
