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
import com.duckduckgo.subscriptions.impl.ui.onboarding.SubscriptionOnboardingViewModel.Command.CloseOnboarding
import com.duckduckgo.subscriptions.impl.ui.onboarding.SubscriptionOnboardingViewModel.Command.ShowStep
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the order-based step sequence after the (concrete) details screen.
 *
 * The first screen shown by the activity is the details fragment (`currentIndex == -1`). Each time a
 * step finishes, [onStepCompleted] advances to the next ordered plugin, or closes onboarding when
 * there are no more plugins.
 */
@ContributesViewModel(ActivityScope::class)
class SubscriptionOnboardingViewModel @Inject constructor(
    private val stepPlugins: ActivePluginPoint<SubscriptionOnboardingStepPlugin>,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private var steps: List<String>? = null
    private var currentIndex = -1

    fun onStepCompleted() {
        viewModelScope.launch {
            val orderedSteps = steps ?: stepPlugins.getPlugins().map { it.name }.also { steps = it }
            currentIndex++
            val next = if (currentIndex <= orderedSteps.lastIndex) {
                ShowStep(orderedSteps[currentIndex])
            } else {
                CloseOnboarding
            }
            command.send(next)
        }
    }

    sealed class Command {
        data class ShowStep(val pluginName: String) : Command()
        data object CloseOnboarding : Command()
    }
}
