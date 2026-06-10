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

package com.duckduckgo.app.onboarding.orchestrator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.onboarding.DuckAiOnboardingDemo
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.onboarding.api.LinearOnboardingHost
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.forPlan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [com.duckduckgo.app.browser.BrowserActivity]'s participation in [NewUserOnboardingPlanProvider].
 */
@ContributesViewModel(ActivityScope::class)
class NewUserBrowserOnboardingViewModel @Inject constructor(
    private val orchestrator: LinearOnboardingOrchestrator,
    private val duckAiOnboardingDemo: DuckAiOnboardingDemo,
    private val duckChat: DuckChat,
) : ViewModel() {

    sealed interface Command {
        /** The current step is hosted by [com.duckduckgo.app.onboarding.ui.OnboardingActivity]; BrowserActivity should hand off and finish. */
        data object HandOffToOnboardingActivity : Command

        /** Open Duck.ai at [url] to run the onboarding demo. */
        data class OpenDuckAiOnboardingDemo(val url: String) : Command
    }

    // Buffered (not conflated): HandOffToOnboardingActivity and OpenDuckAiOnboardingDemo are navigation
    // commands that must each be delivered, even if BrowserActivity is briefly STOPPED when one is sent.
    private val _commands = Channel<Command>(capacity = Channel.BUFFERED)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    init {
        orchestrator.state
            .forPlan(NewUserOnboardingPlanProvider.ROOT_PLAN_ID)
            .filterIsInstance<LinearOnboardingState.InProgress>()
            .onEach { handleState(it) }
            .launchIn(viewModelScope)
    }

    private suspend fun handleState(state: LinearOnboardingState.InProgress) {
        val step = state.currentStep
        when (step.host) {
            LinearOnboardingHost.BrowserActivity -> {
                // stay
            }
            LinearOnboardingHost.OnboardingActivity -> {
                _commands.send(Command.HandOffToOnboardingActivity)
                return
            }
        }
        if (step is NewUserBrowserActivityStep) {
            when (val action = step.resolveAction()) {
                is NewUserBrowserActivityAction.RunDuckAiOnboardingDemo -> {
                    duckAiOnboardingDemo.arm()
                    val url = duckChat.getDuckChatUrl(action.prompt, autoPrompt = true) + "&flow=mobile-app-onboarding"
                    _commands.send(Command.OpenDuckAiOnboardingDemo(url))
                }
            }
        }
    }

    fun onDuckAiFireCompleted() {
        viewModelScope.launch { orchestrator.onEvent(NewUserOnboardingEvent.DuckAiFireCompleted) }
    }
}
