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

package com.duckduckgo.subscriptions.impl.onboarding.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.impl.onboarding.welcome.SubscriptionOnboardingWelcomeStepPlugin.Companion.WELCOME_STEP_ID
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStepStore
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class SubscriptionOnboardingWelcomeViewModel @Inject constructor(
    private val controller: SubscriptionOnboardingController,
    private val currentTimeProvider: CurrentTimeProvider,
    private val stepStore: SubscriptionOnboardingStepStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val formattedBillingDate: String = "",
        val freeTrialDayLabels: List<String> = emptyList(),
    )

    sealed interface Command {
        data object LaunchConfetti : Command
    }

    private val _viewState = MutableStateFlow(buildViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private var confettiRequested = false

    private fun buildViewState(): ViewState {
        val startDate = currentTimeProvider.localDateTimeNow().toLocalDate()
        return ViewState(
            formattedBillingDate = startDate.plusDays(FREE_TRIAL_DAYS.toLong()).format(DATE_FORMATTER),
            freeTrialDayLabels = (0 until FREE_TRIAL_DAYS).map { startDate.plusDays(it.toLong()).dayOfMonth.toString() },
        )
    }

    fun onScreenShown() {
        if (confettiRequested) return
        confettiRequested = true

        viewModelScope.launch(dispatcherProvider.io()) {
            if (!stepStore.isCompleted(WELCOME_STEP_ID)) {
                _commands.send(Command.LaunchConfetti)
            }
        }
    }

    fun onPrimaryCtaClicked() {
        controller.onStepFinished(WELCOME_STEP_ID, COMPLETED)
    }

    private companion object {
        private const val FREE_TRIAL_DAYS = 7
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    }
}
