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

package com.duckduckgo.subscriptions.impl.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType.INTRO
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType.STEP
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType.SUMMARY
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingDataStore
import com.duckduckgo.subscriptions.impl.ui.onboarding.SubscriptionOnboardingViewModel.Command.CloseOnboarding
import com.duckduckgo.subscriptions.impl.ui.onboarding.SubscriptionOnboardingViewModel.Command.ShowStep
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Orchestrates the onboarding flow: an ordered list of screens (an optional [INTRO], the real
 * [STEP]s by priority, then an optional [SUMMARY]).
 *
 * - From [SubscriptionOnboardingOrigin.PURCHASE] the flow starts at the first screen (the INTRO when present).
 * - From [SubscriptionOnboardingOrigin.SETTINGS] the INTRO is skipped and the flow resumes at the last visited step.
 *
 * Completion of a STEP is persisted via [SubscriptionOnboardingDataStore]. A session back stack
 * supports the toolbar back arrow (which can return onto an already-completed step).
 */
@ContributesViewModel(ActivityScope::class)
class SubscriptionOnboardingViewModel @Inject constructor(
    private val stepPlugins: ActivePluginPoint<SubscriptionOnboardingStepPlugin>,
    private val dataStore: SubscriptionOnboardingDataStore,
) : ViewModel() {

    private data class Screen(val name: String, val type: SubscriptionOnboardingStepType)

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private var screens: List<Screen> = emptyList()
    private val backStack = mutableListOf<Int>()
    private var started = false

    fun start(origin: SubscriptionOnboardingOrigin) {
        if (started) return
        started = true
        viewModelScope.launch {
            screens = buildScreens()
            val startIndex = startIndexFor(origin)
            if (startIndex == null) {
                command.send(CloseOnboarding)
                return@launch
            }
            goTo(startIndex)
        }
    }

    fun onStepCompleted() {
        viewModelScope.launch {
            currentScreen()?.let { if (it.type == STEP) dataStore.markCompleted(it.name) }
            advance()
        }
    }

    fun onNextStep() {
        viewModelScope.launch { advance() }
    }

    fun onBackStep() {
        viewModelScope.launch {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
                goToCurrent()
            } else {
                command.send(CloseOnboarding)
            }
        }
    }

    private suspend fun advance() {
        val nextIndex = (backStack.lastOrNull() ?: -1) + 1
        if (nextIndex <= screens.lastIndex) {
            backStack.add(nextIndex)
            goToCurrent()
        } else {
            command.send(CloseOnboarding)
        }
    }

    private suspend fun goTo(index: Int) {
        backStack.add(index)
        goToCurrent()
    }

    private suspend fun goToCurrent() {
        val screen = currentScreen() ?: return
        dataStore.lastVisitedStep = screen.name
        command.send(ShowStep(screen.name))
    }

    private fun currentScreen(): Screen? = backStack.lastOrNull()?.let { screens.getOrNull(it) }

    private suspend fun buildScreens(): List<Screen> {
        val plugins = stepPlugins.getPlugins()
        val ordered = plugins.filter { it.stepType == INTRO } +
            plugins.filter { it.stepType == STEP } +
            plugins.filter { it.stepType == SUMMARY }
        return ordered.map { Screen(it.name, it.stepType) }
    }

    private fun startIndexFor(origin: SubscriptionOnboardingOrigin): Int? {
        if (screens.isEmpty()) return null
        return when (origin) {
            SubscriptionOnboardingOrigin.PURCHASE -> 0
            SubscriptionOnboardingOrigin.SETTINGS -> {
                val resume = screens.indexOfFirst { it.name == dataStore.lastVisitedStep && it.type != INTRO }
                if (resume != -1) resume else screens.indexOfFirst { it.type != INTRO }.takeIf { it != -1 }
            }
        }
    }

    sealed class Command {
        data class ShowStep(val pluginName: String) : Command()
        data object CloseOnboarding : Command()
    }
}
